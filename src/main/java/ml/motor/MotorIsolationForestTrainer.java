package ml.motor;

import smile.anomaly.IsolationForest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

public class MotorIsolationForestTrainer {

    public record Result(
            IsolationForest model,
            double threshold,
            int trainingSamples
    ) {}

    public Result train(
            Map<Integer, List<MotorReading>> trainingProfiles,
            int maxSamples,
            long seed) {

        if (maxSamples < 100) {
            throw new IllegalArgumentException(
                    "maxSamples must be at least 100"
            );
        }

        // Step 1: Collect training records
        List<MotorReading> allReadings =
                new ArrayList<>();

        for (List<MotorReading> profile
                : trainingProfiles.values()) {

            allReadings.addAll(profile);
        }

        if (allReadings.isEmpty()) {
            throw new IllegalArgumentException(
                    "Training data is empty"
            );
        }

        // Step 2: Randomly sample training records
        Collections.shuffle(
                allReadings,
                new Random(seed)
        );

        int n = Math.min(
                maxSamples,
                allReadings.size()
        );

        // Step 3: Extract eight features
        double[][] X = new double[n][];

        for (int i = 0; i < n; i++) {
            X[i] = allReadings.get(i)
                    .anomalyFeatures();
        }

        // Step 4: Configure Isolation Forest
        Properties options = new Properties();

        options.setProperty(
                "smile.isolation_forest.trees", "100"
        );

        options.setProperty(
                "smile.isolation_forest.extension_level", "0"
        );

        // Step 5: Train model
        long start = System.nanoTime();

        IsolationForest model =
                IsolationForest.fit(X, options);

        long end = System.nanoTime();

        double seconds =
                (end - start) / 1_000_000_000.0;

        // Step 6: Calculate training anomaly scores
        double[] scores = model.score(X);

        Arrays.sort(scores);

        // Step 7: Initial threshold = 95th percentile
        int index = (int) Math.ceil(
                scores.length * 0.95
        ) - 1;

        double threshold = scores[index];

        System.out.println(
                "\n===== Isolation Forest Training ====="
        );

        System.out.println(
                "Training samples: " + n
        );

        System.out.println(
                "Number of trees: " + model.size()
        );

        System.out.printf(
                "Training time: %.2f seconds%n",
                seconds
        );

        System.out.printf(
                "Anomaly threshold: %.4f%n",
                threshold
        );

        return new Result(
                model,
                threshold,
                n
        );
    }
}

