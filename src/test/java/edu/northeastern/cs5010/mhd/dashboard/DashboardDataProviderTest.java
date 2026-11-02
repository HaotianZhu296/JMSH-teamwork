package edu.northeastern.cs5010.mhd.dashboard;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import edu.northeastern.cs5010.mhd.model.AlertRecord;
import edu.northeastern.cs5010.mhd.model.AnomalyScore;
import edu.northeastern.cs5010.mhd.model.SensorReading;
import edu.northeastern.cs5010.mhd.store.EventStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DashboardDataProviderTest {

    private static final Gson GSON = new Gson();
    private EventStore store;
    private DashboardDataProvider provider;
    private Path dbFile;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("dashboard-test-", ".db");
        store = new EventStore(dbFile.toString());
        provider = new DashboardDataProvider(store);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void summaryJsonHasExpectedFields() throws Exception {
        store.insertReading(new SensorReading(1000L, "m1", 298, 308, 1500, 40, 0));
        store.insertAlert(new AlertRecord(
                2000L, "m1", AlertRecord.Severity.CRITICAL, "over temp", "Threshold"));

        String json = provider.getSummaryJson();
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        assertEquals(1, obj.get("readingCount").getAsInt());
        assertEquals(1, obj.get("alertCount").getAsInt());
        assertTrue(obj.get("machineIds").getAsJsonArray().size() > 0);
    }

    @Test
    void recentReadingsJsonReturnsCorrectData() throws Exception {
        store.insertReading(new SensorReading(1000L, "m1", 298.1, 308.6, 1551, 42.8, 0));

        String json = provider.getRecentReadingsJson("m1", 10);
        JsonArray arr = GSON.fromJson(json, JsonArray.class);
        assertEquals(1, arr.size());
        JsonObject reading = arr.get(0).getAsJsonObject();
        assertEquals(298.1, reading.get("airTempK").getAsDouble(), 0.01);
        assertEquals(10.5, reading.get("tempDiffK").getAsDouble(), 0.01);
    }

    @Test
    void recentScoresJsonReturnsCorrectData() throws Exception {
        store.insertScore(new AnomalyScore(
                4000L, "m1", 0.87, "IsolationForest"));

        String json = provider.getRecentScoresJson("m1", 10);
        JsonArray arr = GSON.fromJson(json, JsonArray.class);
        assertEquals(1, arr.size());
        JsonObject score = arr.get(0).getAsJsonObject();
        assertEquals("m1", score.get("machineId").getAsString());
        assertEquals(0.87, score.get("score").getAsDouble(), 0.01);
        assertEquals("IsolationForest", score.get("modelName").getAsString());
    }

    @Test
    void summaryJsonIncludesScoreCount() throws Exception {
        store.insertScore(new AnomalyScore(1000L, "m1", 0.5, "IF"));
        store.insertScore(new AnomalyScore(2000L, "m1", 0.8, "IF"));

        String json = provider.getSummaryJson();
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        assertEquals(2, obj.get("scoreCount").getAsInt());
    }

    @Test
    void recentAlertsJsonReturnsCorrectData() throws Exception {
        store.insertAlert(new AlertRecord(
                3000L, "m2", AlertRecord.Severity.WARNING, "EWMA spike", "EwmaDetector"));

        String json = provider.getRecentAlertsJson(10);
        JsonArray arr = GSON.fromJson(json, JsonArray.class);
        assertEquals(1, arr.size());
        JsonObject alert = arr.get(0).getAsJsonObject();
        assertEquals("m2", alert.get("machineId").getAsString());
        assertEquals("WARNING", alert.get("severity").getAsString());
        assertEquals("EwmaDetector", alert.get("detector").getAsString());
    }

    @Test
    void emptyStoreReturnsEmptyArrays() throws Exception {
        String summary = provider.getSummaryJson();
        JsonObject obj = GSON.fromJson(summary, JsonObject.class);
        assertEquals(0, obj.get("readingCount").getAsInt());

        String readings = provider.getRecentReadingsJson("m1", 10);
        assertEquals("[]", readings);

        String alerts = provider.getRecentAlertsJson(10);
        assertEquals("[]", alerts);
    }
}
