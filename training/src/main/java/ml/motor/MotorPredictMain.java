package ml.motor;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Runs the published temperature models on one 61-reading JSONL history. */
public final class MotorPredictMain {
    private MotorPredictMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0 && args.length != 2) {
            throw new IllegalArgumentException("Usage: MotorPredictMain [model-directory history.jsonl]");
        }
        Path models = Path.of(args.length == 0 ? "results/rolling_full_v1" : args[0]);
        Path input = Path.of(args.length == 0 ? "examples/profile79-history.jsonl" : args[1]);
        List<ForecastTelemetry> telemetry = readHistory(input);
        List<MotorReading> history = telemetry.stream().map(ForecastTelemetry::reading).toList();
        ForecastTelemetry current = telemetry.get(60);

        try (var registry = ForecastModelRegistry.load(models)) {
            if (!registry.errors().isEmpty()) {
                throw new IllegalStateException("Model loading failed: " + registry.errors());
            }
            JsonObject result = new JsonObject();
            result.addProperty("machineId", current.machineId());
            result.addProperty("timestampMs", current.timestampMs());
            result.addProperty("currentTemperatureC", current.reading().statorWinding());
            JsonObject forecasts = new JsonObject();
            for (int horizon : ForecastWindows.HORIZONS) {
                JsonObject point = new JsonObject();
                point.addProperty("temperatureC", registry.models().get(horizon).predict(history));
                point.addProperty("targetTimestampMs", current.timestampMs() + horizon * 1000L);
                forecasts.add(Integer.toString(horizon), point);
            }
            result.add("forecasts", forecasts);
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(result));
        }
    }

    static List<ForecastTelemetry> readHistory(Path path) throws Exception {
        List<ForecastTelemetry> input;
        try (var lines = Files.lines(path)) {
            input = lines.filter(line -> !line.isBlank()).map(ForecastTelemetry::parse).toList();
        }
        ForecastWindows.validateHistory(input.stream().map(ForecastTelemetry::reading).toList());
        for (int i = 1; i < input.size(); i++) {
            if (!input.get(i).machineId().equals(input.get(0).machineId())
                    || input.get(i).timestampMs() - input.get(i - 1).timestampMs() != 1000) {
                throw new IllegalArgumentException("History must contain one machine at consecutive 1 Hz timestamps");
            }
        }
        return input;
    }
}
