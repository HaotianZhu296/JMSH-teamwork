package ml.motor;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class PublishedModelsTest {
    private static final Path BUNDLE = Path.of("results/rolling_full_v1");

    @Test
    void publishedWeightsAndMetadataMatchChecksums() throws Exception {
        var lines = Files.readAllLines(BUNDLE.resolve("MODEL_SHA256SUMS"));
        assertEquals(11, lines.size());
        for (String line : lines) {
            String[] columns = line.split("  ", 2);
            Path file = BUNDLE.resolve(columns[1]).normalize();
            assertTrue(file.startsWith(BUNDLE));
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
            assertEquals(columns[0], actual, file.toString());
        }
    }

    @Test
    void publishedModelsReproduceOriginalBundlePredictions() throws Exception {
        var telemetry = MotorPredictMain.readHistory(Path.of("examples/profile79-history.jsonl"));
        var history = telemetry.stream().map(ForecastTelemetry::reading).toList();
        var expected = JsonParser.parseString(
                Files.readString(Path.of("examples/expected-forecast.json"))).getAsJsonObject();
        assertEquals(expected.get("currentTemperatureC").getAsDouble(), history.get(60).statorWinding());
        try (var registry = ForecastModelRegistry.load(BUNDLE)) {
            assertTrue(registry.errors().isEmpty(), registry.errors().toString());
            assertEquals(5, registry.models().size());
            for (int horizon : ForecastWindows.HORIZONS) {
                double prediction = registry.models().get(horizon).predict(history);
                double reference = expected.getAsJsonObject("forecasts")
                        .getAsJsonObject(Integer.toString(horizon)).get("temperatureC").getAsDouble();
                assertTrue(Double.isFinite(prediction));
                assertEquals(reference, prediction, 1e-9, "Horizon " + horizon);
            }
        }
    }
}
