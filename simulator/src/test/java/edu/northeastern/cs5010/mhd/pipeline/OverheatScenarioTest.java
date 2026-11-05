package edu.northeastern.cs5010.mhd.pipeline;

import edu.northeastern.cs5010.mhd.model.MotorReading;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverheatScenarioTest {

    private static final MotorReading ROW =
            new MotorReading(3, 0, 40.0, 25.0, 22.0, 1500.0, 10.0, 1.0, 2.0, 3.0, 4.0);

    @Test
    void rampsThenHoldsThenStops() {
        OverheatScenario s = new OverheatScenario(2, 4, 2, 40, 80);
        assertFalse(s.isActive(1));
        assertEquals(50, s.temperatureC(2, 0), 1e-9);
        assertEquals(60, s.temperatureC(3, 0), 1e-9);
        assertEquals(80, s.temperatureC(5, 0), 1e-9);
        assertEquals(80, s.temperatureC(7, 0), 1e-9);
        assertTrue(s.isActive(7));
        assertFalse(s.isActive(8));
    }

    @Test
    void nanBaseStartsFromDatasetValueWithoutJump() {
        OverheatScenario s = new OverheatScenario(0, 2, 0, Double.NaN, 100);
        assertEquals(70, s.temperatureC(0, 40), 1e-9);
        assertEquals(100, s.temperatureC(1, 40), 1e-9);
    }

    @Test
    void changesOnlyStatorWinding() {
        OverheatScenario s = new OverheatScenario(0, 1, 1, 40, 120);
        MotorReading hot = s.apply(ROW, 0, 40);
        assertEquals(120, hot.statorWindingC());
        assertEquals(ROW.withStatorWindingC(120), hot);
        assertEquals(ROW, s.apply(ROW, 5, 40));
    }

    @Test
    void rejectsBadParameters() {
        assertThrows(IllegalArgumentException.class, () -> new OverheatScenario(0, 0, 0, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new OverheatScenario(-1, 1, 0, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new OverheatScenario(0, 1, -1, 1, 2));
    }

    @Test
    void settingsLoadScenarioOnlyWhenEnabled() {
        assertNull(SensorSimulator.Settings.loadScenario(new Properties()));
        Properties p = new Properties();
        p.setProperty("scenario.enabled", "true");
        p.setProperty("scenario.target_c", "130");
        p.setProperty("scenario.base_c", "");
        OverheatScenario s = SensorSimulator.Settings.loadScenario(p);
        assertEquals(130, s.targetC());
        assertTrue(Double.isNaN(s.baseC()));
        assertEquals(100, s.startReading());
    }
}
