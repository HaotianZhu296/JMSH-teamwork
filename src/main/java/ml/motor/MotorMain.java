package ml.motor;

import smile.regression.RandomForest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class MotorMain {

    public static void main(String[] args)
            throws IOException, ClassNotFoundException {

        // ==========================================
        // Step 1: Load Motor Dataset
        // ==========================================

        System.out.println("Loading Motor Dataset...");

        MotorDataReader reader = new MotorDataReader();

        Map<Integer, List<MotorReading>> profiles =
                reader.loadData("data/measures_v2.csv", 2);

        long totalRecords = profiles.values()
                .stream()
                .mapToLong(List::size)
                .sum();

        System.out.println("Profiles: " + profiles.size());
        System.out.println("Loaded records: " + totalRecords);


        // ==========================================
        // Step 2: Profile-Based Data Split
        // ==========================================

        ProfileSplitter splitter = new ProfileSplitter();

        ProfileSplitter.Split split =
                splitter.split(profiles, 42);

        System.out.println("\n===== Profile Split =====");

        System.out.println(
                "Training profiles: " + split.train().size()
        );

        System.out.println(
                "Validation profiles: "
                        + split.validation().size()
        );

        System.out.println(
                "Testing profiles: " + split.test().size()
        );


        // ==========================================
        // Step 3: Build Forecast Dataset
        // ==========================================

        ForecastDatasetBuilder builder =
                new ForecastDatasetBuilder();

        var trainExamples = builder.build(
                split.train(),
                60,       // Predict 60 seconds ahead
                10,       // Sample every 10 seconds
                20000,    // Maximum training examples
                42        // Random seed
        );

        var validationExamples = builder.build(
                split.validation(),
                60,
                10,
                8000,
                43
        );

        System.out.println("\n===== Forecast Dataset =====");

        System.out.println(
                "Training examples: " + trainExamples.size()
        );

        System.out.println(
                "Validation examples: "
                        + validationExamples.size()
        );


        // ==========================================
        // Step 4: Train Random Forest Regression
        // ==========================================

        System.out.println(
                "\n===== Random Forest Training ====="
        );

        TemperatureForecastTrainer trainer =
                new TemperatureForecastTrainer();

        RandomForest model = trainer.train(trainExamples);

        System.out.println(
                "Number of trees: " + model.size()
        );


        // ==========================================
        // Step 5: Predict Future Temperature
        // ==========================================

        double[] predictions = trainer.predict(
                model,
                validationExamples
        );


        // ==========================================
        // Step 6: Evaluate Temperature Prediction
        // ==========================================

        TemperatureModelEvaluator evaluator =
                new TemperatureModelEvaluator();

        evaluator.evaluate(
                validationExamples,
                predictions
        );


        // ==========================================
        // Step 7: Early Warning Evaluation
        // ==========================================

        EarlyWarningEvaluator warningEvaluator =
                new EarlyWarningEvaluator();

        double actualThreshold = 100.0;

        double[] warningThresholds = {
                94.0,
                96.0,
                98.0,
                100.0
        };

        for (double warningThreshold : warningThresholds) {

            warningEvaluator.evaluate(
                    validationExamples,
                    predictions,
                    actualThreshold,
                    warningThreshold
            );
        }


        // ==========================================
        // Step 8: Current Thermal Detection
        // ==========================================

        CurrentThermalDetector detector =
                new CurrentThermalDetector(
                        100.0,  // Warning temperature
                        120.0,  // Critical temperature
                        0.5     // Maximum rise rate (C/s)
                );

        List<MotorReading> firstProfile =
                profiles.values()
                        .stream()
                        .filter(p -> p.size() >= 2)
                        .findFirst()
                        .orElseThrow();

        MotorReading previous = firstProfile.get(0);
        MotorReading current = firstProfile.get(1);

        System.out.println(
                "\n===== Current Thermal Detection ====="
        );

        System.out.printf(
                "Current winding temperature: %.2f C%n",
                current.statorWinding()
        );

        System.out.println(
                "Status: " + detector.detect(previous, current)
        );


        // ==========================================
        // Step 9: Train Motor Isolation Forest
        // ==========================================

        MotorIsolationForestTrainer anomalyTrainer =
                new MotorIsolationForestTrainer();

        MotorIsolationForestTrainer.Result anomalyResult =
                anomalyTrainer.train(
                        split.train(),
                        20000,
                        42
                );


        // ==========================================
        // Step 10: Evaluate Motor Anomalies
        // ==========================================

        MotorAnomalyEvaluator anomalyEvaluator =
                new MotorAnomalyEvaluator();

        anomalyEvaluator.evaluate(
                anomalyResult.model(),
                split.validation(),
                anomalyResult.threshold(),
                100.0
        );


        // ==========================================
        // Step 11: Save Trained Models
        // ==========================================

        System.out.println(
                "\n===== Model Persistence Test ====="
        );

        Path modelDirectory = Path.of("models");

        DetectionConfig config =
                DetectionConfig.experimental(
                        anomalyResult.threshold()
                );

        // Save both trained models and configuration
        MotorModelStore.save(
                modelDirectory,
                model,
                anomalyResult.model(),
                config
        );


        // ==========================================
        // Step 12: Load Trained Models
        // ==========================================

        MotorModelStore.LoadedModels loaded =
                MotorModelStore.load(modelDirectory);

        System.out.println(
                "Loaded Random Forest trees: "
                        + loaded.forecastModel().size()
        );

        System.out.println(
                "Loaded Isolation Forest trees: "
                        + loaded.anomalyModel().size()
        );

        System.out.printf(
                "Loaded anomaly threshold: %.4f%n",
                loaded.config().anomalyThreshold()
        );


        // ==========================================
        // Step 13: Verify Model Persistence
        // ==========================================

        var sample = validationExamples.get(0);

        double originalPrediction =
                trainer.predictOne(
                        model,
                        sample.features()
                );

        double loadedPrediction =
                trainer.predictOne(
                        loaded.forecastModel(),
                        sample.features()
                );

        double difference = Math.abs(
                originalPrediction - loadedPrediction
        );

        System.out.printf(
                "Original prediction: %.6f C%n",
                originalPrediction
        );

        System.out.printf(
                "Loaded prediction: %.6f C%n",
                loadedPrediction
        );

        System.out.printf(
                "Prediction difference: %.12f%n",
                difference
        );

        if (!Double.isFinite(difference)
                || difference > 1e-9) {

            throw new IllegalStateException(
                    "Loaded Random Forest prediction does not match"
            );
        }

        // Verify Isolation Forest persistence as well
        MotorReading anomalySample =
                split.validation()
                        .values()
                        .iterator()
                        .next()
                        .get(0);

        double originalAnomalyScore =
                anomalyResult.model().score(
                        anomalySample.anomalyFeatures()
                );

        double loadedAnomalyScore =
                loaded.anomalyModel().score(
                        anomalySample.anomalyFeatures()
                );

        double anomalyDifference = Math.abs(
                originalAnomalyScore - loadedAnomalyScore
        );

        System.out.printf(
                "Original anomaly score: %.6f%n",
                originalAnomalyScore
        );

        System.out.printf(
                "Loaded anomaly score: %.6f%n",
                loadedAnomalyScore
        );

        System.out.printf(
                "Anomaly score difference: %.12f%n",
                anomalyDifference
        );

        if (!Double.isFinite(anomalyDifference)
                || anomalyDifference > 1e-9) {

            throw new IllegalStateException(
                    "Loaded Isolation Forest score does not match"
            );
        }

        if (Double.compare(
                anomalyResult.threshold(),
                loaded.config().anomalyThreshold()
        ) != 0) {

            throw new IllegalStateException(
                    "Loaded anomaly threshold does not match"
            );
        }

        System.out.println(
                "Model persistence verification passed."
        );


        // ==========================================
        // Step 14: Unified Real-Time Detection Demo
        // ==========================================

        System.out.println(
                "\n===== Unified Detection Demo ====="
        );

        // IMPORTANT:
        // Use models loaded from disk,
        // not models directly from training.

        UnifiedDetectionService service =
                new UnifiedDetectionService(
                        loaded.forecastModel(),
                        loaded.anomalyModel(),
                        loaded.config()
                );

        // Replay a validation profile
        List<MotorReading> replayProfile =
                split.validation()
                        .values()
                        .stream()
                        .filter(p -> p.size() >= 70)
                        .findFirst()
                        .orElseThrow();

        int replayCount = Math.min(
                70,
                replayProfile.size()
        );

        for (int i = 0; i < replayCount; i++) {

            MotorReading reading =
                    replayProfile.get(i);

            DetectionResult result =
                    service.onReading(
                            "motor-demo-01",
                            reading
                    );

            // Print selected samples
            if (i == 0 || i == 59 || i == 60 || i == 69) {

                System.out.println(
                        "\nReading number: " + (i + 1)
                );

                System.out.println(
                        "Status: " + result.status()
                );

                System.out.printf(
                        "Current temperature: %.2f C%n",
                        result.currentTemperatureC()
                );

                System.out.printf(
                        "Anomaly score: %.4f%n",
                        result.anomalyScore()
                );

                System.out.println(
                        "Statistical anomaly: "
                                + result.statisticalAnomaly()
                );

                System.out.println(
                        "Predictive warning: "
                                + result.predictiveWarning()
                );

                if (result.predictedTemperatureC() == null) {

                    System.out.println(
                            "Future temperature: WARMING UP"
                    );

                } else {

                    System.out.printf(
                            "Future temperature: %.2f C%n",
                            result.predictedTemperatureC()
                    );
                }

                System.out.println(
                        "History count: "
                                + result.historyCount()
                );

                System.out.println(
                        "Reasons: " + result.reasons()
                );
            }
        }


        // ==========================================
        // Step 15: Final Independent Test Evaluation
        // ==========================================

        System.out.println(
                "\n===== FINAL TEST SET EVALUATION ====="
        );

        // Build test examples from the 11 held-out profiles
        var testExamples = builder.build(
                split.test(),
                60,
                10,
                Integer.MAX_VALUE,
                44
        );

        System.out.println(
                "Testing profiles: " + split.test().size()
        );

        System.out.println(
                "Testing examples: " + testExamples.size()
        );

        // Use the model loaded from disk
        // No retraining on the test set
        double[] testPredictions = trainer.predict(
                loaded.forecastModel(),
                testExamples
        );

        // Test 1: Temperature forecasting
        System.out.println(
                "\n===== Test 1: Temperature Forecasting ====="
        );

        evaluator.evaluate(
                testExamples,
                testPredictions
        );

        // Test 2: Future high-temperature warning
        System.out.println(
                "\n===== Test 2: Early Warning ====="
        );

        warningEvaluator.evaluate(
                testExamples,
                testPredictions,
                loaded.config().warningTemperatureC(),
                loaded.config().futureWarningTemperatureC()
        );

        // Test 3: Statistical anomaly detection
        System.out.println(
                "\n===== Test 3: Isolation Forest ====="
        );

        anomalyEvaluator.evaluate(
                loaded.anomalyModel(),
                split.test(),
                loaded.config().anomalyThreshold(),
                loaded.config().warningTemperatureC()
        );

        System.out.println(
                "\n===== FINAL TEST COMPLETED ====="
        );

        // ==========================================
        // Step 16: Finish
        // ==========================================

        System.out.println(
                "\n=================================="
        );

        System.out.println(
                "Motor ML pipeline finished."
        );

        System.out.println(
                "=================================="
        );
    }
}

