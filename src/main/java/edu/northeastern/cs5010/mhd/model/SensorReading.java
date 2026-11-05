package edu.northeastern.cs5010.mhd.model;

public class SensorReading {

    private final long timestamp;
    private final String machineId;
    private final double airTempK;
    private final double processTempK;
    private final double rotationalSpeedRpm;
    private final double torqueNm;
    private final double toolWearMin;

    public SensorReading(long timestamp, String machineId,
                         double airTempK, double processTempK,
                         double rotationalSpeedRpm, double torqueNm,
                         double toolWearMin) {
        this.timestamp = timestamp;
        this.machineId = machineId;
        this.airTempK = airTempK;
        this.processTempK = processTempK;
        this.rotationalSpeedRpm = rotationalSpeedRpm;
        this.torqueNm = torqueNm;
        this.toolWearMin = toolWearMin;
    }

    public long getTimestamp() { return timestamp; }
    public String getMachineId() { return machineId; }
    public double getAirTempK() { return airTempK; }
    public double getProcessTempK() { return processTempK; }
    public double getRotationalSpeedRpm() { return rotationalSpeedRpm; }
    public double getTorqueNm() { return torqueNm; }
    public double getToolWearMin() { return toolWearMin; }

    public double getTempDiffK() {
        return processTempK - airTempK;
    }

    @Override
    public String toString() {
        return String.format("Reading[machine=%s, procTemp=%.1fK, airTemp=%.1fK, rpm=%.0f]",
                machineId, processTempK, airTempK, rotationalSpeedRpm);
    }
}
