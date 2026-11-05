package ml.motor;

public record DetectionConfig(
        double warningTemperatureC,
        double criticalTemperatureC,
        double maxRiseRateCPerSecond,
        double futureWarningTemperatureC,
        double anomalyThreshold
) {

    public static DetectionConfig experimental(
            double anomalyThreshold) {

        return new DetectionConfig(
                100.0,            // Current warning
                120.0,            // Current critical
                0.5,              // Maximum rise rate
                98.0,             // Future warning
                anomalyThreshold  // Isolation Forest threshold
        );
    }
}

