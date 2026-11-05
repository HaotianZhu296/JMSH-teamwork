
package ml.motor;

import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.regression.GradientTreeBoost;
import smile.io.Write;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

public class MotorForecastV2Main {

    record Example(
            double[] features,
            double current,
            double future,
            double delta
    ) {}

    record Metrics(
            double mae,
            double rmse,
            double highTemperatureMae,
            double precision,
            double recall,
            double f1
    ) {}

    public static void main(String[] args) throws Exception {

        var profiles = new MotorDataReader()
                .loadData("data/measures_v2.csv", 2);

        var split = new ProfileSplitter()
                .split(profiles, 42);

        // Keep test profiles untouched during optimization
        List<Example> train = build(
                split.train(), 2, 120000, 42
        );

        List<Example> validation = build(
                split.validation(), 5, 20000, 43
        );

        System.out.println("Training samples: " + train.size());
        System.out.println(
                "Validation samples: " + validation.size()
        );

        DataFrame trainingFrame = trainingFrame(train);
        DataFrame validationFrame = featureFrame(validation);

        // Each configuration: trees, max depth, max leaves
        int[][] configurations = {
                {200, 6, 24},
                {350, 8, 48}
        };

        GradientTreeBoost bestModel = null;
        Metrics bestMetrics = null;
        double bestRmse = Double.POSITIVE_INFINITY;
        String bestConfiguration = "";

        for (int[] cfg : configurations) {

            int trees = cfg[0];
            int depth = cfg[1];
            int nodes = cfg[2];

            Properties options = new Properties();

            // Property names verified for Smile 3.1.1
            options.setProperty(
                    "smile.gradient_boost.trees",
                    Integer.toString(trees)
            );

            options.setProperty(
                    "smile.gradient_boost.loss",
                    "LeastSquares"
            );

            options.setProperty(
                    "smile.gradient_boost.max_depth",
                    Integer.toString(depth)
            );

            options.setProperty(
                    "smile.gradient_boost.max_nodes",
                    Integer.toString(nodes)
            );

            options.setProperty(
                    "smile.gradient_boost.node_size",
                    "10"
            );

            options.setProperty(
                    "smile.gradient_boost.shrinkage",
                    "0.05"
            );

            options.setProperty(
                    "smile.gradient_boost.sampling_rate",
                    "0.8"
            );

            MathEx.setSeed(42);

            System.out.printf(
                    "%nTraining GBDT: trees=%d depth=%d leaves=%d%n",
                    trees, depth, nodes
            );

            long start = System.nanoTime();

            GradientTreeBoost model =
                    GradientTreeBoost.fit(
                            Formula.lhs("delta"),
                            trainingFrame,
                            options
                    );

            double seconds =
                    (System.nanoTime() - start) / 1e9;

            Metrics metrics = evaluate(
                    model,
                    validation,
                    validationFrame
            );

            System.out.printf(
                    Locale.US,
                    "Time: %.2fs | MAE: %.4f C | RMSE: %.4f C%n",
                    seconds,
                    metrics.mae(),
                    metrics.rmse()
            );

            System.out.printf(
                    Locale.US,
                    "High-temp MAE: %.4f C | "
                            + "Precision: %.4f | Recall: %.4f | F1: %.4f%n",
                    metrics.highTemperatureMae(),
                    metrics.precision(),
                    metrics.recall(),
                    metrics.f1()
            );

            if (metrics.rmse() < bestRmse) {
                bestRmse = metrics.rmse();
                bestModel = model;
                bestMetrics = metrics;
                bestConfiguration =
                        trees + " trees, depth "
                                + depth + ", leaves " + nodes;
            }
        }

        if (bestModel == null) {
            throw new IllegalStateException(
                    "No model was trained"
            );
        }

        // Compare against persistence on identical validation data
        double baselineSquaredError = 0;
        double baselineAbsoluteError = 0;

        for (Example e : validation) {
            baselineAbsoluteError += Math.abs(e.delta());
            baselineSquaredError += e.delta() * e.delta();
        }

        System.out.println("\n===== FINAL VALIDATION COMPARISON =====");

        System.out.println("Best config: " + bestConfiguration);

        System.out.printf(
                Locale.US,
                "Persistence MAE: %.4f C%n",
                baselineAbsoluteError / validation.size()
        );

        System.out.printf(
                Locale.US,
                "Persistence RMSE: %.4f C%n",
                Math.sqrt(
                        baselineSquaredError / validation.size()
                )
        );

        System.out.printf(
                Locale.US,
                "GBDT MAE: %.4f C%n",
                bestMetrics.mae()
        );

        System.out.printf(
                Locale.US,
                "GBDT RMSE: %.4f C%n",
                bestMetrics.rmse()
        );

        // Save separately from the verified V1 models
        Path output = Path.of("models_v2");
        Files.createDirectories(output);

        Write.object(
                bestModel,
                output.resolve("forecast_gbt.ser")
        );

        Properties metadata = new Properties();

        metadata.setProperty("model.version", "v2");
        metadata.setProperty("smile.version", "3.1.1");
        metadata.setProperty("sampling.hz", "1");
        metadata.setProperty("history.seconds", "60");
        metadata.setProperty("forecast.horizon.seconds", "60");
        metadata.setProperty("target", "future_minus_current");
        metadata.setProperty(
                "feature.names",
                String.join(",", MotorForecastV2.FEATURE_NAMES)
        );
        metadata.setProperty(
                "best.configuration", bestConfiguration
        );

        try (var writer = Files.newBufferedWriter(
                output.resolve("forecast.properties"),
                StandardCharsets.UTF_8)) {
            metadata.store(writer, "Motor Forecast V2");
        }

        System.out.println(
                "\nV2 model saved to: "
                        + output.toAbsolutePath()
        );
    }

