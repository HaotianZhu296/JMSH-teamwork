# Motor temperature models

This Java 17 module contains the selected, trained GBDT models for forecasts at +1, +5, +10, +30 and +60 seconds. Each model uses 61 consecutive 1 Hz readings and predicts a temperature change, which is added to the current measured temperature.

For teammates: [download the English Word model usage guide](docs/Motor_Model_User_Guide.docx). It includes setup, complete runnable Java examples, the input/output contract and optional MQTT integration. The examples are also available as source files in [examples/](examples/).

## Run the saved models

From the repository root, run:

```bash
cd training
./scripts/run-java.sh ml.motor.MotorPredictMain
```

The command loads the five model files and prints the current temperature and each forecast as JSON. It uses the included 61-reading example from validation Profile 79. The complete training CSV is needed only for training and dataset-wide evaluation.

For your own history, provide the model directory and a JSONL file:

```bash
./scripts/run-java.sh ml.motor.MotorPredictMain \
  results/rolling_full_v1 examples/profile79-history.jsonl
```

The input must contain exactly 61 readings from one machine and Profile, ordered by time at 1 Hz. Each JSON object includes `machineId`, `profileId`, `sampleIndex`, `timestampMs`, `statorWinding`, `coolant`, `ambient`, `motorSpeed`, `torque`, `iD`, `iQ`, `uD` and `uQ`. Temperature units are Celsius. The dataset's original 2 Hz indices advance by two per 1 Hz reading.

Java callers load the registry once and reuse it for predictions:

```java
try (var registry = ForecastModelRegistry.load(Path.of("results/rolling_full_v1"))) {
    if (!registry.errors().isEmpty()) {
        throw new IllegalStateException(registry.errors().toString());
    }
    double forecast5SecondsC = registry.models().get(5).predict(history61);
}
```

`history61` is a `List<MotorReading>`. For one incoming record per second, `RollingForecastService.onReading(ForecastTelemetry)` manages per-machine buffers and returns `ForecastEnvelope`. Other Maven modules can depend on `edu.teamjmsh:machine_overheating_ml:1.0-SNAPSHOT` after installing this module with `./scripts/mvn.sh install`. The repository root pom builds the storage module separately.

## Published models and measured accuracy

The model bundle is [results/rolling_full_v1](results/rolling_full_v1/). The five serialized models total 1,924,288 bytes. The repository explicitly tracks these small final artifacts and their metadata. [selection.properties](results/rolling_full_v1/selection.properties) chooses the model for each horizon.

| Horizon | Model file under the bundle | Validation MAE °C | Validation RMSE °C | Persistence MAE °C |
|---|---|---:|---:|---:|
| 1 s | `models_gbdt_h1_s42/gbdt.ser` | 0.053426 | 0.094247 | 0.086169 |
| 5 s | `models_gbdt_h5_s42/gbdt.ser` | 0.139783 | 0.246074 | 0.346959 |
| 10 s | `models_gbdt_h10_s42/gbdt.ser` | 0.232137 | 0.411737 | 0.668405 |
| 30 s | `models_retained_h30/gbdt.ser` | 0.876021 | 1.620342 | 1.824525 |
| 60 s | `models_retained_h60/gbdt.ser` | 1.950163 | 3.617523 | 3.294472 |

All rows use the same 8,000 validation origins. The 1/5/10-second models use all 885,130 valid training windows, including both original sampling phases. The 30/60-second models were retained after expanded training failed the predeclared improvement rule. Validation MAE at 1 second improved 38.0% over Persistence, with 95.69% of predictions within ±0.2°C.

The complete dataset has 69 Profiles split into 48 training, 10 validation and 11 testing Profiles. The newer selected bundle has validation results only. Its original testing Profiles had already been evaluated for the earlier experiment. [Full experiment report](results/rolling_full_v1/REPORT.md)

Each model directory includes `gbdt.ser`, `tree.properties`, validation metrics and its save/reload check. The published bundle's checksums cover its five model binaries, five metadata files and selection manifest:

```bash
cd results/rolling_full_v1
shasum -a 256 -c MODEL_SHA256SUMS
```

Smile loads the model using Java serialization. Use this repository's verified model files and keep Smile 3.1.1 and the feature schema compatible.

## Tests and offline training

```bash
./scripts/mvn.sh test
```

The tests cover causal windows, Profile isolation, input validity, normalization, model reloads, rolling history, report preservation and the published model fixture. The current native GRU test dependencies target macOS Apple Silicon, matching the training environment. GRU execution on another platform requires matching DJL native dependencies.

The dataset, original sampled-window manifests and unselected candidate model binaries are separate training inputs. To recreate the baseline before expanded training:

```bash
./scripts/run-java.sh ml.motor.MotorHorizonExperimentMain train \
  data/measures_v2.csv results/reproduced_baseline
./scripts/run-java.sh ml.motor.MotorFullTrainingMain \
  data/measures_v2.csv results/reproduced_baseline results/reproduced_full
./scripts/run-java.sh ml.motor.MotorTrainingAuditMain \
  data/measures_v2.csv results/reproduced_baseline results/reproduced_full
```

Use new output directories. The committed historical summaries document the original experiments, while a recreated baseline supplies the complete model and window manifests needed by the expanded-training runner. [Training configuration](README_FULL_TRAINING.md)

## Code and team integration

- [Code map](docs/CODE_MAP.md) describes the data, features, models, evaluation and runtime classes.
- Historical single-horizon experiments are in `src/main/java/ml/motor/legacy/`.
- [MQTT integration](README_ROLLING.md) documents the optional service and Dashboard import template.
- [Example provenance](examples/README.md) records the included real input window.
