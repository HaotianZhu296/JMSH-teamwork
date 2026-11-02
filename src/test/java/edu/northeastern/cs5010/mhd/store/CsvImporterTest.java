package edu.northeastern.cs5010.mhd.store;

import edu.northeastern.cs5010.mhd.model.SensorReading;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvImporterTest {

    private EventStore store;
    private Path dbFile;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("csv-import-test-", ".db");
        store = new EventStore(dbFile.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void importReadingsWithSimpleHeaders() throws Exception {
        Path csv = Files.createTempFile("readings-", ".csv");
        Files.writeString(csv, """
                air_temp_k,process_temp_k,rotational_speed_rpm,torque_nm,tool_wear_min
                298.1,308.6,1551,42.8,0
                299.2,309.1,1408,46.3,3
                300.0,310.7,1498,49.4,5
                """);
        CsvImporter importer = new CsvImporter(store);
        int count = importer.importReadings(csv, "test_machine");
        assertEquals(3, count);
        assertEquals(3, store.getReadingCount());

        List<SensorReading> readings = store.getRecentReadings("test_machine", 10);
        assertEquals(3, readings.size());
        Files.deleteIfExists(csv);
    }

    @Test
    void importReadingsWithAi4iHeaders() throws Exception {
        Path csv = Files.createTempFile("ai4i-", ".csv");
        Files.writeString(csv, """
                Air temperature [K],Process temperature [K],Rotational speed [rpm],Torque [Nm],Tool wear [min]
                298.1,308.6,1551,42.8,0
                299.2,309.1,1408,46.3,3
                """);
        CsvImporter importer = new CsvImporter(store);
        int count = importer.importReadings(csv, "m1");
        assertEquals(2, count);

        List<SensorReading> readings = store.getRecentReadings("m1", 10);
        assertEquals(298.1, readings.get(1).getAirTempK(), 0.01);
        assertEquals(308.6, readings.get(1).getProcessTempK(), 0.01);
        Files.deleteIfExists(csv);
    }

    @Test
    void importReadingsWithBom() throws Exception {
        Path csv = Files.createTempFile("bom-", ".csv");
        Files.writeString(csv, "﻿air_temp_k,process_temp_k,rotational_speed_rpm,torque_nm,tool_wear_min\n"
                + "298.1,308.6,1551,42.8,0\n");
        CsvImporter importer = new CsvImporter(store);
        int count = importer.importReadings(csv, "bom_machine");
        assertEquals(1, count);
        Files.deleteIfExists(csv);
    }

    @Test
    void importAlerts() throws Exception {
        Path csv = Files.createTempFile("alerts-", ".csv");
        Files.writeString(csv, """
                timestamp,machine_id,severity,reason,detector_name
                1000,m1,CRITICAL,temp > 313K,ThresholdDetector
                2000,m2,WARNING,EWMA spike,EwmaDetector
                """);
        CsvImporter importer = new CsvImporter(store);
        int count = importer.importAlerts(csv);
        assertEquals(2, count);
        assertEquals(2, store.getAlertCount());
        Files.deleteIfExists(csv);
    }

    @Test
    void importReadingsWithTimestampAndMachineId() throws Exception {
        Path csv = Files.createTempFile("full-", ".csv");
        Files.writeString(csv, """
                timestamp,machine_id,air_temp_k,process_temp_k,rotational_speed_rpm,torque_nm,tool_wear_min
                5000,mx,298.1,308.6,1551,42.8,0
                6000,mx,299.2,309.1,1408,46.3,3
                """);
        CsvImporter importer = new CsvImporter(store);
        int count = importer.importReadings(csv, "default");
        assertEquals(2, count);

        List<SensorReading> readings = store.getRecentReadings("mx", 10);
        assertEquals(2, readings.size());
        assertEquals(6000L, readings.get(0).getTimestamp());
        Files.deleteIfExists(csv);
    }
}
