package edu.northeastern.cs5010.mhd.pipeline;

import edu.northeastern.cs5010.mhd.model.MotorReading;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelemetryCsvTest {

    private static final MotorReading R =
            new MotorReading(17, 120, 65.5, 25.0, 22.0, 2000.0, 35.0, 1.2, 2.5, 100.0, 150.0);

    @Test
    void formatsColumnsInHeaderOrderWithoutHeaderLine() {
        assertEquals("1700000000000,motor-01,17,120,65.5000,25.0000,22.0000,2000.0000,35.0000,"
                + "1.2000,2.5000,100.0000,150.0000", TelemetryCsv.format(R, 1700000000000L, "motor-01"));
    }

    @Test
    void roundTripsThroughParse() {
        List<String> fields = TelemetryCsv.parse(TelemetryCsv.format(R, 5L, "motor-01"));
        assertEquals(TelemetryCsv.HEADER.size(), fields.size());
        assertEquals("sample_index", TelemetryCsv.HEADER.get(3));
        assertEquals("120", fields.get(3));
    }

    @Test
    void formatIsLocaleIndependent() {
        java.util.Locale old = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            assertEquals("5,motor-01,17,120,65.5000", TelemetryCsv.format(R, 5L, "motor-01").substring(0, 25));
        } finally {
            java.util.Locale.setDefault(old);
        }
    }

    @Test
    void wrongColumnCountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TelemetryCsv.parse("1,2,3"));
    }
}
