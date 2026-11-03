package com.jmsh.detection_alert.entity;

import com.jmsh.detection_alert.domain.AlertStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "evaluations")
public class Evaluation extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id", nullable = false)
    private String machineId;

    @Column(name = "sensor_type", nullable = false)
    private String sensorType;

    @Column(name = "air_temperature", nullable = false)
    private double airTemperature;

    @Column(name = "process_temperature", nullable = false)
    private double processTemperature;

    @Column(name = "rotational_speed", nullable = false)
    private double rotationalSpeed;

    @Column(name = "torque", nullable = false)
    private double torque;

    @Column(name = "tool_wear", nullable = false)
    private double toolWear;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_status", nullable = false)
    private AlertStatus alertStatus;
}
