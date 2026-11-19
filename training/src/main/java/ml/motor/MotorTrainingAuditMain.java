package ml.motor;

import org.knowm.xchart.ChartEncoder;
import org.knowm.xchart.XYChartBuilder;
import java.nio.file.*;
import java.util.*;

/** Training-data utilization and GBDT learning curves. Never reads test scores or evaluates test profiles. */
public final class MotorTrainingAuditMain {
    private record Coverage(Map<Integer,BitSet> history, Map<Integer,BitSet> historyAndTargets) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: data.csv previous-run full-run");
        Path previous = Path.of(args[1]), out = Path.of(args[2]);
        if (!Files.exists(out.resolve("FULL_TRAINING_COMPLETE"))) throw new IllegalStateException("Training incomplete");
        Properties config = new Properties();
        try (var in = Files.newBufferedReader(out.resolve("experiment.properties"))) { config.load(in); }
        if (!MotorHorizonExperimentMain.sha256(Path.of(args[0])).equals(config.getProperty("dataset.sha256")))
            throw new IllegalStateException("Dataset changed since training");
        Coverage old = coverage(previous.resolve("windows_train.csv"));
        Coverage full = coverage(out.resolve("windows_train.csv"));
        List<String> usage = new ArrayList<>(List.of(
                "profile_id,raw_training_rows,old_unique_history_rows,old_unique_history_or_target_rows,full_unique_history_rows,full_unique_history_or_target_rows"));
        var coverageRows = Files.readAllLines(out.resolve("training_coverage.csv"));
        for (String line : coverageRows.subList(1,coverageRows.size())) {
            String[] c = line.split(","); int id = Integer.parseInt(c[0]);
            usage.add(id + "," + c[1] + "," + size(old.history,id) + "," + size(old.historyAndTargets,id)
                    + "," + size(full.history,id) + "," + size(full.historyAndTargets,id));
        }
        Files.write(out.resolve("data_utilization.csv"), usage);

