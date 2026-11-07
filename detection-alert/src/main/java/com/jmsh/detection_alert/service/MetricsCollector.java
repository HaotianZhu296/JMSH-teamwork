package com.jmsh.detection_alert.service;

// TODO: temporarily added, not fully tested — metrics collection and evaluation pending
import com.jmsh.detection_alert.config.PerformanceTargets;
import com.jmsh.detection_alert.domain.AlertStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class MetricsCollector {

    private final PerformanceTargets targets;

    @Getter
    private final AtomicLong totalReadings = new AtomicLong();
    @Getter
    private final AtomicLong truePositives = new AtomicLong();
    @Getter
    private final AtomicLong falsePositives = new AtomicLong();
    @Getter
    private final AtomicLong trueNegatives = new AtomicLong();
    @Getter
    private final AtomicLong falseNegatives = new AtomicLong();

    public void record(AlertStatus predicted, AlertStatus actual) {
        totalReadings.incrementAndGet();
        boolean predictedAlert = predicted != AlertStatus.SAFE;
        boolean actualAlert = actual != AlertStatus.SAFE;

        if (predictedAlert && actualAlert) {
            truePositives.incrementAndGet();
        } else if (predictedAlert) {
            falsePositives.incrementAndGet();
        } else if (actualAlert) {
            falseNegatives.incrementAndGet();
        } else {
            trueNegatives.incrementAndGet();
        }
    }

    public double getPrecision() {
        long tp = truePositives.get();
        long fp = falsePositives.get();
        return (tp + fp) == 0 ? 0.0 : (double) tp / (tp + fp);
    }

    public double getRecall() {
        long tp = truePositives.get();
        long fn = falseNegatives.get();
        return (tp + fn) == 0 ? 0.0 : (double) tp / (tp + fn);
    }

    public double getF1Score() {
        double p = getPrecision();
        double r = getRecall();
        return (p + r) == 0 ? 0.0 : 2 * p * r / (p + r);
    }

    public boolean meetsPrecisionTarget() {
        return getPrecision() >= targets.getPrecision();
    }

    public boolean meetsRecallTarget() {
        return getRecall() >= targets.getRecall();
    }

    public boolean meetsF1Target() {
        return getF1Score() >= targets.getF1Score();
    }

    public void reset() {
        totalReadings.set(0);
        truePositives.set(0);
        falsePositives.set(0);
        trueNegatives.set(0);
        falseNegatives.set(0);
    }
}
