package edu.northeastern.cs5010.mhd.model;

public class AlertRecord {

    public enum Severity { WARNING, CRITICAL }

    private final long timestamp;
    private final String machineId;
    private final Severity severity;
    private final String reason;
    private final String detectorName;

    public AlertRecord(long timestamp, String machineId, Severity severity,
                       String reason, String detectorName) {
        this.timestamp = timestamp;
        this.machineId = machineId;
        this.severity = severity;
        this.reason = reason;
        this.detectorName = detectorName;
    }

    public long getTimestamp() { return timestamp; }
    public String getMachineId() { return machineId; }
    public Severity getSeverity() { return severity; }
    public String getReason() { return reason; }
    public String getDetectorName() { return detectorName; }

    @Override
    public String toString() {
        return String.format("Alert[%s | %s | %s | %s]",
                machineId, severity, detectorName, reason);
    }
}
