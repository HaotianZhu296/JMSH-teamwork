package ml.motor;

import java.util.List;

public record DetectionResult(
        String machineId,
        int profileId,
        long sampleIndex,
        double currentTemperatureC,
        Double predictedTemperatureC,
        double anomalyScore,
        boolean statisticalAnomaly,
        boolean predictiveWarning,
        String status,
        int historyCount,
        List<String> reasons
) {

    public DetectionResult {
        reasons = List.copyOf(reasons);
    }
}
