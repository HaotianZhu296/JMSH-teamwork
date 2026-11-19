package ml.motor;

public record MotorSensorMessage(
        String machineId,
        int profileId,
        long sampleIndex,
        double statorWinding,
        double coolant,
        double ambient,
        double motorSpeed,
        double torque,
        double iD,
        double iQ,
        double uD,
        double uQ
) {

    public MotorReading toMotorReading() {

        // The three unused measurements are absent
        // from the compact MQTT message.
        return new MotorReading(
                profileId,
                sampleIndex,
                uQ,
                coolant,
                statorWinding,
                uD,
                Double.NaN, // statorTooth
                motorSpeed,
                iD,
                iQ,
                Double.NaN, // pm
                Double.NaN, // statorYoke
                ambient,
                torque
        );
    }
}
