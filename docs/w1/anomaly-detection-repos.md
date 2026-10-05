# W1: Anomaly Detection Repo Survey

**Owner:** H  **Week:** W1 (Oct 1 – Oct 7)
**Task:** Survey anomaly detection repos (e.g., PyOD); compile comparison table.

Repo metadata (stars, license, last push, latest release) was pulled from the GitHub API on **2026-10-05**.

## What we need from a library

From the project plan, the detector has to support:

1. **W2:** a simple **threshold detector** baseline
2. **W3:** a **rule-based vs. ML** comparison on the chosen dataset (confusion matrix + metrics)
3. **W4:** a **real-time pipeline** (simulator → MQTT → detector → alerts), so the detector has to score **one reading at a time**
4. Python, open-source license that's fine for coursework, still maintained

## Comparison table

| # | Repo | Stars | License | Last push | Latest release | Data type | Streaming (one point at a time) | Rule-based detectors | Notes |
|---|------|------:|---------|-----------|----------------|-----------|:---:|:---:|-------|
| 1 | [yzhao062/pyod](https://github.com/yzhao062/pyod) | 10.0k | BSD-2 | 2026-10-04 | v3.6.6 (2026-09) | Tabular (+ time series, others) | ✗ (batch `fit`/`predict`) | ✗ | 60+ detectors under one API (IForest, LOF, ECOD, AutoEncoder…). The standard choice for offline ML baselines. |
| 2 | [online-ml/river](https://github.com/online-ml/river) | 6.1k | BSD-3 | 2026-10-02 | 0.26.1 (2026-08) | Streams | ✓ (`learn_one`/`score_one`) | Partial (`anomaly.filter` thresholds) | `river.anomaly` includes HalfSpaceTrees, LOF, OneClassSVM, Gaussian scorer, LODA. Designed for incremental learning, so it fits the MQTT pipeline. |
| 3 | [arundo/adtk](https://github.com/arundo/adtk) | 1.2k | MPL-2.0 | 2024-08-01 | v0.6.2 (2020-04) | Univariate / multivariate time series | ✗ | ✓ | Rule-based detectors (ThresholdAD, LevelShiftAD, PersistAD…) and pipelines. Close match to "temperature above limit" logic, but no release since 2020. |
| 4 | [sintel-dev/Orion](https://github.com/sintel-dev/Orion) | 1.4k | MIT | 2026-10-05 | v0.7.1 (2025-03) | Time series | ✗ | ✗ | Deep / unsupervised pipelines: TadGAN, LSTM dynamic threshold, LSTM/dense autoencoder, VAE, AER, ARIMA, matrix profile. Built-in benchmarks, but heavyweight. |
| 5 | [unit8co/darts](https://github.com/unit8co/darts) | 9.5k | Apache-2.0 | 2026-09-30 | — | Time series | ✗ | ✓ (threshold detectors in `darts.ad`) | Mainly a forecasting library. Its `darts.ad` module does forecast-residual anomaly detection with scorers + detectors. |
| 6 | [stumpy-dev/stumpy](https://github.com/stumpy-dev/stumpy) | 4.2k | BSD-3 | 2026-10-03 | v1.14.1 (2026-02) | Time series | Partial (`stumpi` incremental) | ✗ | Matrix profile: finds discords (unusual subsequences). Good for spotting odd temperature shapes, but it's not a full detector framework. |
| 7 | [SeldonIO/alibi-detect](https://github.com/SeldonIO/alibi-detect) | 2.5k | **BSL 1.1** | 2025-12-11 | v0.13.0 (2025-12) | Tabular, image, time series | Partial (online drift detectors) | ✗ | Outlier + drift + adversarial detection. ⚠ Business Source License since 2024: allowed for non-production use only. |
| 8 | [linkedin/luminol](https://github.com/linkedin/luminol) | 1.2k | Apache-2.0 | 2025-08-22 | — | Univariate time series | ✗ | ✗ | Small and simple (bitmap, derivative, exp. average detectors). Few features, rarely updated. |
| 9 | [xuhongzuo/DeepOD](https://github.com/xuhongzuo/DeepOD) | 0.6k | BSD-2 | 2025-07-24 | 0.4.1 (2023-09) | Tabular + time series | ✗ | ✗ | Deep-learning detectors with a PyOD-style API. Small community. |
| 10 | [datamllab/tods](https://github.com/datamllab/tods) | 1.7k | Apache-2.0 | 2023-09-11 | — | Time series | ✗ | ✗ | AutoML outlier detection system. Not updated since 2023. |
| — | [salesforce/Merlion](https://github.com/salesforce/Merlion) | 4.5k | BSD-3 | — | v2.0.2 (2023-02) | Time series | — | — | **Archived** (read-only). Listed only because it shows up in many search results. Don't use it. |

## Recommendation

| Use | Pick | Why |
|-----|------|-----|
| W2 threshold baseline | **Write it ourselves** (a few lines of NumPy/pandas), with ADTK's `ThresholdAD` / `PersistAD` as reference | The logic is simple. Writing it ourselves avoids depending on ADTK, which hasn't had a release since 2020, and makes it easy to plug in limits from the standards (e.g. IEC 60085 insulation class temperatures). |
| W3 ML baseline (offline) | **PyOD** | Most widely used and actively maintained, with a permissive license. One API lets us compare several detectors (IForest, ECOD, LOF, AutoEncoder) on the same dataset. |
| W4 real-time detector | **River** | The only well-maintained library that scores one reading at a time. `HalfSpaceTrees` fits naturally into an MQTT subscriber loop. |
| Optional (W6–W7 optimization) | Orion or STUMPY | Try them if the baselines miss failure patterns that develop over time. |

**Avoid:** Merlion (archived), TODS (no updates since 2023), alibi-detect (BSL license).

## Open questions for the team

- What the best ML library is depends on the **dataset decision** (J's survey). AI4I 2020 is tabular, so PyOD fits directly. CWRU / NASA bearing are vibration time series, so features need to be extracted first, or Orion / STUMPY become more relevant.
- Does the W4 detector have to learn online, or can we train offline with PyOD and only *score* in the stream? If the second, PyOD + a fixed threshold also works for W4.
