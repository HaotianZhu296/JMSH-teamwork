# MQTT integration for rolling temperature forecasts

The published model bundle is `results/rolling_full_v1`. `integration/forecast.properties` selects it. Start with [README.md](README.md) for a model-loading example that runs locally from the repository checkout.

`MotorForecastMqttMain` subscribes to `sensors/+/telemetry` and publishes JSON on `forecasts/<machineId>`. The process loads its models at startup. `RollingForecastService` maintains a separate 61-reading history for each machine, applies continuity and input checks, and updates all five forecasts on each new record.

## Start the optional MQTT service

From the `training` module directory, configure the broker URL and existing installation's thresholds in `integration/forecast.properties`. Supply credentials using environment variables:

```bash
export FORECAST_MQTT_USERNAME=forecast
export FORECAST_MQTT_PASSWORD='your-local-password'
./scripts/run-java.sh ml.motor.MotorForecastMqttMain integration/forecast.properties
```

The broker must authorize that user to read `sensors/+/telemetry` and write `forecasts/+`. `integration/forecast-acl.conf` and `scripts/add-forecast-user.sh` provide configuration examples for the team's Mosquitto installation.

The existing simulator schema is accepted, including `processTemperature`, `airTemperature`, `rotationalSpeed`, `coolantC`, `profileId`, `sampleIndex`, `timestampMs`, `torque`, `iD`, `iQ`, `uD`, `uQ` and `machineId`. Unknown fields are ignored, while required values must be finite numbers. A 1 Hz record advances the original dataset `sampleIndex` by two and its timestamp by 1000 ms. The default timestamp tolerance is ±250 ms.

The response has separate measured temperature and forecast fields. Forecasts include their horizon, target timestamp, source model and availability state. History gaps, Profile switches and invalid inputs reset the affected buffer. Before 61 valid records, the status is `WARMING_UP` and forecast temperatures are null. Exact duplicate packets are ignored. The service bounds the number of cached machines and serializes access to its buffers.

## Optional anomaly model and Dashboard

The published bundle contains the selected five temperature GBDT models. The pre-existing Isolation Forest/RF bundle is loaded from `legacy.models.directory` when provided. Install that separate bundle to obtain its anomaly scores. A missing anomaly model produces unavailable/null anomaly fields.

`integration/forecast-flow.json` is an importable Node-RED template for a matching team Dashboard setup. It subscribes to the forecast topic and displays measured values, prediction horizons, availability and statuses. This training-module publication provides the template for the integration owner to import and verify. The service's predictive warning appears in the forecast payload; the team's Spring alert persistence is a separate integration.

## Live verification tools

After the broker and forecast service are running, dataset-backed tools are available:

```bash
./scripts/run-java.sh ml.motor.MotorForecastReplayMain \
  data/measures_v2.csv tcp://127.0.0.1:1883 180 1000
./scripts/run-java.sh ml.motor.MotorForecastE2EMain \
  data/measures_v2.csv tcp://127.0.0.1:1883 results/mqtt-acceptance
```

Replay uses `SIM_MQTT_USERNAME` / `SIM_MQTT_PASSWORD`. Acceptance subscriptions use `DASH_MQTT_USERNAME` / `DASH_MQTT_PASSWORD`. These tools use validation data and test machine isolation, warmup, rolling target timestamps, invalid-input handling and thermal status behavior. See [the code map](docs/CODE_MAP.md) for each class's responsibility.
