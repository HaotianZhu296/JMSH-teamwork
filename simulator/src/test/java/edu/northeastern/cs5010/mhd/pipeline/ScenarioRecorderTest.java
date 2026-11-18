package edu.northeastern.cs5010.mhd.pipeline;

import edu.northeastern.cs5010.mhd.model.MotorReading;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioRecorderTest {

    /** Ten readings of one profile at 40 °C; sample indices 0, 2, 4, ... */
    private static List<MotorReading> readings() {
        List<MotorReading> out = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            out.add(new MotorReading(57, i * 2, 40.0, 25.0, 22.0, 1500.0, 10.0, 1.0, 2.0, 3.0, 4.0));
        }
        return out;
    }

    private static SensorSimulator.Settings settings(OverheatScenario scenario) {
        return new SensorSimulator.Settings("tcp://x", Path.of("x"), 0, 2, List.of(57), 0,
                "motor-01", "sensors/{machine_id}/telemetry", 0, scenario);
    }

    /** Value of a number field inside the object that follows the given key. */
    private static String field(String json, String objectKey, String field) {
        Matcher object = Pattern.compile("\"" + objectKey + "\": (\\{[^}]*\\})").matcher(json);
        assertTrue(object.find(), objectKey + " missing in " + json);
        Matcher value = Pattern.compile("\"" + field + "\":([-0-9.]+)").matcher(object.group(1));
        assertTrue(value.find(), field + " missing in " + object.group(1));
        return value.group(1);
    }

    @Test
    void recordsWindowPeakAndFirstThresholdCrossings() throws Exception {
        // Ramp 40 -> 100 over 3 readings starting at reading 2 (60, 80, 100), hold 2 more at 100.
        OverheatScenario scenario = new OverheatScenario(2, 3, 2, 40, 100);
        ScenarioRecorder recorder = new ScenarioRecorder(scenario, "motor-01", 80, 95);
        long[] clock = {1000};
        SensorSimulator.run(readings(), settings(scenario), (t, p) -> { }, () -> clock[0]++, recorder);

        assertEquals(5, recorder.observedCount());
        String json = recorder.toJson();
        assertEquals("2", field(json, "firstInjected", "readingIndex"));
        assertEquals("4", field(json, "firstInjected", "sampleIndex"));
        assertEquals("1002", field(json, "firstInjected", "timestampMs"));
        assertEquals("6", field(json, "lastInjected", "readingIndex"));
        // 80 °C is reached at the 2nd injected reading, 95 °C at the 3rd.
        assertEquals("3", field(json, "firstAtOrAboveWarning", "readingIndex"));
        assertEquals("1003", field(json, "firstAtOrAboveWarning", "timestampMs"));
        assertEquals("4", field(json, "firstAtOrAboveCritical", "readingIndex"));
        assertEquals("100.0000", field(json, "peak", "processTemperature"));
        assertTrue(json.contains("\"injectedReadings\": 5"));
        assertTrue(json.contains("\"profileId\":57"));
    }

    @Test
    void ignoresReadingsOutsideTheWindowAndReportsNullWhenNothingHappened() throws Exception {
        // Scenario starts after the last reading, so nothing is injected.
        OverheatScenario scenario = new OverheatScenario(50, 3, 2, 40, 100);
        ScenarioRecorder recorder = new ScenarioRecorder(scenario, "motor-01", 80, 95);
        SensorSimulator.run(readings(), settings(scenario), (t, p) -> { }, () -> 0L, recorder);

        assertEquals(0, recorder.observedCount());
        String json = recorder.toJson();
        assertTrue(json.contains("\"firstInjected\": null"));
        assertTrue(json.contains("\"firstAtOrAboveCritical\": null"));
    }

    @Test
    void lowTargetNeverReachesTheThresholds() throws Exception {
        OverheatScenario scenario = new OverheatScenario(0, 2, 0, 40, 60);
        ScenarioRecorder recorder = new ScenarioRecorder(scenario, "motor-01", 80, 95);
        SensorSimulator.run(readings(), settings(scenario), (t, p) -> { }, () -> 0L, recorder);

        String json = recorder.toJson();
        assertTrue(json.contains("\"firstAtOrAboveWarning\": null"));
        assertEquals("60.0000", field(json, "peak", "processTemperature"));
    }

    @Test
    void nanBaseIsWrittenAsNull() {
        ScenarioRecorder recorder = new ScenarioRecorder(
                new OverheatScenario(0, 1, 0, Double.NaN, 100), "motor-01", 80, 95);
        assertTrue(recorder.toJson().contains("\"baseC\":null"));
    }

    @Test
    void writesTheFileAndCreatesMissingFolders(@TempDir Path tmp) throws Exception {
        OverheatScenario scenario = new OverheatScenario(0, 1, 0, 40, 100);
        ScenarioRecorder recorder = new ScenarioRecorder(scenario, "motor-01", 80, 95);
        SensorSimulator.run(readings(), settings(scenario), (t, p) -> { }, () -> 7L, recorder);

        Path file = tmp.resolve("out").resolve("scenario.json");
        recorder.writeTo(file);
        assertEquals(recorder.toJson(), Files.readString(file));
    }

    @Test
    void settingsReadLogFileAndThresholds() {
        Properties p = new Properties();
        assertNull(SensorSimulator.Settings.logFile(p));
        p.setProperty("scenario.log_file", " out/scenario.json ");
        assertEquals(Path.of("out/scenario.json"), SensorSimulator.Settings.logFile(p));
    }
}
