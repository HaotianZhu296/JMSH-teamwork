
package ml.motor;

import smile.data.DataFrame;
import smile.regression.GradientTreeBoost;

import java.util.List;

public final class MotorForecastV2 {

    private MotorForecastV2() {}

    public static final int HISTORY_SECONDS = 60;
    public static final int HORIZON_SECONDS = 60;

    public static final String[] FEATURE_NAMES = {
            "w_now", "w_5", "w_15", "w_30", "w_60",
            "rate10", "rate30", "rate60",
            "w_mean10", "w_mean30", "w_mean60", "w_std30",
            "coolant", "coolant_30", "ambient", "w_coolant_gap",
            "speed", "speed_mean30", "speed_delta30",
            "torque", "torque_mean30", "torque_delta30",
            "id", "iq", "i2", "i2_mean30",
            "ud", "uq", "power_proxy", "power_proxy_mean30"
    };

    public static double[] features(
            List<MotorReading> readings, int i) {

        if (i < HISTORY_SECONDS || i >= readings.size()) {
            throw new IllegalArgumentException(
                    "Need 61 readings ending at index i"
            );
        }

        MotorReading now = readings.get(i);
        MotorReading lag30 = readings.get(i - 30);
        MotorReading lag60 = readings.get(i - 60);

        double sumW10 = 0;
        double sumW30 = 0;
        double sumW60 = 0;
        double sumW30Squared = 0;

        double sumSpeed30 = 0;
        double sumTorque30 = 0;
        double sumCurrentSquared30 = 0;
        double sumPower30 = 0;

        for (int lag = 0; lag < 60; lag++) {

            MotorReading r = readings.get(i - lag);
            double winding = r.statorWinding();

            sumW60 += winding;

            if (lag < 10) {
                sumW10 += winding;
            }

            if (lag < 30) {
                sumW30 += winding;
                sumW30Squared += winding * winding;

                sumSpeed30 += r.motorSpeed();
                sumTorque30 += r.torque();

                double currentSquared =
                        r.iD() * r.iD()
                                + r.iQ() * r.iQ();

                sumCurrentSquared30 += currentSquared;

                sumPower30 += 1.5 * (
                        r.uD() * r.iD()
                                + r.uQ() * r.iQ()
                );
            }
        }

        double meanW30 = sumW30 / 30.0;

        double varianceW30 =
                sumW30Squared / 30.0
                        - meanW30 * meanW30;

        double stdW30 = Math.sqrt(
                Math.max(0, varianceW30)
        );

        double currentSquaredNow =
                now.iD() * now.iD()
                        + now.iQ() * now.iQ();

        double powerNow = 1.5 * (
                now.uD() * now.iD()
                        + now.uQ() * now.iQ()
        );

        return new double[] {
                now.statorWinding(),
                readings.get(i - 5).statorWinding(),
                readings.get(i - 15).statorWinding(),
                lag30.statorWinding(),
                lag60.statorWinding(),

                (now.statorWinding()
                        - readings.get(i - 10).statorWinding()) / 10.0,
                (now.statorWinding()
                        - lag30.statorWinding()) / 30.0,
                (now.statorWinding()
                        - lag60.statorWinding()) / 60.0,

                sumW10 / 10.0,
                meanW30,
                sumW60 / 60.0,
                stdW30,

                now.coolant(),
                lag30.coolant(),
                now.ambient(),
                now.statorWinding() - now.coolant(),

                now.motorSpeed(),
                sumSpeed30 / 30.0,
                now.motorSpeed() - lag30.motorSpeed(),

                now.torque(),
                sumTorque30 / 30.0,
                now.torque() - lag30.torque(),

                now.iD(),
                now.iQ(),
                currentSquaredNow,
                sumCurrentSquared30 / 30.0,

                now.uD(),
                now.uQ(),
                powerNow,
                sumPower30 / 30.0
        };
    }

    public static double predictTemperature(
            GradientTreeBoost model,
            List<MotorReading> last61Readings) {

        if (last61Readings.size() != 61) {
            throw new IllegalArgumentException(
                    "Exactly 61 readings are required"
            );
        }

        // Check 1 Hz sample continuity
        for (int i = 1; i < last61Readings.size(); i++) {

            MotorReading before = last61Readings.get(i - 1);
            MotorReading after = last61Readings.get(i);

            if (before.profileId() != after.profileId()
                    || after.sampleIndex()
                    - before.sampleIndex() != 2) {

                throw new IllegalArgumentException(
                        "Discontinuous motor history"
                );
            }
        }

        double[] x = features(last61Readings, 60);

        DataFrame frame = DataFrame.of(
                new double[][] { x },
                FEATURE_NAMES
        );

        double predictedDelta =
                model.predict(frame.get(0));

        return last61Readings.get(60).statorWinding()
                + predictedDelta;
    }
}

