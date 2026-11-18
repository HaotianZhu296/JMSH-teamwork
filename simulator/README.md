# Sensor simulator (Part A)

Replays the Electric Motor Temperature dataset as live MQTT telemetry. Message format: [docs/sensor-message-spec.md](../docs/sensor-message-spec.md).

## Run

The full stack (broker, detection service, dashboard and this simulator) starts with `docker compose up -d --build` from the repository root; see the top-level README. To run the simulator alone against a broker that is already up:

```bash
cd simulator
mvn clean compile
MQTT_USERNAME=simulator MQTT_PASSWORD=sim_pass_2024 \
  mvn -q exec:java -Dexec.mainClass="edu.northeastern.cs5010.mhd.pipeline.SensorSimulator"
```

An optional argument is the path of a properties file (default `src/main/resources/simulator.properties`; Docker uses `simulator-docker.properties`). `data.file` is relative to where you start the program, so with the default file start it from the repository root or change the path. The dataset (`measures_v2.csv`) is not in Git; download it from Kaggle and put it in `data/`.

Credentials are read from the environment (`MQTT_USERNAME`, `MQTT_PASSWORD`) and never from a file. A broker URL starting with `ssl://` can be used, with the trust store given as `-Djavax.net.ssl.trustStore=...`; this has not been tested against a TLS broker yet.

## Settings

| Key | Default | Meaning |
|---|---|---|
| `mqtt.broker` | `tcp://localhost:1883` | Broker URL. |
| `data.file` | `data/measures_v2.csv` | Dataset CSV. |
| `data.limit` | `0` | Maximum readings to publish in total; 0 = all. |
| `data.stride` | `2` | Keep every Nth row of a profile. The file is 2 Hz, so 2 gives 1 Hz. |
| `sim.profiles` | `57` | Profile ids to replay, comma separated, in order. Empty = first profile in the file. |
| `sim.interval` | `1000` | Milliseconds between messages. 1000 = real time at 1 Hz. |
| `sim.machine_id` | `motor-01` | Machine id used in the topic and message. |
| `sim.topic_template` | `sensors/{machine_id}/telemetry` | Topic. |
| `sim.reconnect_max_wait_ms` | `60000` | How long to retry one message while the broker is down. |
| `scenario.*` | off | Scripted overheating, see below. |
| `scenario.log_file` | empty | If set (and the scenario is on), a JSON log of the injection is written there at the end of the run. |
| `scenario.warning_c`, `scenario.critical_c` | `80`, `95` | Reference thresholds used only in the log. Keep them equal to `processTemperature` in `detection-alert/application.yaml`. |

### Choosing a profile

The default profile 57 (validation set) lasts 7,202 s at 1 Hz. Its winding temperature first passes 80 °C at about second 1,247 and 95 °C at about second 1,313, and the maximum is 131.6 °C, so a normal run shows WARNING and then CRITICAL without any injection. At the real rate that is a wait of about 21 minutes for the first warning; use `sim.interval=100` for a faster demo. Profiles 3 and 8 never exceed 46 °C and are not useful for demonstrating alerts. Profiles in the test set are reserved for the final evaluation.

### Scripted overheating

For integration tests, set `scenario.enabled=true`. Starting at the `scenario.start`-th published reading (0-based), `stator_winding` (sent as `processTemperature`) rises linearly to `scenario.target_c` over `scenario.ramp` readings, stays there for `scenario.hold` readings, then the dataset values resume. If `scenario.base_c` is empty the ramp starts from the dataset's own value, so there is no jump. Other fields are unchanged and the result is deterministic. Set `scenario.log_file` to record what was injected. The file lists the first and last injected reading, the peak, and the first reading at or above the warning and critical reference temperatures, each with `profileId`, `sampleIndex` and the publish `timestampMs` that was put in the message. An evaluation can use it to tell injected events from real ones and to know when an alert is expected. Dataset values outside the injected window can exceed the thresholds as well; they are not in the log.

## Tests

```bash
cd simulator
mvn test
```

Covers the CSV reader, both message formats, the scenario, the retry behaviour and the shared split file. One test needs the real dataset in `data/` and is skipped without it.

## Known limits

- TLS and broker ACLs have been checked only through the Docker setup (plain TCP with passwords); the simulator's TLS path is untested.
- One machine at a time. Several machines in parallel is not implemented.
- `toolWear` is always 0 because the dataset has no such column.
