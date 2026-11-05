package ml.motor;

public record MotorReading(
        int profileId,
        long sampleIndex,
        double uQ,
        double coolant,
        double statorWinding,
        double uD,
        double statorTooth,
        double motorSpeed,
        double iD,
        double iQ,
        double pm,
        double statorYoke,
        double ambient,
        double torque
) {

    public double temperatureDifference() {
        return statorWinding - ambient;
    }

    public double[] anomalyFeatures() {
        return new double[]{
                statorWinding,
                coolant,
                ambient,
                motorSpeed,
                torque,
                iD,
                iQ,
                temperatureDifference()
        };
    }
}

