package ml.motor;

import java.util.List;

public class EarlyWarningEvaluator {

    public void evaluate(
            List<ForecastDatasetBuilder.Example> examples,
            double[] predictions,
            double actualThreshold,
            double warningThreshold) {

        if (examples.isEmpty() ||
                examples.size() != predictions.length) {
            throw new IllegalArgumentException(
                    "Invalid evaluation data"
            );
        }

        int TP = 0;
        int FP = 0;
        int TN = 0;
        int FN = 0;

        int eligible = 0;

        for (int i = 0; i < examples.size(); i++) {

            var example = examples.get(i);

            double current = example.currentTemperature();
            double actualFuture = example.targetTemperature();
            double predictedFuture = predictions[i];

            // Only evaluate currently below-limit samples
            if (current >= actualThreshold) {
                continue;
            }

            eligible++;

            // Ground truth stays fixed
            boolean actualHigh =
                    actualFuture >= actualThreshold;

            // Adjust only the warning decision threshold
            boolean predictedHigh =
                    predictedFuture >= warningThreshold;

            if (predictedHigh && actualHigh) {
                TP++;
            } else if (predictedHigh) {
                FP++;
            } else if (actualHigh) {
                FN++;
            } else {
                TN++;
            }
        }

        double precision = TP + FP == 0
                ? 0.0 : (double) TP / (TP + FP);

        double recall = TP + FN == 0
                ? 0.0 : (double) TP / (TP + FN);

        double f1 = precision + recall == 0
                ? 0.0
                : 2 * precision * recall
                / (precision + recall);

        double falsePositiveRate = FP + TN == 0
                ? 0.0 : (double) FP / (FP + TN);

        System.out.println(
                "\n===== Early Warning Evaluation ====="
        );

        System.out.println(
                "Actual temperature threshold: "
                        + actualThreshold
        );

        System.out.println(
                "Prediction warning threshold: "
                        + warningThreshold
        );

        System.out.println("Eligible: " + eligible);
        System.out.println("TP: " + TP);
        System.out.println("FP: " + FP);
        System.out.println("TN: " + TN);
        System.out.println("FN: " + FN);

        System.out.printf("Precision: %.4f%n", precision);
        System.out.printf("Recall: %.4f%n", recall);
        System.out.printf("F1 Score: %.4f%n", f1);
        System.out.printf(
                "False Positive Rate: %.4f%n",
                falsePositiveRate
        );
    }
}

