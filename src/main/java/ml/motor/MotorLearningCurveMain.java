
package ml.motor;

import org.knowm.xchart.ChartEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;

import smile.regression.RandomForest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MotorLearningCurveMain {

    public static void main(String[] args) throws Exception {

        // ==========================================
        // Step 1: Load and split data
        // ==========================================

        System.out.println("Loading motor dataset...");

        MotorDataReader reader = new MotorDataReader();

        var profiles = reader.loadData(
                "data/measures_v2.csv",
                2
        );

        ProfileSplitter splitter = new ProfileSplitter();

        var split = splitter.split(profiles, 42);

        // ==========================================
        // Step 2: Build all available train examples
        // ==========================================

        ForecastDatasetBuilder builder =
                new ForecastDatasetBuilder();

        var allTrainExamples = builder.build(
                split.train(),
                60,
                1,
                Integer.MAX_VALUE,
                42
        );

        // Validation is always the same set
        var validationExamples = builder.build(
                split.validation(),
                60,
                10,
                8000,
                43
        );

        System.out.println(
                "Available training examples: "
                        + allTrainExamples.size()
        );

        System.out.println(
                "Validation examples: "
                        + validationExamples.size()
        );

        // ==========================================
        // Step 3: Training sample sizes
        // ==========================================

        int[] requestedSizes = {
                5000,
                20000,
                50000,
                100000,
                allTrainExamples.size()
        };

        List<Integer> sizes = new ArrayList<>();
        List<Double> trainMse = new ArrayList<>();
        List<Double> validationMse = new ArrayList<>();
        List<Double> validationMae = new ArrayList<>();
        List<Double> validationRmse = new ArrayList<>();

        TemperatureForecastTrainer trainer =
                new TemperatureForecastTrainer();

        RandomForest fullTrainingModel = null;
        int previousSize = 0;

        // ==========================================
        // Step 4: Train at different sample sizes
        // ==========================================

        for (int requested : requestedSizes) {

            int size = Math.min(
                    requested,
                    allTrainExamples.size()
            );

            if (size <= previousSize) {
                continue;
            }

            previousSize = size;

            System.out.println(
                    "\n===== Training with "
                            + size + " examples ====="
            );

            List<ForecastDatasetBuilder.Example> subset =
                    allTrainExamples.subList(0, size);

            // Trains a fresh Random Forest
            RandomForest model = trainer.train(subset);

            // Evaluate on the training subset
            double[] trainPredictions =
                    trainer.predict(model, subset);

            // Evaluate on fixed validation data
            double[] valPredictions =
                    trainer.predict(
                            model,
                            validationExamples
                    );

            double trainError = mse(
                    subset,
                    trainPredictions
            );

            double valError = mse(
                    validationExamples,
                    valPredictions
            );

            double valMae = mae(
                    validationExamples,
                    valPredictions
            );

            double valRmse = Math.sqrt(valError);

            sizes.add(size);
            trainMse.add(trainError);
            validationMse.add(valError);
            validationMae.add(valMae);
            validationRmse.add(valRmse);

            System.out.printf(
                    Locale.US,
                    "Train MSE: %.4f%n",
                    trainError
            );

            System.out.printf(
                    Locale.US,
                    "Validation MSE: %.4f%n",
                    valError
            );

            System.out.printf(
                    Locale.US,
                    "Validation MAE: %.4f C%n",
                    valMae
            );

            System.out.printf(
                    Locale.US,
                    "Validation RMSE: %.4f C%n",
                    valRmse
            );

            // Save reference to the model trained
            // with all available train examples.
            if (size == allTrainExamples.size()) {
                fullTrainingModel = model;
            }
        }

        // ==========================================
        // Step 5: Save experiment data
        // ==========================================

        Path outputDir = Path.of("results");

        Files.createDirectories(outputDir);

        StringBuilder csv = new StringBuilder();

        csv.append(
                "training_samples,train_mse,"
                        + "validation_mse,validation_mae,"
                        + "validation_rmse\n"
        );

        for (int i = 0; i < sizes.size(); i++) {

            csv.append(String.format(
                    Locale.US,
                    "%d,%.6f,%.6f,%.6f,%.6f%n",
                    sizes.get(i),
                    trainMse.get(i),
                    validationMse.get(i),
                    validationMae.get(i),
                    validationRmse.get(i)
            ));
        }

        Files.writeString(
                outputDir.resolve("learning_curve.csv"),
                csv.toString(),
                StandardCharsets.UTF_8
        );

        // ==========================================
        // Step 6: Generate learning curve PNG
        // ==========================================

        XYChart chart = new XYChartBuilder()
                .width(1000)
                .height(650)
                .title("Random Forest Learning Curve")
                .xAxisTitle("Training Samples")
                .yAxisTitle("Mean Squared Error (C^2)")
                .build();

        chart.addSeries(
                "Training MSE",
                sizes,
                trainMse
        );

        chart.addSeries(
                "Validation MSE",
                sizes,
                validationMse
        );

        ChartEncoder.saveChart(
                chart,
                outputDir
                        .resolve("learning_curve")
                        .toString(),
                "png"
        );

        System.out.println(
                "\nLearning curve saved to results/"
        );

        if (fullTrainingModel != null) {
            System.out.println(
                    "Full-training model trees: "
                            + fullTrainingModel.size()
            );
        }

        System.out.println(
                "Learning curve experiment completed."
        );
    }

    private static double mse(
            List<ForecastDatasetBuilder.Example> examples,
            double[] predictions) {

        if (examples.isEmpty()
                || examples.size() != predictions.length) {
            throw new IllegalArgumentException(
                    "Invalid MSE inputs"
            );
        }

        double sum = 0.0;

        for (int i = 0; i < examples.size(); i++) {

            double error =
                    predictions[i]
                            - examples.get(i).targetTemperature();

            sum += error * error;
        }

        return sum / examples.size();
    }

    private static double mae(
            List<ForecastDatasetBuilder.Example> examples,
            double[] predictions) {

        if (examples.isEmpty()
                || examples.size() != predictions.length) {
            throw new IllegalArgumentException(
                    "Invalid MAE inputs"
            );
        }

        double sum = 0.0;

        for (int i = 0; i < examples.size(); i++) {

            sum += Math.abs(
                    predictions[i]
                            - examples.get(i).targetTemperature()
            );
        }

        return sum / examples.size();
    }
}
