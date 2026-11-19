package com.jmsh.detection_alert.dto;

import com.jmsh.detection_alert.domain.AlertStatus;

import java.util.List;

public record ThresholdEvaluation(
        AlertStatus status, List<String> reasons
) {
}
