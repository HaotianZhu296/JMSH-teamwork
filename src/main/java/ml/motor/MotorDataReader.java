package ml.motor;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MotorDataReader {

    public Map<Integer, List<MotorReading>> loadData(
            String filePath,
            int stride) throws IOException {

        if (stride < 1) {
            throw new IllegalArgumentException(
                    "Stride must be at least 1"
            );
        }

        Map<Integer, List<MotorReading>> profiles =
                new LinkedHashMap<>();

        Map<Integer, Long> sampleCounts = new HashMap<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .get();

        try (Reader reader = Files.newBufferedReader(
                Path.of(filePath),
                StandardCharsets.UTF_8);

             CSVParser parser = format.parse(reader)) {

            for (CSVRecord row : parser) {

                int profileId =
                        Integer.parseInt(row.get("profile_id"));

                long index =
                        sampleCounts.getOrDefault(profileId, 0L);

                sampleCounts.put(profileId, index + 1);

                // Keep one record for every 'stride' records
                if (index % stride != 0) {
                    continue;
                }

                MotorReading reading = new MotorReading(
                        profileId,
                        index,
                        Double.parseDouble(row.get("u_q")),
                        Double.parseDouble(row.get("coolant")),
                        Double.parseDouble(row.get("stator_winding")),
                        Double.parseDouble(row.get("u_d")),
                        Double.parseDouble(row.get("stator_tooth")),
                        Double.parseDouble(row.get("motor_speed")),
                        Double.parseDouble(row.get("i_d")),
                        Double.parseDouble(row.get("i_q")),
                        Double.parseDouble(row.get("pm")),
                        Double.parseDouble(row.get("stator_yoke")),
                        Double.parseDouble(row.get("ambient")),
                        Double.parseDouble(row.get("torque"))
                );

                profiles
                        .computeIfAbsent(
                                profileId,
                                id -> new ArrayList<>()
                        )
                        .add(reading);
            }
        }

        return profiles;
    }
}
