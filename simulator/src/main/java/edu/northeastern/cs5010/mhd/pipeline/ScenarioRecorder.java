package edu.northeastern.cs5010.mhd.pipeline;

import edu.northeastern.cs5010.mhd.model.MotorReading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Records where a scripted overheating scenario actually happened during a run, so that an
 * evaluation can tell injected events from real ones and knows when an alert is expected.
 *
 * <p>Only readings changed by the scenario are observed. Dataset values outside the injected
 * window may exceed the thresholds too; those are not in this log. The reference thresholds are
 * used only to mark when the injected temperature first reaches them. They must match the
 * detection service's {@code processTemperature} thresholds, they do not configure it.
 */
public final class ScenarioRecorder {

    /** One observed reading. */
    record Mark(int readingIndex, int profileId, int sampleIndex, long timestampMs, double temperatureC) {
        String json() {
            return String.format(Locale.ROOT,
                    "{\"readingIndex\":%d,\"profileId\":%d,\"sampleIndex\":%d,"
                            + "\"timestampMs\":%d,\"processTemperature\":%.4f}",
                    readingIndex, profileId, sampleIndex, timestampMs, temperatureC);
        }
    }

    private final OverheatScenario scenario;
    private final String machineId;
    private final double warningC;
    private final double criticalC;

    private Mark first;
    private Mark last;
    private Mark peak;
    private Mark firstWarning;
    private Mark firstCritical;
    private int observed;

    public ScenarioRecorder(OverheatScenario scenario, String machineId, double warningC, double criticalC) {
        this.scenario = scenario;
        this.machineId = machineId;
        this.warningC = warningC;
        this.criticalC = criticalC;
    }

    /**
     * Call once per published reading, after the scenario has been applied.
     *
     * @param readingIndex position of the reading in the replay (0-based)
     * @param reading      the reading as published
     * @param timestampMs  the publish timestamp put in the message
     */
    public void observe(int readingIndex, MotorReading reading, long timestampMs) {
        if (!scenario.isActive(readingIndex)) {
            return;
        }
        Mark mark = new Mark(readingIndex, reading.profileId(), reading.sampleIndex(),
                timestampMs, reading.statorWindingC());
        observed++;
        if (first == null) {
            first = mark;
        }
        last = mark;
        if (peak == null || mark.temperatureC() > peak.temperatureC()) {
            peak = mark;
        }
        if (firstWarning == null && mark.temperatureC() >= warningC) {
            firstWarning = mark;
        }
        if (firstCritical == null && mark.temperatureC() >= criticalC) {
            firstCritical = mark;
        }
    }

    /** Number of injected readings seen so far. */
    public int observedCount() {
        return observed;
    }

    public String toJson() {
        return String.format(Locale.ROOT,
                "{\n"
                        + "  \"machineId\": \"%s\",\n"
                        + "  \"scenario\": {\"startReading\":%d,\"rampReadings\":%d,\"holdReadings\":%d,"
                        + "\"baseC\":%s,\"targetC\":%.4f},\n"
                        + "  \"referenceThresholdsC\": {\"warning\":%.4f,\"critical\":%.4f},\n"
                        + "  \"injectedReadings\": %d,\n"
                        + "  \"firstInjected\": %s,\n"
                        + "  \"lastInjected\": %s,\n"
                        + "  \"peak\": %s,\n"
                        + "  \"firstAtOrAboveWarning\": %s,\n"
                        + "  \"firstAtOrAboveCritical\": %s\n"
                        + "}\n",
                machineId,
                scenario.startReading(), scenario.rampReadings(), scenario.holdReadings(),
                Double.isNaN(scenario.baseC()) ? "null" : String.format(Locale.ROOT, "%.4f", scenario.baseC()),
                scenario.targetC(),
                warningC, criticalC,
                observed,
                jsonOrNull(first), jsonOrNull(last), jsonOrNull(peak),
                jsonOrNull(firstWarning), jsonOrNull(firstCritical));
    }

    public void writeTo(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, toJson(), StandardCharsets.UTF_8);
    }

    private static String jsonOrNull(Mark mark) {
        return mark == null ? "null" : mark.json();
    }
}
