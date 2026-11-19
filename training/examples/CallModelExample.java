import ml.motor.*;
import java.nio.file.*;
import java.util.*;

/** Run from training: ./scripts/run-java.sh examples/CallModelExample.java */
public class CallModelExample {
    public static void main(String[] args) throws Exception {
        Path bundle = Path.of("results/rolling_full_v1");
        List<ForecastTelemetry> rows;
        try (var lines = Files.lines(Path.of("examples/profile79-history.jsonl"))) {
            rows = lines.filter(s -> !s.isBlank()).map(ForecastTelemetry::parse).toList();
        }
        List<MotorReading> history61 = rows.stream().map(ForecastTelemetry::reading).toList();
        ForecastWindows.validateHistory(history61);
        for (int i = 1; i < rows.size(); i++) {
            if (!rows.get(i).machineId().equals(rows.get(0).machineId())
                    || rows.get(i).timestampMs() - rows.get(i - 1).timestampMs() != 1000) {
                throw new IllegalArgumentException("One machine and consecutive 1 Hz required");
            }
        }
        // In a service, keep this registry open for the application's lifetime.
        try (var registry = ForecastModelRegistry.load(bundle)) {
            if (!registry.errors().isEmpty()) {
                throw new IllegalStateException(registry.errors().toString());
            }
            for (int h : ForecastWindows.HORIZONS) {
                double futureC = registry.models().get(h).predict(history61);
                if (!Double.isFinite(futureC)) throw new IllegalStateException("Invalid output");
                System.out.printf(Locale.ROOT, "+%ds = %.6f C%n", h, futureC);
            }
        }
    }
}
