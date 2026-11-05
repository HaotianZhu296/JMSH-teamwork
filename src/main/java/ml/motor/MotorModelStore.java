package ml.motor;

import smile.anomaly.IsolationForest;
import smile.regression.RandomForest;
import smile.io.Read;
import smile.io.Write;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class MotorModelStore {

    public record LoadedModels(
            RandomForest forecastModel,
            IsolationForest anomalyModel,
            DetectionConfig config
    ) {}

    public static void save(
            Path directory,
            RandomForest forecastModel,
            IsolationForest anomalyModel,
            DetectionConfig config) throws IOException {

        Files.createDirectories(directory);

        // Save the two trained models
        Write.object(
                forecastModel,
                directory.resolve("forecast_rf.ser")
        );

        Write.object(
                anomalyModel,
                directory.resolve("anomaly_if.ser")
        );

        // Save inference configuration
        Properties properties = new Properties();

        properties.setProperty("schema.version", "1");
        properties.setProperty("smile.version", "3.1.1");

        properties.setProperty(
                "sampling.hz", "1"
        );

        properties.setProperty(
                "forecast.horizon.seconds", "60"
        );

        properties.setProperty(
                "forecast.feature.names",
                String.join(
                        ",",
                        ForecastDatasetBuilder.FEATURE_NAMES
                )
        );

        properties.setProperty(
                "anomaly.feature.names",
                "statorWinding,coolant,ambient,"
                        + "motorSpeed,torque,iD,iQ,"
                        + "temperatureDifference"
        );

        properties.setProperty(
                "warning.temperature",
                Double.toString(config.warningTemperatureC())
        );

        properties.setProperty(
                "critical.temperature",
                Double.toString(config.criticalTemperatureC())
        );

        properties.setProperty(
                "max.rise.rate",
                Double.toString(config.maxRiseRateCPerSecond())
        );

        properties.setProperty(
                "future.warning.temperature",
                Double.toString(
                        config.futureWarningTemperatureC()
                )
        );

        properties.setProperty(
                "anomaly.threshold",
                Double.toString(config.anomalyThreshold())
        );

        try (Writer writer = Files.newBufferedWriter(
                directory.resolve("model.properties"),
                StandardCharsets.UTF_8)) {

            properties.store(
                    writer,
                    "Motor ML Model Configuration"
            );
        }

        System.out.println(
                "Models saved to: "
                        + directory.toAbsolutePath()
        );
    }

    public static LoadedModels load(
            Path directory)
            throws IOException, ClassNotFoundException {

        Properties properties = new Properties();

        try (Reader reader = Files.newBufferedReader(
                directory.resolve("model.properties"),
                StandardCharsets.UTF_8)) {

            properties.load(reader);
        }

        if (!"1".equals(
                properties.getProperty("schema.version"))
                || !"3.1.1".equals(
                properties.getProperty("smile.version"))
                || !"1".equals(
                properties.getProperty("sampling.hz"))
                || !"60".equals(
                properties.getProperty(
                        "forecast.horizon.seconds"))) {

            throw new IOException(
                    "Incompatible model configuration"
            );
        }

        String expectedForecastFeatures =
                String.join(
                        ",",
                        ForecastDatasetBuilder.FEATURE_NAMES
                );

        if (!expectedForecastFeatures.equals(
                properties.getProperty(
                        "forecast.feature.names"))) {

            throw new IOException(
                    "Forecast feature schema mismatch"
            );
        }

        String expectedAnomalyFeatures =
                "statorWinding,coolant,ambient,"
                        + "motorSpeed,torque,iD,iQ,"
                        + "temperatureDifference";

        if (!expectedAnomalyFeatures.equals(
                properties.getProperty(
                        "anomaly.feature.names"))) {

            throw new IOException(
                    "Anomaly feature schema mismatch"
            );
        }

        // Load models from disk
        Object forecastObject = Read.object(
                directory.resolve("forecast_rf.ser")
        );

        Object anomalyObject = Read.object(
                directory.resolve("anomaly_if.ser")
        );

        if (!(forecastObject instanceof RandomForest forecast)
                || !(anomalyObject instanceof IsolationForest anomaly)) {

            throw new IOException(
                    "Unexpected model types"
            );
        }

        DetectionConfig config = new DetectionConfig(
                Double.parseDouble(properties.getProperty(
                        "warning.temperature")),

                Double.parseDouble(properties.getProperty(
                        "critical.temperature")),

                Double.parseDouble(properties.getProperty(
                        "max.rise.rate")),

                Double.parseDouble(properties.getProperty(
                        "future.warning.temperature")),

                Double.parseDouble(properties.getProperty(
                        "anomaly.threshold"))
        );

        return new LoadedModels(
                forecast,
                anomaly,
                config
        );
    }
}

