package com.jmsh.detection_alert.service;

import com.jmsh.detection_alert.domain.AlertStatus;
import com.jmsh.detection_alert.dto.EvaluationReturn;
import com.jmsh.detection_alert.dto.SensorReading;
import com.jmsh.detection_alert.dto.ThresholdEvaluation;
import com.jmsh.detection_alert.entity.Evaluation;
import com.jmsh.detection_alert.repository.EvaluationRepository;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class DetectionService {
    @Value("${thresholds.airTemperature.warning}")
    private double warningAirTemperature;
    @Value("${thresholds.airTemperature.critical}")
    private double criticalAirTemperature;
    @Value("${thresholds.processTemperature.warning}")
    private double warningProcessTemperature;
    @Value("${thresholds.processTemperature.critical}")
    private double criticalProcessTemperature;
    @Value("${thresholds.rotationalSpeed.warning}")
    private double warningRotationalSpeed;
    @Value("${thresholds.rotationalSpeed.critical}")
    private double criticalRotationalSpeed;
    @Value("${thresholds.torque.warning}")
    private double warningTorque;
    @Value("${thresholds.torque.critical}")
    private double criticalTorque;
    @Value("${thresholds.toolWear.warning}")
    private double warningToolWear;
    @Value("${thresholds.toolWear.critical}")
    private double criticalToolWear;
    @Value("${thresholds.warningScore}")
    private double warningScore;
    @Value("${thresholds.criticalScore}")
    private double criticalScore;

    private final EvaluationRepository evaluationRepository;

    /**
     * Processes a sensor reading by evaluating it against thresholds and a predictive model, saving the evaluation to the database, and returning an EvaluationReturn object with the results.
     * @param sensorReading the sensor reading to be processed
     * @return EvaluationReturn containing the machine ID, sensor type, model score, rule-based status, overall alert status, and reasons for the alert status.
     */
    public EvaluationReturn processSensorReading(SensorReading sensorReading) {
        ThresholdEvaluation thresholdEvaluation = evaluateThresholds(sensorReading);
        double modelScore = calculateModelScore(sensorReading);
        AlertStatus modelStatus = statusForScore(modelScore);
        if(modelStatus != AlertStatus.SAFE) {
            thresholdEvaluation.reasons().add("Predictive model indicates a potential issue");
        }
        AlertStatus alertStatus = highestSeverity(thresholdEvaluation.status(), modelStatus);

        Evaluation evaluation = Evaluation.builder()
                .machineId(sensorReading.machineId())
                .sensorType(sensorReading.sensorType())
                .airTemperature(sensorReading.airTemperature())
                .processTemperature(sensorReading.processTemperature())
                .rotationalSpeed(sensorReading.rotationalSpeed())
                .torque(sensorReading.torque())
                .toolWear(sensorReading.toolWear())
                .alertStatus(alertStatus)
                .reasons(thresholdEvaluation.reasons())
                .build();
        evaluationRepository.save(evaluation);

        return new EvaluationReturn(
                sensorReading.machineId(),
                sensorReading.sensorType(),
                modelScore,
                thresholdEvaluation.status(),
                alertStatus,
                thresholdEvaluation.reasons()
        );
    }

    /**
     * Evaluates the sensor reading against predefined thresholds for each parameter and determines the alert status and reasons for any alerts.
     * @param reading the sensor reading to be evaluated
     * @return ThresholdEvaluation containing the overall alert status and a list of reasons for any alerts.
     */
    private ThresholdEvaluation evaluateThresholds(SensorReading reading) {
        AlertStatus status = AlertStatus.SAFE;
        List<String> reasons = new ArrayList<>();

        if (reading.airTemperature() >= criticalAirTemperature) {
            status = highestSeverity(status, AlertStatus.CRITICAL);
            reasons.add("Air temperature exceeded the critical threshold");
        } else if (reading.airTemperature() >= warningAirTemperature) {
            status = highestSeverity(status, AlertStatus.WARNING);
            reasons.add("Air temperature exceeded the warning threshold");
        }

        if (reading.processTemperature() >= criticalProcessTemperature) {
            status = highestSeverity(status, AlertStatus.CRITICAL);
            reasons.add("Process temperature exceeded the critical threshold");
        } else if (reading.processTemperature() >= warningProcessTemperature) {
            status = highestSeverity(status, AlertStatus.WARNING);
            reasons.add("Process temperature exceeded the warning threshold");
        }

        if (reading.rotationalSpeed() >= criticalRotationalSpeed) {
            status = highestSeverity(status, AlertStatus.CRITICAL);
            reasons.add("Rotational speed exceeded the critical threshold");
        } else if (reading.rotationalSpeed() >= warningRotationalSpeed) {
            status = highestSeverity(status, AlertStatus.WARNING);
            reasons.add("Rotational speed exceeded the warning threshold");
        }

        if (reading.torque() >= criticalTorque) {
            status = highestSeverity(status, AlertStatus.CRITICAL);
            reasons.add("Torque exceeded the critical threshold");
        } else if (reading.torque() >= warningTorque) {
            status = highestSeverity(status, AlertStatus.WARNING);
            reasons.add("Torque exceeded the warning threshold");
        }

        if (reading.toolWear() >= criticalToolWear) {
            status = highestSeverity(status, AlertStatus.CRITICAL);
            reasons.add("Tool wear exceeded the critical threshold");
        } else if (reading.toolWear() >= warningToolWear) {
            status = highestSeverity(status, AlertStatus.WARNING);
            reasons.add("Tool wear exceeded the warning threshold");
        }

        return new ThresholdEvaluation(status, reasons);
    }

    /**
     * Temporary deterministic stand-in for the call to the predictive model.
     * The result is a risk score in the range [0, 1].
     */
    private double calculateModelScore(SensorReading reading) {
        double score = (reading.processTemperature() / 100
                + reading.rotationalSpeed() / 3000
                + reading.torque() / 80
                + reading.toolWear() / 200) / 4;

        return Math.max(0, Math.min(1, score));
    }

    private AlertStatus statusForScore(double score) {
        if (score >= criticalScore) {
            return AlertStatus.CRITICAL;
        }
        if (score >= warningScore) {
            return AlertStatus.WARNING;
        }
        return AlertStatus.SAFE;
    }

    private AlertStatus highestSeverity(AlertStatus first, AlertStatus second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

}
