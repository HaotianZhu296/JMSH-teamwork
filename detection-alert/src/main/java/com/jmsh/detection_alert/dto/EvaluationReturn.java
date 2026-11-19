package com.jmsh.detection_alert.dto;

import com.jmsh.detection_alert.domain.AlertStatus;

import java.util.List;

public record EvaluationReturn(
        String machineId,
        String sensorType,
        double modelScore,
        AlertStatus ruleStatus,
        AlertStatus alertStatus,
        List<String> reasons
) {
}
