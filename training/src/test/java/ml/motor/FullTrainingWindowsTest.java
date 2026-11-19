package ml.motor;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FullTrainingWindowsTest {
    private static List<MotorReading> raw(int profile, int count) {
        List<MotorReading> result = new ArrayList<>();
        for (int i = 0; i < count; i++)
            result.add(ForecastPipelineTest.reading(profile, i, 20 + i * .1));
        return result;
    }

    @Test void bothPhasesUseAllEligibleOriginsAtOneHz() {
        var refs = ForecastWindows.allTrainingPhases(Map.of(7, raw(7, 250)), 42);
        assertEquals(10, refs.size()); // 125 rows/phase minus 60 history and 60 future = 5 each.
        Set<Long> origins = new TreeSet<>();
        for (var ref : refs) {
            assertTrue(origins.add(ref.now().sampleIndex()));
            ForecastWindows.validateHistory(ref.history());
            for (int horizon : ForecastWindows.HORIZONS) {
                assertEquals(ref.now().sampleIndex() + 2L * horizon,
                        ref.profile().get(ref.index() + horizon).sampleIndex());
                assertEquals(.2 * horizon, ref.delta(horizon), 1e-8);
            }
        }
        assertEquals(new TreeSet<>(List.of(120L,121L,122L,123L,124L,125L,126L,127L,128L,129L)), origins);
    }

    @Test void evenPhaseMatchesLegacyDownsamplingAndIsReproducible() {
        var raw = raw(9, 300);
        List<MotorReading> legacy = new ArrayList<>();
        for (int i = 0; i < raw.size(); i += 2) legacy.add(raw.get(i));
        assertEquals(ForecastWindows.sample(Map.of(9, legacy), 5, 4, 43),
                ForecastWindows.sample(ForecastWindows.oneHzPhase(Map.of(9, raw), 0), 5, 4, 43));
        assertEquals(ForecastWindows.allTrainingPhases(Map.of(9, raw), 42),
                ForecastWindows.allTrainingPhases(Map.of(9, raw), 42));
    }

    @Test void profilesNeverMixAndRawGapsAreNotCompressed() {
        Map<Integer,List<MotorReading>> profiles = new LinkedHashMap<>();
        profiles.put(1, raw(1, 242)); profiles.put(2, raw(2, 242));
        var refs = ForecastWindows.allTrainingPhases(profiles, 42);
        assertEquals(4, refs.size());
        refs.forEach(r -> r.history().forEach(p -> assertEquals(r.now().profileId(), p.profileId())));
        profiles.get(1).removeIf(r -> r.sampleIndex() == 100); // Even phase interrupted; odd phase still valid.
        refs = ForecastWindows.allTrainingPhases(profiles, 42);
        assertEquals(3, refs.size());
        assertTrue(refs.stream().noneMatch(r -> r.now().profileId() == 1 && r.now().sampleIndex() % 2 == 0));
    }

    @Test void invalidPhaseOrWrongProfileIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ForecastWindows.oneHzPhase(Map.of(1, raw(1, 242)), 2));
        assertThrows(IllegalArgumentException.class, () -> ForecastWindows.oneHzPhase(Map.of(2, raw(1, 242)), 0));
    }

    @Test void lastBoostingStageMatchesFullModelPrediction() {
        List<MotorReading> p = new ArrayList<>();
        for (int i=0;i<160;i++) p.add(ForecastPipelineTest.reading(1,i*2L,20+Math.sin(i/7.0)+.001*i*i));
        var refs=ForecastWindows.sample(Map.of(1,p),1,30,42);
        Properties options=new Properties();
        options.setProperty("smile.gradient_boost.trees","4");
        options.setProperty("smile.gradient_boost.max_depth","3");
        options.setProperty("smile.gradient_boost.max_nodes","4");
        options.setProperty("smile.gradient_boost.node_size","2");
        options.setProperty("model.name","GBDT_full_seed42");
        try(var model=TreeHorizonModel.train(refs,1,42,options)) {
            var stages=model.predictStages(refs);
            assertEquals(4,stages.length);
            assertEquals("GBDT_full_seed42",model.name());
            assertArrayEquals(model.predictAll(refs),stages[stages.length-1],1e-10);
        }
    }
}
