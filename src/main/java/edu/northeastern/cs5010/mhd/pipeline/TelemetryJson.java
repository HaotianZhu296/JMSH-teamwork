package edu.northeastern.cs5010.mhd.pipeline;

import edu.northeastern.cs5010.mhd.model.MotorReading;

import java.util.Locale;

/**
 * Unified JSON telemetry format for cross-module MQTT communication.
 * Maps Electric Motor Temperature dataset fields to the shared schema
 * expected by the detection service and event store.
 */
public final class TelemetryJson {

    private TelemetryJson() {
    }

    public static String format(MotorReading r, long timestampMs, String machineId) {
        return String.format(Locale.ROOT,
                "{\"timestampMs\":%d,\"machineId\":\"%s\",\"sensorType\":\"electric_motor\","
                        + "\"airTemperature\":%.4f,\"processTemperature\":%.4f,"
                        + "\"rotationalSpeed\":%.4f,\"torque\":%.4f,\"toolWear\":0.0,"
                        + "\"profileId\":%d,\"sampleIndex\":%d,"
                        + "\"coolantC\":%.4f,\"iD\":%.4f,\"iQ\":%.4f,\"uD\":%.4f,\"uQ\":%.4f}",
                timestampMs, machineId,
                r.ambientC(), r.statorWindingC(),
                r.motorSpeed(), r.torque(),
                r.profileId(), r.sampleIndex(),
                r.coolantC(), r.iD(), r.iQ(), r.uD(), r.uQ());
    }
}
