package edu.northeastern.cs5010.mhd.model;

public class AnomalyScore {

    private final long timestamp;
    private final String machineId;
    private final double score;
    private final String modelName;

    public AnomalyScore(long timestamp, String machineId,
                        double score, String modelName) {
        this.timestamp = timestamp;
        this.machineId = machineId;
        this.score = score;
        this.modelName = modelName;
    }

    public long getTimestamp() { return timestamp; }
    public String getMachineId() { return machineId; }
    public double getScore() { return score; }
    public String getModelName() { return modelName; }

    @Override
    public String toString() {
        return String.format("Score[machine=%s, score=%.4f, model=%s]",
                machineId, score, modelName);
    }
}
