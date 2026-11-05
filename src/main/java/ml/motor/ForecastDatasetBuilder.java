package ml.motor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ForecastDatasetBuilder {

    // Feature names and order must remain consistent.
    public static final String[] FEATURE_NAMES = {
            "winding_now",
            "winding_30s",
            "winding_60s",
            "rise_60s",
            "coolant",
            "ambient",
            "speed",
            "torque",
            "id",
            "iq",
            "ud",
            "uq"
    };

    public record Example(
            double[] features,
            double targetTemperature,
            double currentTemperature
    ) {}

    public List<Example> build(
            Map<Integer, List<MotorReading>> profiles,
            int horizonSeconds,
            int sampleStepSeconds,
            int maxExamples,
            long seed) {

        if (horizonSeconds < 1 ||
                sampleStepSeconds < 1 ||
                maxExamples < 1) {
            throw new IllegalArgumentException(
                    "Invalid forecasting parameters"
            );
        }

        List<Example> examples = new ArrayList<>();

        for (List<MotorReading> readings : profiles.values()) {

            // Assumes the reader has converted 2 Hz to 1 Hz.
            for (int i = 60;
                 i + horizonSeconds < readings.size();
                 i += sampleStepSeconds) {

                MotorReading current = readings.get(i);
                MotorReading lag30 = readings.get(i - 30);
                MotorReading lag60 = readings.get(i - 60);

                double[] features = {
                        current.statorWinding(),
                        lag30.statorWinding(),
                        lag60.statorWinding(),

                        current.statorWinding()
                                - lag60.statorWinding(),

                        current.coolant(),
                        current.ambient(),
                        current.motorSpeed(),
                        current.torque(),
                        current.iD(),
                        current.iQ(),
                        current.uD(),
                        current.uQ()
                };

                double target =
                        readings.get(i + horizonSeconds)
                                .statorWinding();

                examples.add(new Example(
                        features,
                        target,
                        current.statorWinding()
                ));
            }
        }

        Collections.shuffle(examples, new Random(seed));

        int limit = Math.min(maxExamples, examples.size());

        return new ArrayList<>(
                examples.subList(0, limit)
        );
    }
}
