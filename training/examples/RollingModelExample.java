import ml.motor.*;
import java.nio.file.*;
import com.google.gson.GsonBuilder;

/** Offline replay of one record at a time; no MQTT broker or retraining required. */
public class RollingModelExample {
    public static void main(String[] args) throws Exception {
        try (var registry = ForecastModelRegistry.load(Path.of("results/rolling_full_v1"))) {
            if (!registry.errors().isEmpty()) {
                throw new IllegalStateException(registry.errors().toString());
            }
            // warning C, critical C, max rise C/s, future warning C, anomaly threshold
            var config = new DetectionConfig(80, 95, 0.5, 80, 0.6);
            // null: anomaly detection is handled separately in this example.
            var service = new RollingForecastService(registry.models(), null, config, 128, 250);
            var json = new GsonBuilder().serializeNulls().create();
            try (var lines = Files.lines(Path.of("examples/profile79-history.jsonl"))) {
                for (String line : (Iterable<String>) lines.filter(s -> !s.isBlank())::iterator) {
                    // In production, call this once for each new sensor message.
                    ForecastTelemetry input = ForecastTelemetry.parse(line);
                    ForecastEnvelope result = service.onReading(input);
                    System.out.println(json.toJson(result));
                }
            }
        }
    }
}
