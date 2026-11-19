# Published-model example

`profile79-history.jsonl` contains 61 real historical readings from validation Profile 79 of the Electric Motor Temperature dataset. They are the even original sample indices 0 through 120, representing 0 through 60 seconds at 1 Hz. Replay timestamps are `sampleIndex * 500` milliseconds relative to the Profile start.

Source file SHA-256:

```text
78f3d150f0f2ad9c5dc7ff24dd12c00d386ad530f48c1589ad24fcd88867d3ad
```

The file contains historical input measurements. The model generates all future temperature outputs. This one-window example verifies loading and calling the model. Dataset-wide accuracy is reported separately on the 8,000 validation origins.

`expected-forecast.json` records outputs obtained from the original local model bundle for this history. The published-model regression test checks the included model files against those values.

## Java integration examples

The [Word usage guide](../docs/Motor_Model_User_Guide.docx) explains input fields, Java calls, rolling outputs and optional MQTT integration. These complete Java 17 examples match the guide and run from the `training` directory:

```bash
./scripts/run-java.sh examples/CallModelExample.java
./scripts/run-java.sh examples/RollingModelExample.java
```

`CallModelExample` validates one complete history and calls all five models. `RollingModelExample` replays the same historical inputs one at a time, emitting 60 `WARMING_UP` results followed by a `READY` result. Replay runs immediately using the input timestamps; a real sensor or MQTT callback supplies new readings each second. The example uses the configuration file's thermal defaults and leaves the separately supplied anomaly model unset.
