package com.jmsh.detection_alert.config;

// TODO: temporarily added, not fully tested — performance target validation pending
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "performance-targets")
@Getter
@Setter
public class PerformanceTargets {
    private double precision = 0.85;
    private double recall = 0.90;
    private double f1Score = 0.87;
    private long maxDetectionLatencyMs = 2000;
    private long maxEndToEndLatencyMs = 5000;
    private double falsePositiveRate = 0.10;
}
