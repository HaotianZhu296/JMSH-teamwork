
package ml.motor;

import java.io.IOException;
import java.nio.file.Path;

public class MotorDetectionFacade {

    private final UnifiedDetectionService service;

    public MotorDetectionFacade(Path modelDirectory)
            throws IOException, ClassNotFoundException {

        MotorModelStore.LoadedModels loaded =
                MotorModelStore.load(modelDirectory);

        this.service = new UnifiedDetectionService(
                loaded.forecastModel(),
                loaded.anomalyModel(),
                loaded.config()
        );
    }

    public DetectionResult detect(
            MotorSensorMessage message) {

        return service.onReading(message);
    }
}