    private static List<Example> build(
            Map<Integer, List<MotorReading>> profiles,
            int stepSeconds,
            int maxExamples,
            long seed) {

        List<Example> all = new ArrayList<>();

        for (List<MotorReading> profile : profiles.values()) {

            for (int i = 60;
                 i + 60 < profile.size();
                 i += stepSeconds) {

                MotorReading past = profile.get(i - 60);
                MotorReading now = profile.get(i);
                MotorReading future = profile.get(i + 60);

                // At 2 Hz original indexing, 60s = 120 indices
                if (now.sampleIndex() - past.sampleIndex() != 120
                        || future.sampleIndex()
                        - now.sampleIndex() != 120) {
                    continue;
                }

                double[] x = MotorForecastV2.features(
                        profile, i
                );

                double current = now.statorWinding();
                double target = future.statorWinding();

                all.add(new Example(
                        x,
                        current,
                        target,
                        target - current
                ));
            }
        }

        Collections.shuffle(all, new Random(seed));

        return new ArrayList<>(
                all.subList(
                        0,
                        Math.min(maxExamples, all.size())
                )
        );
    }

    private static DataFrame trainingFrame(
            List<Example> samples) {

        int features = MotorForecastV2.FEATURE_NAMES.length;

        double[][] rows =
                new double[samples.size()][features + 1];

        for (int i = 0; i < samples.size(); i++) {
            System.arraycopy(
                    samples.get(i).features(),
                    0,
                    rows[i],
                    0,
                    features
            );
            rows[i][features] = samples.get(i).delta();
        }

        String[] names = Arrays.copyOf(
                MotorForecastV2.FEATURE_NAMES,
                features + 1
        );
        names[features] = "delta";

        return DataFrame.of(rows, names);
    }

    private static DataFrame featureFrame(
            List<Example> samples) {

        double[][] rows =
                new double[samples.size()][];

        for (int i = 0; i < samples.size(); i++) {
            rows[i] = samples.get(i).features();
        }

        return DataFrame.of(
                rows,
                MotorForecastV2.FEATURE_NAMES
        );
    }

    private static Metrics evaluate(
            GradientTreeBoost model,
            List<Example> samples,
            DataFrame frame) {

        double absoluteError = 0;
        double squaredError = 0;
        double highError = 0;
        int highCount = 0;

        int tp = 0, fp = 0, fn = 0;

        for (int i = 0; i < samples.size(); i++) {

            Example example = samples.get(i);

            double predictedDelta =
                    model.predict(frame.get(i));

            double predictedTemperature =
                    example.current() + predictedDelta;

            double error =
                    example.future() - predictedTemperature;

            absoluteError += Math.abs(error);
            squaredError += error * error;

            if (example.future() >= 100.0) {
                highError += Math.abs(error);
                highCount++;
            }

            if (example.current() < 100.0) {

                boolean actualHigh =
                        example.future() >= 100.0;

                boolean predictedHigh =
                        predictedTemperature >= 98.0;

                if (actualHigh && predictedHigh) tp++;
                else if (!actualHigh && predictedHigh) fp++;
                else if (actualHigh) fn++;
            }
        }

        int n = samples.size();

        double precision = tp + fp == 0
                ? 0 : (double) tp / (tp + fp);

        double recall = tp + fn == 0
                ? 0 : (double) tp / (tp + fn);

        double f1 = precision + recall == 0
                ? 0
                : 2 * precision * recall / (precision + recall);

        return new Metrics(
                absoluteError / n,
                Math.sqrt(squaredError / n),
                highCount == 0 ? Double.NaN
                        : highError / highCount,
                precision,
                recall,
                f1
        );
    }
}
