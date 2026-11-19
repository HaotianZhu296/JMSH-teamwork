package edu.northeastern.cs5010.mhd.store;

import edu.northeastern.cs5010.mhd.model.AlertRecord;
import edu.northeastern.cs5010.mhd.model.AnomalyScore;
import edu.northeastern.cs5010.mhd.model.SensorReading;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventStoreTest {

    private EventStore store;
    private Path dbFile;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("eventstore-test-", ".db");
        store = new EventStore(dbFile.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void emptyStoreHasZeroCounts() throws SQLException {
        assertEquals(0, store.getReadingCount());
        assertEquals(0, store.getAlertCount());
    }

    @Test
    void insertAndCountReadings() throws SQLException {
        SensorReading r = new SensorReading(
                1000L, "m1", 298.1, 308.6, 1551, 42.8, 0);
        store.insertReading(r);
        store.insertReading(r);
        assertEquals(2, store.getReadingCount());
    }

    @Test
    void insertAndCountAlerts() throws SQLException {
        AlertRecord a = new AlertRecord(
                2000L, "m1", AlertRecord.Severity.CRITICAL,
                "temp > 313K", "ThresholdDetector");
        store.insertAlert(a);
        assertEquals(1, store.getAlertCount());
        assertEquals(0, store.getReadingCount());
    }

    @Test
    void batchInsert() throws SQLException {
        List<SensorReading> batch = List.of(
                new SensorReading(1000L, "m1", 298.0, 308.0, 1500, 40, 0),
                new SensorReading(2000L, "m1", 298.5, 309.0, 1520, 41, 1),
                new SensorReading(3000L, "m2", 299.0, 310.0, 1540, 42, 2));
        store.insertReadingsBatch(batch);
        assertEquals(3, store.getReadingCount());
    }

    @Test
    void getRecentReadingsFiltersByMachine() throws SQLException {
        store.insertReading(new SensorReading(1000L, "m1", 298, 308, 1500, 40, 0));
        store.insertReading(new SensorReading(2000L, "m2", 299, 309, 1520, 41, 1));
        store.insertReading(new SensorReading(3000L, "m1", 300, 310, 1540, 42, 2));

        List<SensorReading> m1 = store.getRecentReadings("m1", 10);
        assertEquals(2, m1.size());
        assertEquals(3000L, m1.get(0).getTimestamp());
    }

    @Test
    void getRecentAlertsOrdersByTimestampDesc() throws SQLException {
        store.insertAlert(new AlertRecord(
                1000L, "m1", AlertRecord.Severity.WARNING, "rate of rise", "Threshold"));
        store.insertAlert(new AlertRecord(
                3000L, "m1", AlertRecord.Severity.CRITICAL, "temp > 313K", "Threshold"));
        store.insertAlert(new AlertRecord(
                2000L, "m2", AlertRecord.Severity.WARNING, "EWMA spike", "EWMA"));

        List<AlertRecord> alerts = store.getRecentAlerts(10);
        assertEquals(3, alerts.size());
        assertEquals(3000L, alerts.get(0).getTimestamp());
        assertEquals(2000L, alerts.get(1).getTimestamp());
    }

    @Test
    void getMachineIdsReturnsSorted() throws SQLException {
        store.insertReading(new SensorReading(1000L, "m3", 298, 308, 1500, 40, 0));
        store.insertReading(new SensorReading(2000L, "m1", 299, 309, 1520, 41, 1));
        store.insertReading(new SensorReading(3000L, "m2", 300, 310, 1540, 42, 2));

        List<String> ids = store.getMachineIds();
        assertEquals(List.of("m1", "m2", "m3"), ids);
    }

    @Test
    void insertAndCountScores() throws SQLException {
        AnomalyScore s = new AnomalyScore(1000L, "m1", 0.85, "IsolationForest");
        store.insertScore(s);
        store.insertScore(new AnomalyScore(2000L, "m1", 0.92, "IsolationForest"));
        assertEquals(2, store.getScoreCount());
    }

    @Test
    void getRecentScoresFiltersByMachine() throws SQLException {
        store.insertScore(new AnomalyScore(1000L, "m1", 0.5, "IF"));
        store.insertScore(new AnomalyScore(2000L, "m2", 0.7, "IF"));
        store.insertScore(new AnomalyScore(3000L, "m1", 0.9, "IF"));

        List<AnomalyScore> m1 = store.getRecentScores("m1", 10);
        assertEquals(2, m1.size());
        assertEquals(3000L, m1.get(0).getTimestamp());
        assertEquals(0.9, m1.get(0).getScore(), 0.01);
    }

    @Test
    void batchInsertScores() throws SQLException {
        List<AnomalyScore> batch = List.of(
                new AnomalyScore(1000L, "m1", 0.1, "IF"),
                new AnomalyScore(2000L, "m1", 0.5, "IF"),
                new AnomalyScore(3000L, "m2", 0.9, "IF"));
        store.insertScoresBatch(batch);
        assertEquals(3, store.getScoreCount());
    }

    @Test
    void recentReadingsLimitWorks() throws SQLException {
        for (int i = 0; i < 20; i++) {
            store.insertReading(new SensorReading(
                    i * 1000L, "m1", 298, 308, 1500, 40, i));
        }
        List<SensorReading> limited = store.getRecentReadings("m1", 5);
        assertEquals(5, limited.size());
    }
}
