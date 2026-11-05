package edu.northeastern.cs5010.mhd.store;

import edu.northeastern.cs5010.mhd.model.AlertRecord;
import edu.northeastern.cs5010.mhd.model.AnomalyScore;
import edu.northeastern.cs5010.mhd.model.SensorReading;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CsvImporter {

    private final EventStore store;

    public CsvImporter(EventStore store) {
        this.store = store;
    }

    public int importReadings(Path csvFile, String defaultMachineId) throws IOException, SQLException {
        List<SensorReading> readings = parseReadings(csvFile, defaultMachineId);
        store.insertReadingsBatch(readings);
        return readings.size();
    }

    public int importAlerts(Path csvFile) throws IOException, SQLException {
        int count = 0;
        try (Reader reader = bomSkippingReader(csvFile);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord row : parser) {
                AlertRecord alert = new AlertRecord(
                        Long.parseLong(row.get("timestamp")),
                        row.get("machine_id"),
                        AlertRecord.Severity.valueOf(row.get("severity").toUpperCase()),
                        row.get("reason"),
                        row.get("detector_name"));
                store.insertAlert(alert);
                count++;
            }
        }
        return count;
    }

    public int importScores(Path csvFile) throws IOException, SQLException {
        int count = 0;
        try (Reader reader = bomSkippingReader(csvFile);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord row : parser) {
                AnomalyScore score = new AnomalyScore(
                        Long.parseLong(row.get("timestamp")),
                        row.get("machine_id"),
                        Double.parseDouble(row.get("score")),
                        row.get("model_name"));
                store.insertScore(score);
                count++;
            }
        }
        return count;
    }

    List<SensorReading> parseReadings(Path csvFile, String defaultMachineId)
            throws IOException {
        List<SensorReading> readings = new ArrayList<>();
        long baseTimestamp = System.currentTimeMillis();

        try (Reader reader = bomSkippingReader(csvFile);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            int index = 0;
            for (CSVRecord row : parser) {
                String machineId = hasColumn(row, "machine_id")
                        ? row.get("machine_id") : defaultMachineId;
                long timestamp = hasColumn(row, "timestamp")
                        ? Long.parseLong(row.get("timestamp"))
                        : baseTimestamp + index * 1000L;

                double airTemp = getDouble(row, "Air temperature [K]", "air_temp_k");
                double procTemp = getDouble(row, "Process temperature [K]", "process_temp_k");
                double rpm = getDouble(row, "Rotational speed [rpm]", "rotational_speed_rpm");
                double torque = getDouble(row, "Torque [Nm]", "torque_nm");
                double toolWear = getDouble(row, "Tool wear [min]", "tool_wear_min");

                readings.add(new SensorReading(
                        timestamp, machineId, airTemp, procTemp, rpm, torque, toolWear));
                index++;
            }
        }
        return readings;
    }

    private double getDouble(CSVRecord row, String... names) {
        for (String name : names) {
            if (hasColumn(row, name)) {
                return Double.parseDouble(row.get(name));
            }
        }
        throw new IllegalArgumentException(
                "None of the column names found: " + String.join(", ", names));
    }

    private boolean hasColumn(CSVRecord row, String name) {
        try {
            row.get(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static Reader bomSkippingReader(Path path) throws IOException {
        BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        br.mark(1);
        int first = br.read();
        if (first != '﻿') {
            br.reset();
        }
        return br;
    }
}
