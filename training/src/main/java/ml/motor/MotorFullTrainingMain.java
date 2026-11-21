package ml.motor;

import org.knowm.xchart.ChartEncoder;
import org.knowm.xchart.XYChartBuilder;
import java.nio.file.*;
import java.util.*;

/**
 * Retrains the selected GBDT family on all valid training origins in both 1 Hz phases.
 * Validation origins and tree hyperparameters are inherited from the previous experiment.
 * There is deliberately no test mode: this follow-up must not repeatedly inspect the old test set.
 */
public final class MotorFullTrainingMain {
    private static final int[] SEEDS = {42, 2026};

    public static void main(String[] args) throws Exception {
        if (args.length != 3)
            throw new IllegalArgumentException("Usage: MotorFullTrainingMain data.csv previous-run new-run");
        Path csv = Path.of(args[0]), previous = Path.of(args[1]), out = Path.of(args[2]);
        if (Files.exists(out)) throw new IllegalArgumentException("Use a new run directory; old artifacts are immutable");
        Properties prior = properties(previous.resolve("experiment.properties"));
        String hash = MotorHorizonExperimentMain.sha256(csv);
        if (!hash.equals(prior.getProperty("dataset.sha256")))
            throw new IllegalStateException("Dataset differs from the baseline experiment");

        var raw = new MotorDataReader().loadData(csv.toString(), 1);
        var split = new ProfileSplitter().split(raw, 42);
        if (split.train().size() != 48 || split.validation().size() != 10 || split.test().size() != 11)
            throw new IllegalStateException("Expected 48/10/11 profiles");
        if (!split.train().keySet().toString().equals(prior.getProperty("train.profiles"))
                || !split.validation().keySet().toString().equals(prior.getProperty("validation.profiles"))
                || !split.test().keySet().toString().equals(prior.getProperty("test.profiles")))
            throw new IllegalStateException("Profile assignment changed");

        var validation = ForecastWindows.sample(ForecastWindows.oneHzPhase(split.validation(), 0),
                5, Integer.parseInt(prior.getProperty("validation.windows")), 43);
        verifyOrigins(previous.resolve("windows_validation.csv"), validation);
        var training = ForecastWindows.allTrainingPhases(split.train(), 42);
        if (training.isEmpty()) throw new IllegalStateException("No training windows");

        Files.createDirectories(out);
        Properties config = new Properties();
        config.setProperty("model.version", "full-training-v1");
        config.setProperty("dataset.sha256", hash);
        config.setProperty("previous.run", previous.toAbsolutePath().toString());
        config.setProperty("split.seed", "42");
        config.setProperty("train.profiles", split.train().keySet().toString());
        config.setProperty("validation.profiles", split.validation().keySet().toString());
        config.setProperty("test.profiles", split.test().keySet().toString());
        config.setProperty("dataset.raw.rows", Long.toString(rows(raw)));
        config.setProperty("train.raw.rows", Long.toString(rows(split.train())));
        config.setProperty("train.windows", Integer.toString(training.size()));
        config.setProperty("previous.train.windows", prior.getProperty("train.windows"));
        config.setProperty("validation.windows", Integer.toString(validation.size()));
        config.setProperty("training.phases", "0,1; offsets 0 and 0.5 seconds; original index step 2 in each window");
        config.setProperty("sampling", "all eligible common origins, step1 second per phase, no cap; order seed42");
        config.setProperty("window.size", "61");
        config.setProperty("max.target.seconds", "60");
        config.setProperty("seeds", "42,2026");
        config.setProperty("test.evaluated", "false");
        config.setProperty("selection", "adopt full only if MAE improves for BOTH seeds and seed42 RMSE does not worsen; otherwise retain old seed42");
        config.setProperty("java.version", System.getProperty("java.version"));
        MotorHorizonExperimentMain.writeProperties(out.resolve("experiment.properties"), config);
        Files.copy(previous.resolve("profile_split.csv"), out.resolve("profile_split.csv"));
        MotorHorizonExperimentMain.windows(out, "train", training);
        MotorHorizonExperimentMain.windows(out, "validation", validation);
        writeCoverage(out, split.train(), training);
        System.out.printf(Locale.ROOT, "Raw rows=%d; training rows=%d; profiles=48/10/11; training windows=%d; validation=%d; test NOT evaluated%n",
                rows(raw), rows(split.train()), training.size(), validation.size());

        List<String> metrics = new ArrayList<>(List.of(ForecastEvaluation.header()));
        List<String> selected = new ArrayList<>(List.of(ForecastEvaluation.header()));
        List<String> comparisons = new ArrayList<>(List.of(
                "horizon,seed,old_mae_c,full_mae_c,old_rmse_c,full_rmse_c,mae_improvement_pct,training_seconds,reload_difference_c"));
        List<String> uncertainty = new ArrayList<>(List.of(
                "horizon,profiles,resamples,seed,old_minus_full_mae_low95_c,old_minus_full_mae_high95_c"));
        Properties selection = new Properties();
        List<ForecastEvaluation.Metrics> fullSummary = new ArrayList<>(), oldSummary = new ArrayList<>();
        for (int h : ForecastWindows.HORIZONS) {
            var baseline = ForecastEvaluation.metrics(validation, h, new BaselineForecaster(h, false).predictAll(validation));
            metrics.add(ForecastEvaluation.row("validation", h, "Persistence", baseline, baseline));
            ForecastEvaluation.Metrics[] oldMetrics = new ForecastEvaluation.Metrics[2], fullMetrics = new ForecastEvaluation.Metrics[2];
            double[] old42 = null, full42 = null;
            for (int s = 0; s < SEEDS.length; s++) {
                int seed = SEEDS[s];
                Path oldDir = previous.resolve("models_gbdt_h" + h + "_s" + seed);
                Properties oldConfig = properties(oldDir.resolve("tree.properties"));
                if (!hash.equals(oldConfig.getProperty("dataset.sha256")))
                    throw new IllegalStateException("Old model dataset hash mismatch");
                double[] oldPrediction;
                try (var old = TreeHorizonModel.load(oldDir, h)) {
                    oldPrediction = old.predictAll(validation);
                    oldMetrics[s] = ForecastEvaluation.metrics(validation, h, oldPrediction);
                    metrics.add(ForecastEvaluation.row("validation", h, "GBDT_sampled_seed" + seed, oldMetrics[s], baseline));
                }
                Properties options = new Properties();
                options.putAll(config);
                for (String key : oldConfig.stringPropertyNames())
                    if (key.startsWith("smile.gradient_boost.")) options.setProperty(key, oldConfig.getProperty(key));
                options.setProperty("seed", Integer.toString(seed));
                options.setProperty("model.name", "GBDT_full_seed" + seed);
                options.setProperty("feature.count", Integer.toString(TreeHorizonModel.NAMES.length));
                options.setProperty("baseline.model.sha256", MotorHorizonExperimentMain.sha256(oldDir.resolve("gbdt.ser")));
                Path modelDir = out.resolve("models_gbdt_h" + h + "_s" + seed);
                System.out.printf(Locale.ROOT, "Training full GBDT h=%d seed=%d windows=%d%n", h, seed, training.size());
                long start = System.nanoTime();
                double before;
                try (var model = TreeHorizonModel.train(training, h, seed, options)) {
                    before = model.predict(validation.get(0).history());
                    model.save(modelDir, options);
                }
                double seconds = (System.nanoTime() - start) / 1e9;
                double[] fullPrediction;
                double reloadDifference;
                try (var loaded = TreeHorizonModel.load(modelDir, h)) {
                    reloadDifference = Math.abs(loaded.predict(validation.get(0).history()) - before);
                    if (reloadDifference > 1e-10) throw new IllegalStateException("Save/reload mismatch");
                    fullPrediction = loaded.predictAll(validation);
                    fullMetrics[s] = ForecastEvaluation.metrics(validation, h, fullPrediction);
                    String row = ForecastEvaluation.row("validation", h, loaded.name(), fullMetrics[s], baseline);
                    metrics.add(row);
                    Files.write(modelDir.resolve("validation_metrics.csv"), List.of(ForecastEvaluation.header(), row));
                }
                Files.writeString(modelDir.resolve("reload_check.csv"), "absolute_difference_c\n" + reloadDifference + "\n");
                comparisons.add(String.format(Locale.ROOT, "%d,%d,%.9f,%.9f,%.9f,%.9f,%.6f,%.3f,%.12f",
                        h, seed, oldMetrics[s].mae(), fullMetrics[s].mae(), oldMetrics[s].rmse(), fullMetrics[s].rmse(),
                        ForecastEvaluation.improvement(fullMetrics[s].mae(), oldMetrics[s].mae()), seconds, reloadDifference));
                Files.write(out.resolve("candidates.csv"), metrics);
                Files.write(out.resolve("comparison.csv"), comparisons);
                System.out.printf(Locale.ROOT, "h=%d seed=%d old MAE=%.6f full MAE=%.6f improvement=%.3f%% %.1fs%n",
                        h, seed, oldMetrics[s].mae(), fullMetrics[s].mae(),
                        ForecastEvaluation.improvement(fullMetrics[s].mae(), oldMetrics[s].mae()), seconds);
                if (seed == 42) {
                    old42 = oldPrediction; full42 = fullPrediction;
                    ForecastEvaluation.details(out, "validation", h, "GBDT_full_seed42", validation, fullPrediction);
                }
            }
            oldSummary.add(oldMetrics[0]); fullSummary.add(fullMetrics[0]);
            double[] interval = bootstrap(validation, h, old42, full42);
            uncertainty.add(h + ",10,2000,4242," + interval[0] + "," + interval[1]);
            Files.write(out.resolve("profile_bootstrap.csv"), uncertainty);
            boolean adopt = fullMetrics[0].mae() < oldMetrics[0].mae()
                    && fullMetrics[1].mae() < oldMetrics[1].mae()
                    && fullMetrics[0].rmse() <= oldMetrics[0].rmse();
            String relative = "models_gbdt_h" + h + "_s42";
            if (!adopt) {
                relative = "models_retained_h" + h;
                Path retained = out.resolve(relative);
                Files.createDirectories(retained);
                Path original = previous.resolve("models_gbdt_h" + h + "_s42");
                for (String name : List.of("gbdt.ser", "tree.properties", "validation_metrics.csv", "reload_check.csv"))
                    Files.copy(original.resolve(name), retained.resolve(name));
            }
            selection.setProperty("h" + h + ".type", "GBDT");
            selection.setProperty("h" + h + ".path", relative);
            selection.setProperty("h" + h + ".adopt.full", Boolean.toString(adopt));
            selected.add(ForecastEvaluation.row("validation", h, adopt ? "GBDT_full_seed42" : "GBDT_sampled_seed42",
                    adopt ? fullMetrics[0] : oldMetrics[0], baseline));
            MotorHorizonExperimentMain.writeProperties(out.resolve("selection.properties"), selection);
            Files.write(out.resolve("validation.csv"), selected);
            System.out.println("h=" + h + " selection=" + (adopt ? "full training" : "retain previous model"));
        }
        var chart = new XYChartBuilder().width(1000).height(600).title("More training data: fixed validation origins")
                .xAxisTitle("Horizon (s)").yAxisTitle("MAE (C)").build();
        double[] x = Arrays.stream(ForecastWindows.HORIZONS).asDoubleStream().toArray();
        chart.addSeries("Previous 24000 windows", x, oldSummary.stream().mapToDouble(ForecastEvaluation.Metrics::mae).toArray());
        chart.addSeries("All eligible two-phase windows", x, fullSummary.stream().mapToDouble(ForecastEvaluation.Metrics::mae).toArray());
        ChartEncoder.saveChart(chart, out.resolve("training_size_comparison").toString(), "png");
        Files.writeString(out.resolve("FULL_TRAINING_COMPLETE"),
                "Validation comparison complete. Existing test set NOT re-evaluated. Runtime configuration NOT changed.\n");
    }

