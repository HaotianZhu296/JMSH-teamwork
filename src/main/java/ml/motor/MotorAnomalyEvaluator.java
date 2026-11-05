package ml.motor;

import smile.anomaly.IsolationForest;

import java.util.List;
import java.util.Map;

public class MotorAnomalyEvaluator {

    public void evaluate(
            IsolationForest model,
            Map<Integer, List<MotorReading>> validationProfiles,
            double anomalyThreshold,
            double highTemperatureThreshold) {

        long total = 0;
        long anomalyCount = 0;
        long highTemperatureCount = 0;
        long bothCount = 0;

        int displayed = 0;

        System.out.println(
                "\n===== Motor Anomaly Evaluation ====="
        );

        for (List<MotorReading> profile
                : validationProfiles.values()) {

            for (MotorReading reading : profile) {

                total++;

                double score = model.score(
                        reading.anomalyFeatures()
                );

                boolean anomaly =
                        score > anomalyThreshold;

                boolean highTemperature =
                        reading.statorWinding()
                                >= highTemperatureThreshold;

                if (anomaly) {
                    anomalyCount++;
                }

                if (highTemperature) {
                    highTemperatureCount++;
                }

                if (anomaly && highTemperature) {
                    bothCount++;
                }

                // Display the first five flagged samples
                if (anomaly && displayed < 5) {

                    System.out.printf(
                            "Profile: %d | Time: %.1fs "
                                    + "| Temp: %.2f C "
                                    + "| Score: %.4f%n",
                            reading.profileId(),
                            reading.sampleIndex() / 2.0,
                            reading.statorWinding(),
                            score
                    );

                    displayed++;
                }
            }
        }

        double anomalyRate = total == 0
                ? 0.0
                : 100.0 * anomalyCount / total;

        System.out.println("\n===== Summary =====");

        System.out.println("Total records: " + total);

        System.out.println(
                "Anomaly records: " + anomalyCount
        );

        System.out.printf(
                "Anomaly rate: %.2f%%%n",
                anomalyRate
        );

        System.out.println(
                "High-temperature records: "
                        + highTemperatureCount
        );

        System.out.println(
                "Both anomaly and high temperature: "
                        + bothCount
        );

        System.out.println(
                "Anomaly but below high-temperature threshold: "
                        + (anomalyCount - bothCount)
        );

        System.out.println(
                "High temperature but not anomaly: "
                        + (highTemperatureCount - bothCount)
        );
    }
}
