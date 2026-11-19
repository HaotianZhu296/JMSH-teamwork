package com.jmsh.detection_alert.service;

import com.jmsh.detection_alert.domain.AlertStatus;
import com.jmsh.detection_alert.dto.EvaluationReturn;
import com.jmsh.detection_alert.dto.SensorReading;
import com.jmsh.detection_alert.entity.Evaluation;
import com.jmsh.detection_alert.repository.EvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DetectionServiceTest {

    @Mock
    private EvaluationRepository evaluationRepository;

    private DetectionService detectionService;

    @BeforeEach
    void setUp() {
        detectionService = new DetectionService(evaluationRepository);
        ReflectionTestUtils.setField(detectionService, "warningAirTemperature", 75.0);
        ReflectionTestUtils.setField(detectionService, "criticalAirTemperature", 90.0);
        ReflectionTestUtils.setField(detectionService, "warningProcessTemperature", 80.0);
        ReflectionTestUtils.setField(detectionService, "criticalProcessTemperature", 95.0);
        ReflectionTestUtils.setField(detectionService, "warningRotationalSpeed", 4000.0);
        ReflectionTestUtils.setField(detectionService, "criticalRotationalSpeed", 5500.0);
        ReflectionTestUtils.setField(detectionService, "warningTorque", 0.8);
        ReflectionTestUtils.setField(detectionService, "criticalTorque", 1.0);
        ReflectionTestUtils.setField(detectionService, "warningToolWear", 50.0);
        ReflectionTestUtils.setField(detectionService, "criticalToolWear", 75.0);
        ReflectionTestUtils.setField(detectionService, "warningScore", 0.65);
        ReflectionTestUtils.setField(detectionService, "criticalScore", 0.85);
    }

    @Test
    void processSensorReadingReturnsSafeEvaluationForNormalReading() {
        EvaluationReturn result = detectionService.processSensorReading(
                new SensorReading("machine-1", "baseline", 25.0, 40.0, 500.0, 0.3, 0.0)
        );

        assertEquals(AlertStatus.SAFE, result.ruleStatus());
        assertEquals(AlertStatus.SAFE, result.alertStatus());
        assertTrue(result.reasons().isEmpty());
        verifySavedEvaluation(AlertStatus.SAFE);
    }

    @Test
    void processSensorReadingChecksAllFieldsAndCriticalOverridesWarnings() {
        EvaluationReturn result = detectionService.processSensorReading(
                new SensorReading("machine-2", "mixed", 80.0, 96.0, 4500.0, 0.9, 80.0)
        );

        assertEquals(AlertStatus.CRITICAL, result.ruleStatus());
        assertEquals(AlertStatus.CRITICAL, result.alertStatus());
        assertEquals(6, result.reasons().size());
        assertTrue(result.reasons().contains("Air temperature exceeded the warning threshold"));
        assertTrue(result.reasons().contains("Process temperature exceeded the critical threshold"));
        assertTrue(result.reasons().contains("Rotational speed exceeded the warning threshold"));
        assertTrue(result.reasons().contains("Torque exceeded the warning threshold"));
        assertTrue(result.reasons().contains("Tool wear exceeded the critical threshold"));
        assertTrue(result.reasons().contains("Predictive model indicates a potential issue"));
        verifySavedEvaluation(AlertStatus.CRITICAL);
    }

    @Test
    void processSensorReadingAddsModelReasonWhenScoreReachesWarningThreshold() {
        EvaluationReturn result = detectionService.processSensorReading(
                new SensorReading("machine-3", "model-risk", 25.0, 90.0, 4500.0, 0.7, 0.0)
        );

        assertEquals(AlertStatus.WARNING, result.ruleStatus());
        assertEquals(AlertStatus.WARNING, result.alertStatus());
    }

    private void verifySavedEvaluation(AlertStatus expectedStatus) {
        ArgumentCaptor<Evaluation> captor = ArgumentCaptor.forClass(Evaluation.class);
        verify(evaluationRepository).save(captor.capture());
        assertEquals(expectedStatus, captor.getValue().getAlertStatus());
    }
}
