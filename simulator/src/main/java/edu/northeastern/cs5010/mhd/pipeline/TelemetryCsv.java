package edu.northeastern.cs5010.mhd.pipeline;

import edu.northeastern.cs5010.mhd.model.MotorReading;

import java.util.List;
import java.util.Locale;

/**
 * CSV telemetry format (spec: Shared/JMSH/sensor-message-spec-draft.md).
 * One message = one line, no header, columns in {@link #HEADER} order.
 * Raw measurements only: no derived features and no labels. Temperatures are in °C.
 */
public final class TelemetryCsv {

    public static final List<String> HEADER = List.of(
            "timestamp_ms",
            "machine_id",
            "profile_id",
            "sample_index",
            "stator_winding",
            "coolant",
            "ambient",
            "motor_speed",
            "torque",
            "i_d",
            "i_q",
            "u_d",
            "u_q");

    private TelemetryCsv() {
    }

    public static String format(MotorReading r, long timestampMs, String machineId) {
        return String.join(",",
                Long.toString(timestampMs),
                machineId,
                Integer.toString(r.profileId()),
                Integer.toString(r.sampleIndex()),
                num(r.statorWindingC()),
                num(r.coolantC()),
                num(r.ambientC()),
                num(r.motorSpeed()),
                num(r.torque()),
                num(r.iD()),
                num(r.iQ()),
                num(r.uD()),
                num(r.uQ()));
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    /** Split one telemetry line into its fields. Throws IllegalArgumentException on a wrong column count. */
    public static List<String> parse(String line) {
        String[] fields = line.split(",", -1);
        if (fields.length != HEADER.size()) {
            throw new IllegalArgumentException(
                    "expected " + HEADER.size() + " columns, got " + fields.length);
        }
        return List.of(fields);
    }
}
