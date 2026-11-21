# Sensor telemetry message

What the simulator (Part A) publishes and what the detection service (Part C), event store and dashboard (Part D) read.

## Transport

- Topic: `sensors/<machine_id>/telemetry` (default machine id `motor-01`). The broker ACL lets the `simulator` user write only `sensors/+/telemetry`.
- QoS 1. After a reconnect the broker can deliver a message twice, so receivers that need exactly-once handling should drop repeats by `(machineId, profileId, sampleIndex)`.
- Rate: one message per second per machine (1 Hz). Within one profile, messages are sent in order of increasing `sampleIndex`.
- Payload: one JSON object per message (`TelemetryJson`). The earlier one-line CSV format is still in the code (`TelemetryCsv`) but is no longer published.

## Fields

Source dataset: Kaggle *Electric Motor Temperature* (`measures_v2.csv`, permanent magnet synchronous motor, 2 Hz, 69 profiles). Temperatures are in °C in the dataset, so no unit conversion is done.

| JSON field | Type | Source column | Notes |
|---|---|---|---|
| `timestampMs` | long | (none) | Unix time in ms when the message is published. Used for latency measurement. |
| `machineId` | string | (none) | From `sim.machine_id`. |
| `sensorType` | string | (none) | Always `electric_motor`. |
| `airTemperature` | double | `ambient` | °C. |
| `processTemperature` | double | `stator_winding` | °C. This is the value the detection thresholds apply to. |
| `rotationalSpeed` | double | `motor_speed` | Dataset value, unit not checked by the simulator. |
| `torque` | double | `torque` | Dataset value, unit not checked by the simulator. |
| `toolWear` | double | (none) | Always `0.0`. The dataset has no tool wear. It exists because the shared schema came from the AI4I dataset. |
| `profileId` | int | `profile_id` | The measurement session. The receiver must reset any history when it changes. |
| `sampleIndex` | int | row index in profile | Index in the original 2 Hz file, so after 1 Hz down-sampling it is 0, 2, 4, ... Simulated seconds = `sampleIndex / 2`. |
| `coolantC` | double | `coolant` | °C. |
| `iD`, `iQ` | double | `i_d`, `i_q` | Dataset values. |
| `uD`, `uQ` | double | `u_d`, `u_q` | Dataset values. |

Numbers have four decimal places. Only raw measurements are sent: no derived features, no labels and no future values. Columns the models do not use (`pm`, `stator_tooth`, `stator_yoke`) are not sent.

Example (format only, not a real row):

```json
{"timestampMs":1700000000000,"machineId":"motor-01","sensorType":"electric_motor","airTemperature":22.0000,"processTemperature":65.5000,"rotationalSpeed":2000.0000,"torque":35.0000,"toolWear":0.0,"profileId":17,"sampleIndex":120,"coolantC":25.0000,"iD":1.2000,"iQ":2.5000,"uD":100.0000,"uQ":150.0000}
```

## Replay rules

- One machine, one profile at a time, in file order. A list of profiles is replayed one after another; `profileId` changes and `sampleIndex` restarts at 0 at each switch.
- The 2 Hz file is reduced to 1 Hz by keeping the rows with an even index inside each profile.
- If the broker is unreachable, the simulator keeps retrying the same message (0.5 s, 1 s, 2 s, 4 s, then every 5 s) for up to `sim.reconnect_max_wait_ms` (default 60 s), then exits. Nothing is skipped and the order is kept, so `sampleIndex` has no gaps.

## Train / validation / test split

`config/profile_split.csv` assigns each of the 69 profiles to `train` (48), `validation` (10) or `test` (11). The split is by whole profile, never by random rows, and the whole team uses this one file. A unit test (`ProfileSplitTest`) checks its size and that the default demo profile is not a test profile.
