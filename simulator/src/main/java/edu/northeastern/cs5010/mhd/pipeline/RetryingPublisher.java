package edu.northeastern.cs5010.mhd.pipeline;

import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.logging.Logger;

/**
 * Keeps retrying one message until the broker accepts it or {@code maxWaitMs} has passed.
 * Replay is sequential, so blocking here is the buffer: nothing is skipped and the order
 * (and the contiguous {@code sample_index}) is preserved across a broker outage.
 * QoS 1 can deliver a message twice after a reconnect; receivers can drop repeats by
 * (machine_id, profile_id, sample_index).
 */
public final class RetryingPublisher implements SensorSimulator.Publisher {

    /** Pauses the thread; a fake in tests. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    private static final Logger LOG = Logger.getLogger(RetryingPublisher.class.getName());
    private static final long FIRST_BACKOFF_MS = 500;
    private static final long MAX_BACKOFF_MS = 5000;

    private final SensorSimulator.Publisher delegate;
    private final long maxWaitMs;
    private final Sleeper sleeper;

    public RetryingPublisher(SensorSimulator.Publisher delegate, long maxWaitMs, Sleeper sleeper) {
        this.delegate = delegate;
        this.maxWaitMs = maxWaitMs;
        this.sleeper = sleeper;
    }

    @Override
    public void publish(String topic, String payload) throws MqttException, InterruptedException {
        long waited = 0;
        long backoff = FIRST_BACKOFF_MS;
        while (true) {
            try {
                delegate.publish(topic, payload);
                return;
            } catch (MqttException e) {
                if (waited >= maxWaitMs) {
                    throw e;
                }
                long pause = Math.min(backoff, maxWaitMs - waited);
                LOG.warning("publish failed (" + e.getMessage() + "), retrying in " + pause + " ms");
                sleeper.sleep(pause);
                waited += pause;
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            }
        }
    }
}
