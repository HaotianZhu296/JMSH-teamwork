package edu.northeastern.cs5010.mhd.pipeline;

import edu.northeastern.cs5010.mhd.model.MotorReading;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensorSimulatorTest {

    private static List<MotorReading> readings(int n) {
        List<MotorReading> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new MotorReading(3, i * 2, 40.0 + i, 25.0, 22.0, 1500.0, 10.0, 1.0, 2.0, 3.0, 4.0));
        }
        return out;
    }

    private static SensorSimulator.Settings settings(OverheatScenario scenario) {
        return new SensorSimulator.Settings("tcp://localhost:1883", Path.of("unused.csv"), 0, 2,
                List.of(3), 0, "motor-01", "sensors/{machine_id}/telemetry", 0, scenario);
    }

    @Test
    void publishesInOrderToMachineTopic() throws Exception {
        List<String> topics = new ArrayList<>();
        List<String> payloads = new ArrayList<>();
        int sent = SensorSimulator.run(readings(3), settings(null),
                (t, p) -> {
                    topics.add(t);
                    payloads.add(p);
                }, () -> 42L);

        assertEquals(3, sent);
        assertEquals(List.of("sensors/motor-01/telemetry", "sensors/motor-01/telemetry",
                "sensors/motor-01/telemetry"), topics);
        assertTrue(payloads.get(1).contains("\"machineId\":\"motor-01\""));
        assertTrue(payloads.get(1).contains("\"processTemperature\":41.0000"));
        assertTrue(payloads.get(1).contains("\"sampleIndex\":2"));
    }

    @Test
    void emptyInputSendsNothing() throws Exception {
        int sent = SensorSimulator.run(List.of(), settings(null), (t, p) -> {
            throw new AssertionError("should not publish");
        }, () -> 0L);
        assertEquals(0, sent);
    }

    @Test
    void scenarioRampsFromDatasetValueAtStart() throws Exception {
        // dataset stator_winding: 40,41,42,43,44; ramp starts at reading 1 (41) to 51 over 2, hold 1.
        List<String> payloads = new ArrayList<>();
        SensorSimulator.run(readings(5), settings(new OverheatScenario(1, 2, 1, Double.NaN, 51)),
                (t, p) -> payloads.add(p), () -> 0L);
        List<String> temps = payloads.stream().map(p -> extractJsonField(p, "processTemperature")).toList();
        assertEquals(List.of("40.0000", "46.0000", "51.0000", "51.0000", "44.0000"), temps);
    }

    @Test
    void settingsParseProfileList() {
        assertEquals(List.of(3, 17), SensorSimulator.Settings.parseProfiles(" 3, 17 ,"));
        assertEquals(List.of(), SensorSimulator.Settings.parseProfiles(""));
    }

    @Test
    void retryingPublisherRidesOutAnOutageWithoutSkippingOrReordering() throws Exception {
        List<String> delivered = new ArrayList<>();
        int[] failuresLeft = {3};
        SensorSimulator.Publisher flaky = (t, p) -> {
            if (failuresLeft[0] > 0) {
                failuresLeft[0]--;
                throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
            }
            delivered.add(p);
        };
        List<Long> pauses = new ArrayList<>();
        RetryingPublisher retrying = new RetryingPublisher(flaky, 60_000, pauses::add);

        SensorSimulator.run(readings(3), settings(null), retrying, () -> 0L);

        assertEquals(3, delivered.size());
        assertEquals("2", extractJsonField(delivered.get(1), "sampleIndex"));
        assertEquals(List.of(500L, 1000L, 2000L), pauses);
    }

    @Test
    void retryingPublisherGivesUpAfterMaxWait() {
        SensorSimulator.Publisher down = (t, p) -> {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        };
        List<Long> pauses = new ArrayList<>();
        RetryingPublisher retrying = new RetryingPublisher(down, 1_200, pauses::add);
        assertThrows(MqttException.class, () -> retrying.publish("t", "p"));
        assertEquals(1_200L, pauses.stream().mapToLong(Long::longValue).sum());
    }

    private static String extractJsonField(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\":(\"[^\"]*\"|[^,}]+)");
        Matcher m = p.matcher(json);
        if (!m.find()) throw new AssertionError("field not found: " + field);
        return m.group(1).replace("\"", "");
    }
}
