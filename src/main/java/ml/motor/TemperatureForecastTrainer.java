
package ml.motor;

import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.regression.RandomForest;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class TemperatureForecastTrainer {

    public RandomForest train(
            List<ForecastDatasetBuilder.Example> examples) {

        if (examples.isEmpty()) {
            throw new IllegalArgumentException(
                    "Training dataset is empty"
            );
        }

        int featureCount =
                ForecastDatasetBuilder.FEATURE_NAMES.length;

        double[][] trainingData =
                new double[examples.size()][featureCount + 1];

        for (int i = 0; i < examples.size(); i++) {

            ForecastDatasetBuilder.Example example =
                    examples.get(i);

            System.arraycopy(
                    example.features(),
                    0,
                    trainingData[i],
                    0,
                    featureCount
            );

            trainingData[i][featureCount] =
                    example.targetTemperature();
        }

        String[] columns = Arrays.copyOf(
                ForecastDatasetBuilder.FEATURE_NAMES,
                featureCount + 1
        );

        columns[featureCount] = "target";

        DataFrame frame =
                DataFrame.of(trainingData, columns);

        Properties options = new Properties();

        options.setProperty(
                "smile.random_forest.trees", "50"
        );

        options.setProperty(
                "smile.random_forest.max_depth", "12"
        );

        options.setProperty(
                "smile.random_forest.max_nodes", "128"
        );

        options.setProperty(
                "smile.random_forest.node_size", "8"
        );

        long start = System.nanoTime();

        RandomForest model = RandomForest.fit(
                Formula.lhs("target"),
                frame,
                options
        );

        long end = System.nanoTime();

        System.out.printf(
                "Training time: %.2f seconds%n",
                (end - start) / 1_000_000_000.0
        );

        return model;
    }

    public double[] predict(
            RandomForest model,
            List<ForecastDatasetBuilder.Example> examples) {

        if (examples.isEmpty()) {
            return new double[0];
        }

        double[][] features =
                new double[examples.size()][];

        for (int i = 0; i < examples.size(); i++) {
            features[i] = examples.get(i).features();
        }

        DataFrame frame = DataFrame.of(
                features,
                ForecastDatasetBuilder.FEATURE_NAMES
        );

        double[] predictions = new double[examples.size()];

        for (int i = 0; i < examples.size(); i++) {
            predictions[i] = model.predict(frame.get(i));
        }

        return predictions;
    }


    public double predictOne(
            RandomForest model,
            double[] features) {

        if (features.length !=
                ForecastDatasetBuilder.FEATURE_NAMES.length) {

            throw new IllegalArgumentException(
                    "Feature count does not match model input"
            );
        }

        DataFrame frame = DataFrame.of(
                new double[][]{features},
                ForecastDatasetBuilder.FEATURE_NAMES
        );

        return model.predict(frame.get(0));
    }

}

