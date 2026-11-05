package ml.motor;

import java.util.List;

public class TemperatureModelEvaluator {

    public void evaluate(
            List<ForecastDatasetBuilder.Example> examples,
            double[] predictions) {

        if (examples.isEmpty() ||
                examples.size() != predictions.length) {
            throw new IllegalArgumentException(
                    "Invalid evaluation data"
            );
        }

        double absoluteError = 0;
        double squaredError = 0;

        double baselineAbsoluteError = 0;
        double baselineSquaredError = 0;

        for (int i = 0; i < examples.size(); i++) {

            ForecastDatasetBuilder.Example example =
                    examples.get(i);

            double actual = example.targetTemperature();
            double predicted = predictions[i];

            double error = actual - predicted;

            absoluteError += Math.abs(error);
            squaredError += error * error;

            // Persistence baseline:
            // Future temperature = current temperature
            double baselineError =
                    actual - example.currentTemperature();

            baselineAbsoluteError += Math.abs(baselineError);
            baselineSquaredError += baselineError * baselineError;
        }

        int n = examples.size();

        double mae = absoluteError / n;
        double rmse = Math.sqrt(squaredError / n);

        double baselineMae = baselineAbsoluteError / n;
        double baselineRmse =
                Math.sqrt(baselineSquaredError / n);

        System.out.println("\n===== Forecast Evaluation =====");

        System.out.println("Samples: " + n);

        System.out.printf(
                "Random Forest MAE: %.4f C%n", mae
        );

        System.out.printf(
                "Random Forest RMSE: %.4f C%n", rmse
        );

        System.out.printf(
                "Baseline MAE: %.4f C%n", baselineMae
        );

        System.out.printf(
                "Baseline RMSE: %.4f C%n", baselineRmse
        );
    }
}
