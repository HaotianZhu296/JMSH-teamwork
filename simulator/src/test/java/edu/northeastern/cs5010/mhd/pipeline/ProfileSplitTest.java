package edu.northeastern.cs5010.mhd.pipeline;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the shared train/validation/test split, which the whole team must use unchanged. */
class ProfileSplitTest {

    private static final Path SPLIT_FILE = Path.of("..", "config", "profile_split.csv");

    private static Map<Integer, String> load() throws IOException {
        List<String> lines = Files.readAllLines(SPLIT_FILE);
        assertEquals("profile_id,split", lines.get(0));
        Map<Integer, String> split = new TreeMap<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split(",");
            assertEquals(2, parts.length, "bad line: " + line);
            assertNull(split.put(Integer.parseInt(parts[0].trim()), parts[1].trim()),
                    "duplicate profile_id: " + parts[0]);
        }
        return split;
    }

    @Test
    void coversAll69ProfilesWithOnlyKnownSplits() throws IOException {
        Map<Integer, String> split = load();
        assertEquals(69, split.size());
        assertTrue(split.values().stream().allMatch(s ->
                s.equals("train") || s.equals("validation") || s.equals("test")));
    }

    @Test
    void hasTheAgreedSplitSizes() throws IOException {
        Map<String, Integer> counts = new HashMap<>();
        load().values().forEach(s -> counts.merge(s, 1, Integer::sum));
        assertEquals(48, counts.get("train"));
        assertEquals(10, counts.get("validation"));
        assertEquals(11, counts.get("test"));
    }

    @Test
    void defaultDemoProfileIsInTheValidationSet() throws IOException {
        // The demo must not use test profiles: they are reserved for the final evaluation.
        assertEquals("validation", load().get(57));
    }
}
