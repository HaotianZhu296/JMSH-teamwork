
package ml.motor;

import smile.anomaly.IsolationForest;
import smile.regression.RandomForest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnifiedDetectionService {

    // 1 Hz: 60 seconds of history + current reading
    private static final int WINDOW_SIZE = 61;

    // Original data is 2 Hz, downsampled to 1 Hz.
    // Therefore original sampleIndex increases by 2.
    private static final long EXPECTED_INDEX_STEP = 2;

    private final RandomForest forecastModel;
    private final IsolationForest anomalyModel;
    private final DetectionConfig config;

    private final TemperatureForecastTrainer predictor;
    private final CurrentThermalDetector ruleDetector;

    private final Map<String, MachineState> machines =
            new HashMap<>();

    private static class MachineState {
        Deque<MotorReading> history = new ArrayDeque<>();
        MotorReading lastReading;
    }

    public UnifiedDetectionService(
            RandomForest forecastModel,
            IsolationForest anomalyModel,
            DetectionConfig config) {

        this.forecastModel = forecastModel;
        this.anomalyModel = anomalyModel;
        this.config = config;

        this.predictor = new TemperatureForecastTrainer();

        this.ruleDetector = new CurrentThermalDetector(
                config.warningTemperatureC(),
                config.criticalTemperatureC(),
                config.maxRiseRateCPerSecond()
        );
    }

    // Entry point for compact MQTT messages
    public DetectionResult onReading(
            MotorSensorMessage message) {

        return onReading(
                message.machineId(),
                message.toMotorReading()
        );
    }

    // Entry point for existing MotorReading objects
    public synchronized DetectionResult onReading(
            String machineId,
            MotorReading reading) {

        if (machineId == null || machineId.isBlank()) {
            throw new IllegalArgumentException(
                    "machineId must not be empty"
            );
        }

        // Validate the actual model input measurements
        for (double value : reading.anomalyFeatures()) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Invalid sensor measurement"
                );
            }
        }

        if (!Double.isFinite(reading.uD())
                || !Double.isFinite(reading.uQ())) {
            throw new IllegalArgumentException(
                    "Invalid voltage measurement"
            );
        }

        MachineState state = machines.computeIfAbsent(
                machineId,
                key -> new MachineState()
        );

        MotorReading previous = state.lastReading;

        boolean reset = false;

        // Detect profile changes and missing/out-of-order data
        if (previous != null) {

            boolean profileChanged =
                    previous.profileId() != reading.profileId();

            boolean sequenceBroken =
                    reading.sampleIndex()
                            != previous.sampleIndex()
                            + EXPECTED_INDEX_STEP;

            if (profileChanged || sequenceBroken) {

                state.history.clear();
                previous = null;
                reset = true;
            }
        }

        // Update history buffer
        state.history.addLast(reading);

        if (state.history.size() > WINDOW_SIZE) {
            state.history.removeFirst();
        }

        state.lastReading = reading;

        // ======================================
        // 1. Current rule-based detection
        // ======================================

        String ruleStatus =
                ruleDetector.detect(previous, reading);

        // ======================================
        // 2. Isolation Forest anomaly detection
        // ======================================

        double anomalyScore = anomalyModel.score(
                reading.anomalyFeatures()
        );

        boolean statisticalAnomaly =
                anomalyScore > config.anomalyThreshold();

        // ======================================
        // 3. Future temperature prediction
        // ======================================

        Double predictedTemperature = null;
        boolean predictiveWarning = false;

        if (state.history.size() == WINDOW_SIZE) {

            List<MotorReading> window =
                    new ArrayList<>(state.history);

            MotorReading now = window.get(60);
            MotorReading lag30 = window.get(30);
            MotorReading lag60 = window.get(0);

            // Must match ForecastDatasetBuilder
            double[] features = {
                    now.statorWinding(),
                    lag30.statorWinding(),
                    lag60.statorWinding(),

                    now.statorWinding()
                            - lag60.statorWinding(),

                    now.coolant(),
                    now.ambient(),
                    now.motorSpeed(),
                    now.torque(),
                    now.iD(),
                    now.iQ(),
                    now.uD(),
                    now.uQ()
            };

            predictedTemperature =
                    predictor.predictOne(
                            forecastModel,
                            features
                    );

            // Predictive warning applies before
            // current temperature reaches the limit.
            predictiveWarning =
                    reading.statorWinding()
                            < config.warningTemperatureC()
                            && predictedTemperature
                            >= config.futureWarningTemperatureC();
        }

        // ======================================
        // 4. Combine detection results
        // ======================================

        List<String> reasons = new ArrayList<>();

        if (reset) {
            reasons.add(
                    "History reset: profile changed "
                            + "or sample sequence interrupted"
            );
        }

        if ("CRITICAL".equals(ruleStatus)) {

            reasons.add(
                    "Current temperature reached "
                            + "critical threshold"
            );

        } else if (reading.statorWinding()
                >= config.warningTemperatureC()) {

            reasons.add(
                    "Current temperature reached "
                            + "warning threshold"
            );

        } else if ("WARNING".equals(ruleStatus)) {

            reasons.add(
                    "Temperature rise rate exceeded limit"
            );
        }

        if (predictiveWarning) {
            reasons.add(
                    "Predicted temperature exceeded "
                            + "future warning threshold"
            );
        }

        if (statisticalAnomaly) {
            reasons.add(
                    "Isolation Forest detected "
                            + "an unusual operating state"
            );
        }

        if (predictedTemperature == null) {
            reasons.add(
                    "Forecast warming up: "
                            + state.history.size()
                            + "/" + WINDOW_SIZE
                            + " readings available"
            );
        }

        String status = "SAFE";

        if (statisticalAnomaly) {
            status = "ANOMALY";
        }

        if (predictiveWarning
                || "WARNING".equals(ruleStatus)) {
            status = "WARNING";
        }

        if ("CRITICAL".equals(ruleStatus)) {
            status = "CRITICAL";
        }

        if (reasons.isEmpty()) {
            reasons.add("No abnormal condition detected");
        }

        return new DetectionResult(
                machineId,
                reading.profileId(),
                reading.sampleIndex(),
                reading.statorWinding(),
                predictedTemperature,
                anomalyScore,
                statisticalAnomaly,
                predictiveWarning,
                status,
                state.history.size(),
                reasons
        );
    }
}

