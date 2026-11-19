package ml.motor;

public class CurrentThermalDetector {

    private final double warningTemperature;
    private final double criticalTemperature;
    private final double maxRiseRate;

    public CurrentThermalDetector(
            double warningTemperature,
            double criticalTemperature,
            double maxRiseRate) {

        this.warningTemperature = warningTemperature;
        this.criticalTemperature = criticalTemperature;
        this.maxRiseRate = maxRiseRate;
    }

    public String detect(
            MotorReading previous,
            MotorReading current) {

        double temperature = current.statorWinding();

        if (temperature >= criticalTemperature) {
            return "CRITICAL";
        }

        if (temperature >= warningTemperature) {
            return "WARNING";
        }

        if (previous != null &&
                previous.profileId() == current.profileId()) {

            double timeDifference =
                    (current.sampleIndex()
                            - previous.sampleIndex()) / 2.0;

            if (timeDifference > 0) {

                double riseRate =
                        (temperature - previous.statorWinding())
                                / timeDifference;

                if (riseRate > maxRiseRate) {
                    return "WARNING";
                }
            }
        }

        return "SAFE";
    }
}
