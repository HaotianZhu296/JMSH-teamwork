package ml.motor;

import java.util.List;

public final class MotorGruFeatures {

    private MotorGruFeatures() {}

    public static final int WINDOW = 61;
    public static final int FEATURE_COUNT = 13;

    public static final String[] FEATURE_NAMES = {
            "stator_winding",
            "coolant",
            "ambient",
            "motor_speed",
            "torque",
            "i_d",
            "i_q",
            "u_d",
            "u_q",
            "temperature_rate_10s",
            "winding_coolant_gap",
            "current_squared",
            "power_proxy"
    };

    /**
     * Generates 13 features for one time step.
     *
     * @param profile   complete 1 Hz profile, or a 61-reading
     *                  real-time history
     * @param endIndex  index of the current prediction time
     * @param t         position in the 61-step window, 0..60
     * @param output    reusable array of length 13
     */
    public static void fill(
            List<MotorReading> profile,
            int endIndex,
            int t,
            float[] output) {

        if (endIndex < WINDOW - 1
                || endIndex >= profile.size()
                || t < 0
                || t >= WINDOW
                || output.length != FEATURE_COUNT) {

            throw new IllegalArgumentException(
                    "Invalid GRU feature window"
            );
        }

        int index = endIndex - (WINDOW - 1) + t;

        MotorReading now = profile.get(index);

        // Causal temperature rate:
        // Only readings already available at time t are used.
        int lookback = Math.min(10, t);

        double temperatureRate = 0.0;

        if (lookback > 0) {
            double pastTemperature =
                    profile.get(index - lookback)
                            .statorWinding();

            temperatureRate =
                    (now.statorWinding() - pastTemperature)
                            / lookback;
        }

        double currentSquared =
                now.iD() * now.iD()
                        + now.iQ() * now.iQ();

        double powerProxy = 1.5 * (
                now.uD() * now.iD()
                        + now.uQ() * now.iQ()
        );

        output[0] = (float) now.statorWinding();
        output[1] = (float) now.coolant();
        output[2] = (float) now.ambient();
        output[3] = (float) now.motorSpeed();
        output[4] = (float) now.torque();
        output[5] = (float) now.iD();
        output[6] = (float) now.iQ();
        output[7] = (float) now.uD();
        output[8] = (float) now.uQ();

        output[9] = (float) temperatureRate;

        output[10] = (float) (
                now.statorWinding() - now.coolant()
        );

        output[11] = (float) currentSquared;
        output[12] = (float) powerProxy;
    }
}
