package com.jmsh.detection_alert.dto;

public record SensorReading(
        String machineId,
        String sensorType,
        double airTemperature,
        double processTemperature,
        double rotationalSpeed,
        double torque,
        double toolWear
) {
}