    private static long rows(Map<Integer, List<MotorReading>> data) {
        return data.values().stream().mapToLong(List::size).sum();
    }

    private static Properties properties(Path file) throws Exception {
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(file)) { p.load(reader); }
        return p;
    }

    static void verifyOrigins(Path file, List<ForecastWindows.Ref> refs) throws Exception {
        List<String> saved = Files.readAllLines(file);
        if (saved.size() != refs.size() + 1) throw new IllegalStateException("Validation size differs");
        for (int i = 0; i < refs.size(); i++) {
            var r = refs.get(i).now();
            if (!saved.get(i + 1).equals(r.profileId() + "," + r.sampleIndex()))
                throw new IllegalStateException("Validation origin/order differs at " + i);
        }
    }

    private static void writeCoverage(Path out, Map<Integer,List<MotorReading>> raw, List<ForecastWindows.Ref> refs) throws Exception {
        Map<Integer,long[]> counts = new LinkedHashMap<>();
        raw.keySet().forEach(id -> counts.put(id, new long[2]));
        refs.forEach(r -> counts.get(r.now().profileId())[(int)(r.now().sampleIndex() % 2)]++);
        List<String> rows = new ArrayList<>(List.of("profile_id,raw_training_rows,even_phase_windows,odd_phase_windows"));
        raw.forEach((id, p) -> rows.add(id + "," + p.size() + "," + counts.get(id)[0] + "," + counts.get(id)[1]));
        Files.write(out.resolve("training_coverage.csv"), rows);
    }

    private static double[] bootstrap(List<ForecastWindows.Ref> refs, int h, double[] old, double[] full) {
        Map<Integer,double[]> clusters = new TreeMap<>();
        for (int i = 0; i < refs.size(); i++) {
            var ref = refs.get(i);
            double[] c = clusters.computeIfAbsent(ref.now().profileId(), k -> new double[2]);
            c[0] += Math.abs(old[i] - ref.target(h)) - Math.abs(full[i] - ref.target(h)); c[1]++;
        }
        var values = new ArrayList<>(clusters.values()); Random random = new Random(4242); double[] draws = new double[2000];
        for (int b = 0; b < draws.length; b++) {
            double total = 0, n = 0;
            for (int j = 0; j < values.size(); j++) { double[] c = values.get(random.nextInt(values.size())); total += c[0]; n += c[1]; }
            draws[b] = total / n;
        }
        Arrays.sort(draws);
        return new double[]{draws[49], draws[1949]};
    }
}
