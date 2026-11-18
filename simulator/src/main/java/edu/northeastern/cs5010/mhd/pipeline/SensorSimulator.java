package edu.northeastern.cs5010.mhd.pipeline;

import edu.northeastern.cs5010.mhd.model.MotorReading;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Replays the Electric Motor Temperature dataset as live MQTT telemetry: one machine, the configured
 * profiles in order, one reading every {@code sim.interval} ms (default 1 s = 1 Hz).
 *
 * <p>Usage: {@code mvn exec:java -Dexec.mainClass=edu.northeastern.cs5010.mhd.pipeline.SensorSimulator}
 * (optional argument: path to a properties file; default {@code config/simulator.properties}).
 *
 * <p>Credentials come from the environment, never from files in Git: {@code MQTT_USERNAME},
 * {@code MQTT_PASSWORD}. For {@code ssl://} brokers, point the JVM at the trust store with
 * {@code -Djavax.net.ssl.trustStore=... -Djavax.net.ssl.trustStorePassword=...}.
 */
public final class SensorSimulator {

    private static final Logger LOG = Logger.getLogger(SensorSimulator.class.getName());
    static final String DEFAULT_CONFIG = "config/simulator.properties";
    private static final String CLIENT_ID = "sensor-simulator";

    private SensorSimulator() {
    }

    /** Settings keys follow README.md "Pipeline configuration". */
    public record Settings(
            String brokerUri,
            Path dataFile,
            int limit,
            int stride,
            List<Integer> profiles,
            long intervalMs,
            String machineId,
            String topicTemplate,
            long reconnectMaxWaitMs,
            OverheatScenario scenario,
            Path scenarioLogFile,
            double scenarioWarningC,
            double scenarioCriticalC) {

        /** Settings without a scenario log. */
        public Settings(String brokerUri, Path dataFile, int limit, int stride, List<Integer> profiles,
                        long intervalMs, String machineId, String topicTemplate,
                        long reconnectMaxWaitMs, OverheatScenario scenario) {
            this(brokerUri, dataFile, limit, stride, profiles, intervalMs, machineId, topicTemplate,
                    reconnectMaxWaitMs, scenario, null, 80.0, 95.0);
        }

        public static Settings load(Path file) throws IOException {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
            return new Settings(
                    p.getProperty("mqtt.broker", "tcp://localhost:1883"),
                    Path.of(p.getProperty("data.file", "data/measures_v2.csv")),
                    Integer.parseInt(p.getProperty("data.limit", "0")),
                    Integer.parseInt(p.getProperty("data.stride", "2")),
                    parseProfiles(p.getProperty("sim.profiles", "")),
                    Long.parseLong(p.getProperty("sim.interval", "1000")),
                    p.getProperty("sim.machine_id", "motor-01"),
                    p.getProperty("sim.topic_template", "sensors/{machine_id}/telemetry"),
                    Long.parseLong(p.getProperty("sim.reconnect_max_wait_ms", "60000")),
                    loadScenario(p),
                    logFile(p),
                    Double.parseDouble(p.getProperty("scenario.warning_c", "80").trim()),
                    Double.parseDouble(p.getProperty("scenario.critical_c", "95").trim()));
        }

        /** Returns null unless {@code scenario.log_file} is set. */
        static Path logFile(Properties p) {
            String file = p.getProperty("scenario.log_file", "").trim();
            return file.isEmpty() ? null : Path.of(file);
        }

        static List<Integer> parseProfiles(String text) {
            List<Integer> out = new ArrayList<>();
            for (String part : text.split(",")) {
                if (!part.isBlank()) {
                    out.add(Integer.parseInt(part.trim()));
                }
            }
            return out;
        }

        /** Returns null unless {@code scenario.enabled=true}. */
        static OverheatScenario loadScenario(Properties p) {
            if (!Boolean.parseBoolean(p.getProperty("scenario.enabled", "false").trim())) {
                return null;
            }
            String base = p.getProperty("scenario.base_c", "").trim();
            return new OverheatScenario(
                    Integer.parseInt(p.getProperty("scenario.start", "100").trim()),
                    Integer.parseInt(p.getProperty("scenario.ramp", "120").trim()),
                    Integer.parseInt(p.getProperty("scenario.hold", "60").trim()),
                    base.isEmpty() ? Double.NaN : Double.parseDouble(base),
                    Double.parseDouble(p.getProperty("scenario.target_c", "125").trim()));
        }
    }

    /** Sends one message. Implemented by the MQTT client in {@link #main}; a fake in tests. */
    @FunctionalInterface
    public interface Publisher {
        void publish(String topic, String payload) throws MqttException, InterruptedException;
    }

    /**
     * Publishes every reading in order, applying the scripted scenario if one is configured.
     * Returns the number of messages sent.
     */
    public static int run(List<MotorReading> readings, Settings settings,
                          Publisher publisher, LongSupplier clockMs)
            throws MqttException, InterruptedException {
        return run(readings, settings, publisher, clockMs, null);
    }

    /** Same as above; if {@code recorder} is not null it sees every reading changed by the scenario. */
    public static int run(List<MotorReading> readings, Settings settings,
                          Publisher publisher, LongSupplier clockMs, ScenarioRecorder recorder)
            throws MqttException, InterruptedException {
        OverheatScenario scenario = settings.scenario();
        double datasetStartC = Double.NaN;
        if (scenario != null && scenario.startReading() < readings.size()) {
            datasetStartC = readings.get(scenario.startReading()).statorWindingC();
        }
        String topic = settings.topicTemplate().replace("{machine_id}", settings.machineId());
        int sent = 0;
        for (int i = 0; i < readings.size(); i++) {
            MotorReading reading = readings.get(i);
            if (scenario != null) {
                reading = scenario.apply(reading, i, datasetStartC);
            }
            long timestampMs = clockMs.getAsLong();
            publisher.publish(topic, TelemetryJson.format(reading, timestampMs, settings.machineId()));
            if (recorder != null) {
                recorder.observe(i, reading, timestampMs);
            }
            sent++;
            if (settings.intervalMs() > 0) {
                Thread.sleep(settings.intervalMs());
            }
        }
        return sent;
    }

    public static void main(String[] args) throws Exception {
        Settings settings = Settings.load(Path.of(args.length > 0 ? args[0] : DEFAULT_CONFIG));
        List<MotorReading> readings = MotorDataReader.read(
                settings.dataFile(), settings.profiles(), settings.stride(), settings.limit());
        LOG.info("loaded " + readings.size() + " readings from " + settings.dataFile());

        MqttClient client = new MqttClient(settings.brokerUri(), CLIENT_ID, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        String user = System.getenv("MQTT_USERNAME");
        String password = System.getenv("MQTT_PASSWORD");
        if (user != null && !user.isEmpty()) {
            options.setUserName(user);
            options.setPassword(password == null ? new char[0] : password.toCharArray());
        }
        client.connect(options);
        try {
            Publisher direct = (topic, payload) -> {
                MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
                message.setQos(1);
                client.publish(topic, message);
            };
            Publisher publisher = new RetryingPublisher(direct, settings.reconnectMaxWaitMs(), Thread::sleep);
            ScenarioRecorder recorder = null;
            if (settings.scenario() != null && settings.scenarioLogFile() != null) {
                recorder = new ScenarioRecorder(settings.scenario(), settings.machineId(),
                        settings.scenarioWarningC(), settings.scenarioCriticalC());
            }
            try {
                int sent = run(readings, settings, publisher, System::currentTimeMillis, recorder);
                LOG.info("published " + sent + " messages to " + settings.brokerUri());
            } finally {
                if (recorder != null) {
                    recorder.writeTo(settings.scenarioLogFile());
                    LOG.info("scenario log written to " + settings.scenarioLogFile());
                }
            }
        } finally {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
    }
}
