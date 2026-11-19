package edu.northeastern.cs5010.mhd.pipeline;

import edu.northeastern.cs5010.mhd.model.MotorReading;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MotorDataReaderTest {

    // Same column order as measures_v2.csv.
    private static final String HEADER =
            "u_q,coolant,stator_winding,u_d,stator_tooth,motor_speed,i_d,i_q,pm,stator_yoke,ambient,torque,profile_id";

    @TempDir
    Path tmp;

    /** Row whose stator_winding encodes profile and index so tests can identify it. */
    private static String row(int profile, int i) {
        return "1.5,25.0," + (profile * 100 + i) + ".5,-2.0,30.0,1000.0,-1.0,2.0,40.0,30.0,20.0,3.0," + profile;
    }

    private Path csv(String... rows) throws IOException {
        Path file = tmp.resolve("m.csv");
        Files.writeString(file, HEADER + "\n" + String.join("\n", rows) + "\n");
        return file;
    }

    @Test
    void stride2KeepsEvenSampleIndexesPerProfile() throws Exception {
        Path file = csv(row(7, 0), row(7, 1), row(7, 2), row(7, 3), row(7, 4),
                row(9, 0), row(9, 1), row(9, 2));
        List<MotorReading> rows = MotorDataReader.read(file, List.of(7, 9), 2, 0);
        assertEquals(List.of(0, 2, 4, 0, 2), rows.stream().map(MotorReading::sampleIndex).toList());
        assertEquals(List.of(7, 7, 7, 9, 9), rows.stream().map(MotorReading::profileId).toList());
        assertEquals(704.5, rows.get(2).statorWindingC());
    }

    @Test
    void readsColumnsByNameAndReplaysProfilesInRequestedOrder() throws Exception {
        Path file = csv(row(7, 0), row(9, 0));
        List<MotorReading> rows = MotorDataReader.read(file, List.of(9, 7), 1, 0);
        assertEquals(List.of(9, 7), rows.stream().map(MotorReading::profileId).toList());
        MotorReading r = rows.get(0);
        assertEquals(1.5, r.uQ());
        assertEquals(-2.0, r.uD());
        assertEquals(25.0, r.coolantC());
        assertEquals(20.0, r.ambientC());
        assertEquals(1000.0, r.motorSpeed());
        assertEquals(3.0, r.torque());
        assertEquals(-1.0, r.iD());
        assertEquals(2.0, r.iQ());
    }

    @Test
    void emptyProfileListMeansFirstProfileInFile() throws Exception {
        Path file = csv(row(7, 0), row(7, 1), row(9, 0));
        List<MotorReading> rows = MotorDataReader.read(file, List.of(), 1, 0);
        assertEquals(2, rows.size());
        assertEquals(7, rows.get(1).profileId());
    }

    @Test
    void badRowIsSkippedButKeepsItsSampleIndex() throws Exception {
        String bad = "x,25.0,not-a-number,-2.0,30.0,1000.0,-1.0,2.0,40.0,30.0,20.0,3.0,7";
        Path file = csv(row(7, 0), bad, row(7, 2));
        List<MotorReading> rows = MotorDataReader.read(file, List.of(7), 1, 0);
        assertEquals(List.of(0, 2), rows.stream().map(MotorReading::sampleIndex).toList());
    }

    @Test
    void limitCapsTotalReadings() throws Exception {
        Path file = csv(row(7, 0), row(7, 1), row(7, 2), row(9, 0));
        assertEquals(3, MotorDataReader.read(file, List.of(7, 9), 1, 3).size());
    }

    @Test
    void missingColumnIsAnError() throws Exception {
        Path file = tmp.resolve("bad.csv");
        Files.writeString(file, "a,b\n1,2\n");
        assertThrows(IOException.class, () -> MotorDataReader.read(file, List.of(), 1, 0));
    }

    @Test
    void realDatasetMatchesDocumentedShape() throws Exception {
        Path file = Path.of("data/measures_v2.csv");
        assumeTrue(Files.exists(file), "measures_v2.csv not downloaded to data/");
        List<MotorReading> rows = MotorDataReader.read(file, List.of(17), 2, 0);
        assertEquals(0, rows.get(0).sampleIndex());
        assertEquals(2, rows.get(1).sampleIndex());
        assertEquals(17, rows.get(0).profileId());
    }
}
