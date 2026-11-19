package ml.motor;

import java.util.*;

/** One index is one second after stride=2. sampleIndex remains the original 2 Hz index. */
public final class ForecastWindows {
    public static final int[] HORIZONS = {1, 5, 10, 30, 60};
    private ForecastWindows() {}

    public record Ref(List<MotorReading> profile, int index) {
        public MotorReading now() { return profile.get(index); }
        public List<MotorReading> history() { return profile.subList(index - 60, index + 1); }
        public double target(int horizon) {
            requireHorizon(horizon);
            return profile.get(index + horizon).statorWinding();
        }
        public double delta(int horizon) { return target(horizon) - now().statorWinding(); }
    }

    public static void requireHorizon(int h) {
        if (Arrays.stream(HORIZONS).noneMatch(x -> x == h)) throw new IllegalArgumentException("Unsupported horizon: " + h);
    }

    public static boolean finite(MotorReading r) {
        for (double v : r.anomalyFeatures()) if (!Double.isFinite(v)) return false;
        // Bound derived values to float range too: GRU stores float32 inputs.
        return Double.isFinite(r.uD()) && Double.isFinite(r.uQ())
                && Float.isFinite((float)(r.iD()*r.iD() + r.iQ()*r.iQ()))
                && Float.isFinite((float)(1.5*(r.uD()*r.iD() + r.uQ()*r.iQ())));
    }

    public static void validateHistory(List<MotorReading> history) {
        if (history.size() != 61) throw new IllegalArgumentException("Exactly 61 readings required");
        for (int i = 0; i < history.size(); i++) {
            if (!finite(history.get(i))) throw new IllegalArgumentException("Non-finite model input");
            if (i > 0 && !continuous(history.get(i-1), history.get(i)))
                throw new IllegalArgumentException("Discontinuous 1 Hz history");
        }
    }

    public static boolean continuous(MotorReading a, MotorReading b) {
        return a.profileId() == b.profileId() && b.sampleIndex() - a.sampleIndex() == 2;
    }

    /** Identical origins for every horizon; require ALL 121 rows and 120 edges to be valid. */
    public static List<Ref> sample(Map<Integer, List<MotorReading>> profiles, int step, int limit, long seed) {
        if (step < 1 || limit < 1) throw new IllegalArgumentException("Invalid sampling configuration");
        List<Ref> refs = enumerate(profiles, step);
        Collections.shuffle(refs, new Random(seed));
        return new ArrayList<>(refs.subList(0, Math.min(limit, refs.size())));
    }

    /** A causal 1 Hz sequence offset by either 0 or 0.5 seconds in the original 2 Hz data. */
    public static Map<Integer, List<MotorReading>> oneHzPhase(
            Map<Integer, List<MotorReading>> rawProfiles, int phase) {
        if (phase != 0 && phase != 1) throw new IllegalArgumentException("Phase must be 0 or 1");
        Map<Integer, List<MotorReading>> result = new LinkedHashMap<>();
        rawProfiles.forEach((id, readings) -> {
            List<MotorReading> selected = new ArrayList<>();
            for (MotorReading reading : readings) {
                if (reading.profileId() != id || reading.sampleIndex() < 0)
                    throw new IllegalArgumentException("Invalid profile ownership/index");
                if (reading.sampleIndex() % 2 == phase) selected.add(reading);
            }
            result.put(id, selected);
        });
        return result;
    }

    /** No window cap: all common valid origins in BOTH phases, each with a 61-row 1 Hz input. */
    public static List<Ref> allTrainingPhases(Map<Integer, List<MotorReading>> rawTrainingProfiles, long seed) {
        List<Ref> refs = enumerate(oneHzPhase(rawTrainingProfiles, 0), 1);
        refs.addAll(enumerate(oneHzPhase(rawTrainingProfiles, 1), 1));
        Collections.shuffle(refs, new Random(seed));
        return refs;
    }

    private static List<Ref> enumerate(Map<Integer, List<MotorReading>> profiles, int step) {
        List<Ref> refs = new ArrayList<>();
        for (var p : profiles.values()) {
            int[] invalidRows = new int[p.size()+1], invalidEdges = new int[p.size()+1];
            for (int i = 0; i < p.size(); i++) {
                invalidRows[i+1] = invalidRows[i] + (finite(p.get(i)) ? 0 : 1);
                invalidEdges[i+1] = invalidEdges[i] + (i > 0 && !continuous(p.get(i-1), p.get(i)) ? 1 : 0);
            }
            for (int i = 60; i + 60 < p.size(); i += step) {
                int start = i-60, end = i+60;
                if (invalidRows[end+1] == invalidRows[start] && invalidEdges[end+1] == invalidEdges[start+1])
                    refs.add(new Ref(p, i));
            }
        }
        return refs;
    }
}
