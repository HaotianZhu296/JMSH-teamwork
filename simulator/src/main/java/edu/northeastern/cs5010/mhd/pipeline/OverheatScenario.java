package edu.northeastern.cs5010.mhd.pipeline;

import edu.northeastern.cs5010.mhd.model.MotorReading;

/**
 * Scripted overheating for integration tests, applied to {@code stator_winding}.
 *
 * <p>Starting at the {@code startReading}-th reading of the replay (0-based), the temperature rises
 * linearly from {@code baseC} to {@code targetC} over {@code rampReadings} readings, stays at
 * {@code targetC} for {@code holdReadings} readings, then the dataset values resume.
 * If {@code baseC} is NaN the ramp starts from the dataset's own value at the start reading, so the
 * series has no jump. Other columns are unchanged. The result is deterministic.
 */
public record OverheatScenario(
        int startReading,
        int rampReadings,
        int holdReadings,
        double baseC,
        double targetC) {

    public OverheatScenario {
        if (startReading < 0 || rampReadings < 1 || holdReadings < 0) {
            throw new IllegalArgumentException(
                    "need startReading >= 0, rampReadings >= 1, holdReadings >= 0");
        }
    }

    /** True while the scenario overrides the dataset at this replay position. */
    public boolean isActive(int readingIndex) {
        int step = readingIndex - startReading;
        return step >= 0 && step < rampReadings + holdReadings;
    }

    /**
     * Temperature (°C) at the given replay position.
     *
     * @param datasetStartC dataset value at the start reading, used when baseC is NaN
     */
    public double temperatureC(int readingIndex, double datasetStartC) {
        int step = readingIndex - startReading;
        double from = Double.isNaN(baseC) ? datasetStartC : baseC;
        double fraction = Math.min(step + 1, rampReadings) / (double) rampReadings;
        return from + (targetC - from) * fraction;
    }

    public MotorReading apply(MotorReading reading, int readingIndex, double datasetStartC) {
        if (!isActive(readingIndex)) {
            return reading;
        }
        return reading.withStatorWindingC(temperatureC(readingIndex, datasetStartC));
    }
}
