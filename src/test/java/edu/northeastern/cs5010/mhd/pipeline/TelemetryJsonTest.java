package edu.northeastern.cs5010.mhd.pipeline;

import edu.northeastern.cs5010.mhd.model.MotorReading;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryJsonTest {

    @Test
    void formatProducesValidJson() {
        MotorReading r = new MotorReading(57, 0,
                85.2, 32.1, 23.5, 1450.0, 42.3, -0.5, 3.2, -50.1, 180.5);
        String json = TelemetryJson.format(r, 1700000000000L, "motor-01");

        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"machineId\":\"motor-01\""));
        assertTrue(json.contains("\"timestampMs\":1700000000000"));
        assertTrue(json.contains("\"sensorType\":\"electric_motor\""));
    }

    @Test
    void fieldMappingIsCorrect() {
        MotorReading r = new MotorReading(57, 10,
                85.2, 32.1, 23.5, 1450.0, 42.3, -0.5, 3.2, -50.1, 180.5);
        String json = TelemetryJson.format(r, 1000L, "m1");

        assertTrue(json.contains("\"airTemperature\":23.5000"));
        assertTrue(json.contains("\"processTemperature\":85.2000"));
        assertTrue(json.contains("\"rotationalSpeed\":1450.0000"));
        assertTrue(json.contains("\"torque\":42.3000"));
        assertTrue(json.contains("\"toolWear\":0.0"));
        assertTrue(json.contains("\"coolantC\":32.1000"));
    }
}
