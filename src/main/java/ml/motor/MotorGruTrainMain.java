package ml.motor;

import ai.djl.Device;
import ai.djl.Model;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Activation;
import ai.djl.nn.LambdaBlock;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.nn.recurrent.GRU;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.EasyTrain;
import ai.djl.training.Trainer;
import ai.djl.training.dataset.ArrayDataset;
import ai.djl.training.dataset.Batch;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Optimizer;
import ai.djl.training.tracker.Tracker;

import org.knowm.xchart.ChartEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MotorGruTrainMain {

    static final int WINDOW = 61;
    static final int FEATURES = 9;
    static final int HORIZON = 60;

    static final int MAX_TRAIN = 120000;
    static final int MAX_VALIDATION = 8000;
    static final int EPOCHS = 40;
    static final int PATIENCE = 8;

    record WindowRef(List<MotorReading> profile, int index) {}

    record Scale(
            double[] mean,
            double[] std,
            double targetMean,
            double targetStd
    ) {}

    record Data(float[] x, float[] y, int count) {}

    static float[] raw(MotorReading r) {
        return new float[] {
                (float) r.statorWinding(),
                (float) r.coolant(),
                (float) r.ambient(),
                (float) r.motorSpeed(),
                (float) r.torque(),
                (float) r.iD(),
                (float) r.iQ(),
                (float) r.uD(),
                (float) r.uQ()
        };
    }

    static List<WindowRef> windows(
            Map<Integer, List<MotorReading>> profiles,
            int step,
            int limit,
            long seed) {

        List<WindowRef> result = new ArrayList<>();

        for (var profile : profiles.values()) {
            for (int i = 60;
                 i + HORIZON < profile.size();
                 i += step) {

                // Original CSV index advances by 2 at 1 Hz.
                long previous = profile.get(i - 60).sampleIndex();
                long current = profile.get(i).sampleIndex();
                long future = profile.get(i + 60).sampleIndex();

                if (current - previous != 120
                        || future - current != 120) {
                    continue;
                }

                result.add(new WindowRef(profile, i));
            }
        }

        Collections.shuffle(result, new Random(seed));

        return new ArrayList<>(result.subList(
                0,
                Math.min(limit, result.size())
        ));
    }

    static double delta(WindowRef w) {
        return w.profile().get(w.index() + HORIZON)
                .statorWinding()
                - w.profile().get(w.index()).statorWinding();
    }

    static Scale calculateScale(
            Map<Integer, List<MotorReading>> trainingProfiles,
            List<WindowRef> trainWindows) {

        double[] mean = new double[FEATURES];
        double[] std = new double[FEATURES];

        long count = 0;

        // Only Training Profiles contribute to scaling.
        for (var profile : trainingProfiles.values()) {
            for (MotorReading reading : profile) {
                float[] f = raw(reading);

                for (int j = 0; j < FEATURES; j++) {
                    mean[j] += f[j];
                    std[j] += (double) f[j] * f[j];
                }

                count++;
            }
        }

        for (int j = 0; j < FEATURES; j++) {
            mean[j] /= count;

            std[j] = Math.sqrt(
                    Math.max(0, std[j] / count
                            - mean[j] * mean[j])
            );

            std[j] = Math.max(std[j], 1e-6);
        }

        double targetMean = 0;
        double targetSquared = 0;

        for (WindowRef w : trainWindows) {
            double d = delta(w);
            targetMean += d;
            targetSquared += d * d;
        }

        targetMean /= trainWindows.size();

        double targetStd = Math.sqrt(Math.max(
                0,
                targetSquared / trainWindows.size()
                        - targetMean * targetMean
        ));

        targetStd = Math.max(targetStd, 1e-6);

        return new Scale(mean, std, targetMean, targetStd);
    }

    static Data encode(
            List<WindowRef> windows,
            Scale scale) {

        int n = windows.size();

        float[] x = new float[n * WINDOW * FEATURES];
        float[] y = new float[n];

        for (int s = 0; s < n; s++) {
            WindowRef w = windows.get(s);

            for (int t = 0; t < WINDOW; t++) {
                MotorReading reading =
                        w.profile().get(w.index() - 60 + t);

                float[] features = raw(reading);

                for (int j = 0; j < FEATURES; j++) {
                    int offset =
                            (s * WINDOW + t) * FEATURES + j;

                    x[offset] = (float) (
                            (features[j] - scale.mean()[j])
                                    / scale.std()[j]
                    );
                }
            }

            y[s] = (float) (
                    (delta(w) - scale.targetMean())
                            / scale.targetStd()
            );
        }

        return new Data(x, y, n);
    }

    static SequentialBlock createNetwork() {

        SequentialBlock network = new SequentialBlock();

        network.add(
                GRU.builder()
                        .setStateSize(32)
                        .setNumLayers(1)
                        .build()
        );

        // Keep the GRU output at the last time step.
        network.add(LambdaBlock.singleton(
                array -> array.get(
                        new NDIndex(":, -1, :")
                ),
                "lastTimeStep"
        ));

        network.add(
                Linear.builder().setUnits(16).build()
        );

        network.add(Activation::relu);

        network.add(
                Linear.builder().setUnits(1).build()
        );

        return network;
    }

    static ArrayDataset dataset(
            NDManager manager,
            Data data,
            int batchSize,
            boolean shuffle) {

        NDArray x = manager.create(
                data.x(),
                new Shape(data.count(), WINDOW, FEATURES)
        );

        NDArray y = manager.create(
                data.y(),
                new Shape(data.count(), 1)
        );

        return new ArrayDataset.Builder()
                .setData(x)
                .optLabels(y)
                .setSampling(batchSize, shuffle)
                .build();
    }

    // Returns MAE and MSE, both in Celsius units.
    static double[] evaluate(
            Trainer trainer,
            ArrayDataset dataset,
            Scale scale) throws Exception {

        double absolute = 0;
        double squared = 0;
        long count = 0;

        for (Batch batch : trainer.iterateDataset(dataset)) {

            try (batch) {
                float[] predictions = trainer
                        .evaluate(batch.getData())
                        .head()
                        .toFloatArray();

                float[] targets = batch
                        .getLabels()
                        .head()
                        .toFloatArray();

                for (int i = 0; i < predictions.length; i++) {

                    double error = (
                            predictions[i] - targets[i]
                    ) * scale.targetStd();

                    absolute += Math.abs(error);
                    squared += error * error;
                    count++;
                }
            }
        }

        return new double[] {
                absolute / count,
                squared / count
        };
    }

    public static void main(String[] args) throws Exception {

        System.out.println("Loading Motor Dataset...");

        var profiles = new MotorDataReader()
                .loadData("data/measures_v2.csv", 2);

        var split = new ProfileSplitter()
                .split(profiles, 42);

        List<WindowRef> trainingWindows = windows(
                split.train(), 2, MAX_TRAIN, 42
        );

        List<WindowRef> validationWindows = windows(
                split.validation(), 5, MAX_VALIDATION, 43
        );

        if (trainingWindows.isEmpty()
                || validationWindows.isEmpty()) {
            throw new IllegalStateException("Empty dataset");
        }

        Scale scale = calculateScale(
                split.train(), trainingWindows
        );

        Data trainingData = encode(trainingWindows, scale);
        Data validationData = encode(validationWindows, scale);

        System.out.println(
                "Train windows: " + trainingData.count()
        );
        System.out.println(
                "Validation windows: " + validationData.count()
        );

        Path output = Path.of("models_gru");
        Files.createDirectories(output);

        List<Integer> epochs = new ArrayList<>();
        List<Double> trainingMse = new ArrayList<>();
        List<Double> validationMse = new ArrayList<>();

        try (Model model = Model.newInstance(
                "motor_gru", "PyTorch")) {

            model.setBlock(createNetwork());

            var config = new DefaultTrainingConfig(
                    Loss.l2Loss()
            ).optOptimizer(
                    Optimizer.adam()
                            .optLearningRateTracker(
                                    Tracker.fixed(0.0002f)
                            )
                            .build()
            ).optDevices(new Device[]{Device.cpu()});

            try (Trainer trainer = model.newTrainer(config)) {

                trainer.initialize(
                        new Shape(1, WINDOW, FEATURES)
                );

                NDManager manager = model.getNDManager();

                ArrayDataset train = dataset(
                        manager, trainingData, 64, true
                );

                ArrayDataset trainEval = dataset(
                        manager, trainingData, 256, false
                );

                ArrayDataset validation = dataset(
                        manager, validationData, 256, false
                );

                double bestMse = Double.POSITIVE_INFINITY;
                int badEpochs = 0;

                for (int epoch = 1; epoch <= EPOCHS; epoch++) {

                    for (Batch batch : trainer.iterateDataset(train)) {
                        try (batch) {
                            EasyTrain.trainBatch(trainer, batch);
                            trainer.step();
                        }
                    }

                    double[] trainMetrics = evaluate(
                            trainer, trainEval, scale
                    );

                    double[] valMetrics = evaluate(
                            trainer, validation, scale
                    );

                    double trMse = trainMetrics[1];
                    double vaMse = valMetrics[1];

                    epochs.add(epoch);
                    trainingMse.add(trMse);
                    validationMse.add(vaMse);

                    System.out.printf(
                            Locale.US,
                            "Epoch %02d | Train MSE: %.4f"
                                    + " | Val MSE: %.4f"
                                    + " | Val MAE: %.4f C"
                                    + " | Val RMSE: %.4f C%n",
                            epoch,
                            trMse,
                            vaMse,
                            valMetrics[0],
                            Math.sqrt(vaMse)
                    );

                    if (vaMse < bestMse - 1e-4) {
                        bestMse = vaMse;
                        badEpochs = 0;

                        model.save(output, "motor_gru");

                    } else {
                        badEpochs++;
                    }

                    if (badEpochs >= PATIENCE) {
                        System.out.println("Early stopping.");
                        break;
                    }
                }

                System.out.printf(
                        Locale.US,
                        "Best validation RMSE: %.4f C%n",
                        Math.sqrt(bestMse)
                );
            }
        }

        // Save normalization parameters
        Properties props = new Properties();

        props.setProperty("sampling.hz", "1");
        props.setProperty("window.size", "61");
        props.setProperty("forecast.seconds", "60");
        props.setProperty("feature.count", "9");
        props.setProperty(
                "feature.names",
                "stator_winding,coolant,ambient,motor_speed,"
                        + "torque,i_d,i_q,u_d,u_q"
        );

        props.setProperty(
                "target.mean",
                Double.toString(scale.targetMean())
        );

        props.setProperty(
                "target.std",
                Double.toString(scale.targetStd())
        );

        for (int i = 0; i < FEATURES; i++) {
            props.setProperty(
                    "feature.mean." + i,
                    Double.toString(scale.mean()[i])
            );
            props.setProperty(
                    "feature.std." + i,
                    Double.toString(scale.std()[i])
            );
        }

        try (var writer = Files.newBufferedWriter(
                output.resolve("normalization.properties"),
                StandardCharsets.UTF_8)) {
            props.store(writer, "GRU normalization");
        }

        // Save epoch metrics
        StringBuilder csv = new StringBuilder(
                "epoch,training_mse,validation_mse\n"
        );

        for (int i = 0; i < epochs.size(); i++) {
            csv.append(String.format(
                    Locale.US,
                    "%d,%.6f,%.6f%n",
                    epochs.get(i),
                    trainingMse.get(i),
                    validationMse.get(i)
            ));
        }

        Files.writeString(
                output.resolve("loss_history.csv"),
                csv.toString()
        );

        // Generate actual epoch loss curve
        XYChart chart = new XYChartBuilder()
                .width(1000)
                .height(650)
                .title("GRU Training and Validation Loss")
                .xAxisTitle("Epoch")
                .yAxisTitle("MSE (C^2)")
                .build();

        chart.addSeries(
                "Training MSE",
                epochs,
                trainingMse
        );

        chart.addSeries(
                "Validation MSE",
                epochs,
                validationMse
        );

        ChartEncoder.saveChart(
                chart,
                output.resolve("gru_loss_curve").toString(),
                "png"
        );

        System.out.println(
                "GRU artifacts saved to: "
                        + output.toAbsolutePath()
        );
    }
}
