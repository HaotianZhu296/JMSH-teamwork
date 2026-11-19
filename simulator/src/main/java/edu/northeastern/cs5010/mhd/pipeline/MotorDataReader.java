package edu.northeastern.cs5010.mhd.pipeline;

import edu.northeastern.cs5010.mhd.model.MotorReading;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads the Electric Motor Temperature CSV ({@code measures_v2.csv}, 13 columns, 2 Hz).
 * Columns are found by header name, so their order in the file does not matter.
 * Malformed rows are logged and skipped, but still count towards the sample index.
 */
public final class MotorDataReader {

    private static final Logger LOG = Logger.getLogger(MotorDataReader.class.getName());

    static final List<String> REQUIRED_COLUMNS = List.of(
            "profile_id", "stator_winding", "coolant", "ambient", "motor_speed",
            "torque", "i_d", "i_q", "u_d", "u_q");

    private MotorDataReader() {
    }

    /**
     * @param profiles profiles to return, replayed in this order; empty = the first profile in the file
     * @param stride   keep every stride-th row of a profile (2 turns 2 Hz into 1 Hz)
     * @param limit    maximum number of readings in total; 0 = no limit
     */
    public static List<MotorReading> read(Path file, List<Integer> profiles, int stride, int limit)
            throws IOException {
        if (stride < 1) {
            throw new IllegalArgumentException("stride must be >= 1");
        }
        Map<Integer, List<MotorReading>> byProfile = new HashMap<>();
        Map<Integer, Integer> position = new HashMap<>();
        List<Integer> wanted = new ArrayList<>(profiles);
        boolean firstOnly = wanted.isEmpty();
        Integer firstSeen = null;

        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
        try (Reader reader = openSkippingBom(file);
             CSVParser parser = CSVParser.parse(reader, format)) {
            for (String column : REQUIRED_COLUMNS) {
                if (!parser.getHeaderMap().containsKey(column)) {
                    throw new IOException("column '" + column + "' missing in " + file);
                }
            }
            for (CSVRecord record : parser) {
                int profileId;
                try {
                    profileId = (int) Double.parseDouble(record.get("profile_id"));
                } catch (NumberFormatException e) {
                    LOG.warning("skipping record " + record.getRecordNumber() + ": bad profile_id");
                    continue;
                }
                if (firstOnly) {
                    if (firstSeen == null) {
                        firstSeen = profileId;
                        wanted.add(profileId);
                    } else if (profileId != firstSeen && byProfile.containsKey(firstSeen)) {
                        break; // first profile is complete
                    }
                }
                if (!wanted.contains(profileId)) {
                    continue;
                }
                int index = position.merge(profileId, 1, Integer::sum) - 1;
                if (index % stride != 0) {
                    continue;
                }
                try {
                    byProfile.computeIfAbsent(profileId, k -> new ArrayList<>())
                            .add(toReading(record, profileId, index));
                } catch (NumberFormatException e) {
                    LOG.warning("skipping record " + record.getRecordNumber() + ": " + e.getMessage());
                }
            }
        }

        List<MotorReading> out = new ArrayList<>();
        for (int profileId : wanted) {
            List<MotorReading> rows = byProfile.get(profileId);
            if (rows == null) {
                LOG.warning("profile " + profileId + " not found in " + file);
                continue;
            }
            for (MotorReading r : rows) {
                if (limit > 0 && out.size() >= limit) {
                    return out;
                }
                out.add(r);
            }
        }
        return out;
    }

    private static MotorReading toReading(CSVRecord r, int profileId, int sampleIndex) {
        return new MotorReading(profileId, sampleIndex,
                Double.parseDouble(r.get("stator_winding")),
                Double.parseDouble(r.get("coolant")),
                Double.parseDouble(r.get("ambient")),
                Double.parseDouble(r.get("motor_speed")),
                Double.parseDouble(r.get("torque")),
                Double.parseDouble(r.get("i_d")),
                Double.parseDouble(r.get("i_q")),
                Double.parseDouble(r.get("u_d")),
                Double.parseDouble(r.get("u_q")));
    }

    private static Reader openSkippingBom(Path file) throws IOException {
        BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
        reader.mark(1);
        if (reader.read() != '﻿') {
            reader.reset();
        }
        return reader;
    }
}
