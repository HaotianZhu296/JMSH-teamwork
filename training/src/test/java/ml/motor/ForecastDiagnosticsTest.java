package ml.motor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastDiagnosticsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistenceDiagnosticsPreserveTrainingComparison() throws Exception {
        Path csv = temporaryDirectory.resolve("readings.csv");
        try (var writer = Files.newBufferedWriter(csv)) {
            writer.write("profile_id,u_q,coolant,stator_winding,u_d,stator_tooth,motor_speed,i_d,i_q,pm,stator_yoke,ambient,torque\n");
            for (int profile = 1; profile <= 69; profile++) {
                for (int sample = 0; sample < 242; sample++) {
                    writer.write(profile + ",8,25," + (30 + sample * 0.01)
                            + ",7,30,1500,5,6,30,30,22,9\n");
                }
            }
        }
        Properties selection = new Properties();
        for (int horizon : ForecastWindows.HORIZONS) {
            selection.setProperty("h" + horizon + ".type", "Persistence");
            selection.setProperty("h" + horizon + ".path", ".");
        }
        try (var writer = Files.newBufferedWriter(temporaryDirectory.resolve("selection.properties"))) {
            selection.store(writer, "Diagnostic fixture");
        }
        Path trainingComparison = temporaryDirectory.resolve("profile_bootstrap.csv");
        String original = "saved full-training comparison\n";
        Files.writeString(trainingComparison, original);

        ForecastDiagnosticsMain.main(new String[]{csv.toString(), temporaryDirectory.toString()});

        assertEquals(original, Files.readString(trainingComparison));
        var diagnostics = Files.readAllLines(temporaryDirectory.resolve("persistence_bootstrap.csv"));
        assertEquals(6, diagnostics.size());
        assertTrue(diagnostics.subList(1, diagnostics.size()).stream()
                .allMatch(row -> row.contains(",0.0,0.0,")));
        assertEquals(6, Files.readAllLines(temporaryDirectory.resolve("validation_regimes.csv")).size());
    }
}