        var raw = new MotorDataReader().loadData(args[0],1);
        var split = new ProfileSplitter().split(raw,42);
        var all = ForecastWindows.allTrainingPhases(split.train(),42);
        var diagnostic = new ArrayList<>(all.subList(0, Math.min(4096,all.size())));
        var validation = ForecastWindows.sample(ForecastWindows.oneHzPhase(split.validation(),0),5,
                Integer.parseInt(config.getProperty("validation.windows")),43);
        MotorFullTrainingMain.verifyOrigins(previous.resolve("windows_validation.csv"),validation);
        verifySelectedModels(out, validation);
        for (int h : ForecastWindows.HORIZONS) for (int seed : new int[]{42,2026}) {
            Path dir = out.resolve("models_gbdt_h"+h+"_s"+seed);
            try (var model = TreeHorizonModel.load(dir,h)) {
                double[][] train = model.predictStages(diagnostic), val = model.predictStages(validation);
                double[] finalPrediction = model.predictAll(validation);
                for (int i=0;i<finalPrediction.length;i++)
                    if (Math.abs(finalPrediction[i]-val[val.length-1][i])>1e-10)
                        throw new IllegalStateException("Staged prediction mismatch");
                List<String> loss = new ArrayList<>(List.of("trees,train_mae_c,train_mse_c2,validation_mae_c,validation_mse_c2"));
                double[] trees = new double[train.length], trainMse = new double[train.length], valMse = new double[train.length];
                for (int stage=0;stage<train.length;stage++) {
                    double[] tr = errors(diagnostic,h,train[stage]), va = errors(validation,h,val[stage]);
                    loss.add((stage+1)+","+tr[0]+","+tr[1]+","+va[0]+","+va[1]);
                    trees[stage]=stage+1;trainMse[stage]=tr[1];valMse[stage]=va[1];
                }
                Path csv = out.resolve("loss_gbdt_h"+h+"_s"+seed+".csv");
                Files.write(csv,loss); Files.copy(csv,dir.resolve("loss.csv"),StandardCopyOption.REPLACE_EXISTING);
                var chart = new XYChartBuilder().width(1000).height(600).title("Full-data GBDT h="+h+" seed="+seed)
                        .xAxisTitle("Boosting trees (fixed 200; diagnostic only)").yAxisTitle("MSE (C squared)").build();
                chart.addSeries("Train diagnostic 4096",trees,trainMse);
                chart.addSeries("Fixed validation " + validation.size(),trees,valMse);
                String base = "loss_gbdt_h"+h+"_s"+seed;
                ChartEncoder.saveChart(chart,out.resolve(base).toString(),"png");
                Files.copy(out.resolve(base+".png"),dir.resolve("loss.png"),StandardCopyOption.REPLACE_EXISTING);
            }
        }
        List<String> checksums = new ArrayList<>();
        try (var files = Files.walk(out)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                if (name.equals("gbdt.ser") || name.equals("tree.properties") || name.equals("selection.properties"))
                    checksums.add(MotorHorizonExperimentMain.sha256(file) + "  " + out.relativize(file));
            }
        }
        Files.write(out.resolve("MODEL_SHA256SUMS"), checksums);
        Files.writeString(out.resolve("AUDIT_COMPLETE"),
                "Raw-row coverage, staged prediction consistency, 10 learning curves and model checksums verified. No test evaluation.\n");
        System.out.println("Coverage audit and 10 GBDT loss curves saved; no test evaluation performed.");
    }

    private static void verifySelectedModels(Path out, List<ForecastWindows.Ref> validation) throws Exception {
        List<String> saved = Files.readAllLines(out.resolve("validation.csv"));
        List<ForecastEvaluation.Metrics> selected = new ArrayList<>(), persistence = new ArrayList<>();
        try (var registry = ForecastModelRegistry.load(out)) {
            if (!registry.errors().isEmpty()) throw new IllegalStateException("Cannot load selected models: " + registry.errors());
            for (int h : ForecastWindows.HORIZONS) {
                var model = registry.models().get(h);
                var metrics = ForecastEvaluation.metrics(validation, h, model.predictAll(validation));
                var baseline = ForecastEvaluation.metrics(validation, h, new BaselineForecaster(h, false).predictAll(validation));
                String row = saved.get(1 + selected.size());
                if (!ForecastEvaluation.row("validation", h, row.split(",")[2], metrics, baseline).equals(row))
                    throw new IllegalStateException("Selected model disagrees with validation.csv at horizon " + h);
                selected.add(metrics);
                persistence.add(baseline);
            }
        }
        ForecastEvaluation.horizonChart(out, "validation_full", selected, persistence);
    }

    private static int size(Map<Integer,BitSet> values,int id) {
        return values.getOrDefault(id,new BitSet()).cardinality();
    }

    private static Coverage coverage(Path manifest) throws Exception {
        Map<Integer,BitSet> histories=new HashMap<>(), labels=new HashMap<>();
        try (var in=Files.newBufferedReader(manifest)) {
            in.readLine();String line;
            while((line=in.readLine())!=null) {
                String[] c=line.split(",");int profile=Integer.parseInt(c[0]),origin=Integer.parseInt(c[1]);
                BitSet history=histories.computeIfAbsent(profile,k->new BitSet());
                BitSet targets=labels.computeIfAbsent(profile,k->new BitSet());
                for(int j=0;j<61;j++)history.set(origin-2*j);
                for(int h:ForecastWindows.HORIZONS)targets.set(origin+2*h);
            }
        }
        histories.forEach((id,bits)->labels.get(id).or(bits));
        return new Coverage(histories,labels);
    }

    private static double[] errors(List<ForecastWindows.Ref> refs,int h,double[] predictions) {
        double abs=0,sq=0;
        for(int i=0;i<predictions.length;i++) {
            if (!Double.isFinite(predictions[i])) throw new IllegalStateException("Non-finite staged prediction");
            double e=predictions[i]-refs.get(i).target(h);abs+=Math.abs(e);sq+=e*e;
        }
        return new double[]{abs/predictions.length,sq/predictions.length};
    }
}
