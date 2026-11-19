# Published-model example

`profile79-history.jsonl` contains 61 real historical readings from validation Profile 79 of the Electric Motor Temperature dataset. They are the even original sample indices 0 through 120, representing 0 through 60 seconds at 1 Hz. Replay timestamps are `sampleIndex * 500` milliseconds relative to the Profile start.

Source file SHA-256:

```text
78f3d150f0f2ad9c5dc7ff24dd12c00d386ad530f48c1589ad24fcd88867d3ad
```

The file contains historical input measurements. The model generates all future temperature outputs. This one-window example verifies loading and calling the model. Dataset-wide accuracy is reported separately on the 8,000 validation origins.

`expected-forecast.json` records outputs obtained from the original local model bundle for this history. The published-model regression test checks the included model files against those values.
