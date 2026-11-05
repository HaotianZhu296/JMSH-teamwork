package edu.northeastern.cs5010.mhd.model;

/**
 * One reading from the Electric Motor Temperature dataset (PMSM test bench, Paderborn University).
 * Temperatures are in °C as in the CSV. {@code sampleIndex} is the 0-based position of the row inside
 * its profile in the original 2 Hz file, so after 1 Hz down-sampling it runs 0, 2, 4, ...
 */
public record MotorReading(
        int profileId,
        int sampleIndex,
        double statorWindingC,
        double coolantC,
        double ambientC,
        double motorSpeed,
        double torque,
        double iD,
        double iQ,
        double uD,
        double uQ) {

    /** Same reading with a different stator winding temperature. */
    public MotorReading withStatorWindingC(double value) {
        return new MotorReading(profileId, sampleIndex, value, coolantC, ambientC,
                motorSpeed, torque, iD, iQ, uD, uQ);
    }
}
