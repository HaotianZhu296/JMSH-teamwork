
package ml.motor;

import java.io.IOException;
import java.nio.file.Path;

public class MotorInferenceMain {

    public static void main(String[] args)
            throws IOException, ClassNotFoundException {

        // =====================================
        // Step 1: Load trained models
        // =====================================

        System.out.println(
                "===== Standalone Motor Inference ====="
        );

        Path modelDirectory = args.length > 0
                ? Path.of(args[0])
                : Path.of("models");

        MotorModelStore.LoadedModels loaded =
                MotorModelStore.load(modelDirectory);

        System.out.println("Models loaded successfully.");

        System.out.println(
                "Random Forest trees: "
                        + loaded.forecastModel().size()
        );

        System.out.println(
                "Isolation Forest trees: "
                        + loaded.anomalyModel().size()
        );

        // =====================================
        // Step 2: Initialize detection service
        // =====================================

        UnifiedDetectionService service =
                new UnifiedDetectionService(
                        loaded.forecastModel(),
                        loaded.anomalyModel(),
                        loaded.config()
                );

        // =====================================
        // Step 3: Simulate 70 incoming readings
        // =====================================

        System.out.println(
                "\n===== Streaming Inference Test ====="
        );

        for (int i = 0; i < 70; i++) {

            // Synthetic measurements for an API test.
            // These are NOT real dataset records.
            MotorSensorMessage message =
                    new MotorSensorMessage(
                            "motor-demo-01",
                            999,
                            i * 2L,
                            25.0 + i * 0.2,
                            25.0,
                            22.0,
                            1500.0,
                            30.0,
                            1.0,
                            2.0,
                            40.0,
                            80.0
                    );

            DetectionResult result =
                    service.onReading(message);

            if (i == 0 || i == 59
                    || i == 60 || i == 69) {

                printResult(i + 1, result);
            }

            // Verify the 61-reading history requirement.
            if (i == 59
                    && result.predictedTemperatureC() != null) {

                throw new IllegalStateException(
                        "Forecast started before 61 readings"
                );
            }

            if (i == 60
                    && result.predictedTemperatureC() == null) {

                throw new IllegalStateException(
                        "Forecast did not start at reading 61"
                );
            }
        }

        // =====================================
        // Step 4: Verify profile switching
        // =====================================

        System.out.println(
                "\n===== Profile Reset Test ====="
        );

        MotorSensorMessage newProfileMessage =
                new MotorSensorMessage(
                        "motor-demo-01",
                        1000,
                        0L,
                        26.0,
                        25.0,
                        22.0,
                        1500.0,
                        30.0,
                        1.0,
                        2.0,
                        40.0,
                        80.0
                );

        DetectionResult resetResult =
                service.onReading(newProfileMessage);

        printResult(1, resetResult);

        if (resetResult.historyCount() != 1
                || resetResult.predictedTemperatureC() != null) {

            throw new IllegalStateException(
                    "Profile reset verification failed"
            );
        }

        System.out.println(
                "\nStandalone inference verification passed."
        );
    }

    private static void printResult(
            int readingNumber,
            DetectionResult result) {

        System.out.println(
                "\nReading number: " + readingNumber
        );

        System.out.println(
                "Machine: " + result.machineId()
        );

        System.out.println(
                "Profile: " + result.profileId()
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
                "History: " + result.historyCount()
        );

        System.out.println(
                "Reasons: " + result.reasons()
        );
    }
}