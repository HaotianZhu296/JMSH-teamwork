package ml.motor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ProfileSplitter {

    public record Split(
            Map<Integer, List<MotorReading>> train,
            Map<Integer, List<MotorReading>> validation,
            Map<Integer, List<MotorReading>> test
    ) {}

    public Split split(
            Map<Integer, List<MotorReading>> profiles,
            long seed) {

        List<Integer> ids =
                new ArrayList<>(profiles.keySet());

        Collections.shuffle(ids, new Random(seed));

        int n = ids.size();
        int trainCount = (int) Math.round(n * 0.70);
        int valCount = (int) Math.round(n * 0.15);

        Map<Integer, List<MotorReading>> train =
                new LinkedHashMap<>();

        Map<Integer, List<MotorReading>> validation =
                new LinkedHashMap<>();

        Map<Integer, List<MotorReading>> test =
                new LinkedHashMap<>();

        for (int i = 0; i < n; i++) {

            int profileId = ids.get(i);

            if (i < trainCount) {
                train.put(
                        profileId,
                        profiles.get(profileId)
                );

            } else if (i < trainCount + valCount) {
                validation.put(
                        profileId,
                        profiles.get(profileId)
                );

            } else {
                test.put(
                        profileId,
                        profiles.get(profileId)
                );
            }
        }

        return new Split(train, validation, test);
    }
}
