# Full training-data experiment (Java / GBDT)

See [README.md](README.md) for the module overview and [docs/CODE_MAP.md](docs/CODE_MAP.md) for the Java file map.

The GitHub checkout includes the five selected inference models and compact experiment summaries. The original full CSV, window manifests and unselected candidate models remain separate training artifacts. A fresh clone can run `MotorPredictMain` immediately after compilation. For expanded retraining, first create a complete baseline with `MotorHorizonExperimentMain train`, as shown in the main README, then pass that new baseline directory to the commands below.

This follow-up increases training-data coverage without fitting on validation or testing Profiles. It does not change Dashboard, MQTT settings, or the previous model bundle.

## What "full" means

- The dataset has 1,330,816 raw 2 Hz rows and 69 Profiles.
- The existing split is unchanged: 48 train / 10 validation / 11 test, seed 42.
- The 48 training Profiles contain 896,650 raw rows.
- Previously, the experiment used 24,000 windows sampled from the even-index 1 Hz stream, with candidate origins every 5 seconds. A window contains 61 history rows; 24,000 windows does **not** mean only 24,000 individual rows were used.
- The new experiment uses **885,130 eligible windows**, with no window-count limit. It builds two separate 1 Hz streams from original even and odd sample indices (offsets 0 and 0.5 seconds), then uses every eligible origin in both streams.
- Every input still has exactly 61 observations spaced 1 second apart. A target at +h seconds is at original sample index `origin + 2*h`. The odd-index stream is only another training view of real data; no interpolation or synthetic sensor measurements are created.
- An origin must have the complete 60-second history and all labels through +60 seconds to preserve common origins across horizons. The first/last 60 seconds cannot themselves all be prediction origins. Boundary rows can still participate as history or targets. Invalid or discontinuous sequences remain excluded.
- The validation set is the **exact original 8,000 origins in the same order**, verified against the prior window manifest. Both models use the same 33 causal tree features and the same tree hyperparameters.
- The old testing set is **not re-evaluated**. Its previously reported test metrics belong to the previous models, not these new models.

The two phase streams overlap heavily in time. More windows do not imply proportionally more independent information. No claim is made that all 69 Profiles were used for fitting, or that increasing the training set guarantees improved generalization.

The source CSV has no physical timestamps. Offline continuity is checked using Profile IDs and original row indices under the dataset's 2 Hz sampling assumption; missing measurements that were already removed before CSV export cannot be detected from these fields alone.

## Run

From this module directory, with Java 17:

```bash
./scripts/mvn.sh test
./scripts/run-java.sh ml.motor.MotorFullTrainingMain \
  data/measures_v2.csv results/rolling_v1 results/rolling_full_v1
./scripts/run-java.sh ml.motor.MotorTrainingAuditMain \
  data/measures_v2.csv results/rolling_v1 results/rolling_full_v1
```

Use a **new output directory** for another experiment. The previous bundle must include its model directories, `experiment.properties`, `profile_split.csv`, and `windows_validation.csv`; these verify data identity and comparison fairness. The audit also requires the saved `windows_train.csv` manifests. Do not delete the original models or manifests to make a repeat run succeed.

This experiment trains the already-selected **GBDT family only**, independently for 1/5/10/30/60 seconds, each with seeds 42 and 2026. It does not allocate a full 885,130×61×13 GRU tensor or rerun the old GRU comparison. Tree settings remain: 200 trees, depth 6, maximum 24 leaves, node size 10, shrinkage 0.05, sampling rate 0.8, least-squares delta target. The complete training table is supplied to GBDT; each tree retains stochastic 80% sampling as in the baseline.

The selection rule is recorded before fitting: adopt the full-data model for a horizon only if validation MAE improves for **both seeds** and the predeclared deployment seed 42 has no RMSE regression. Otherwise copy the previous seed-42 model into the new bundle. `selection.properties` records the choice per horizon; no running service configuration is switched automatically.

## Outputs

`MotorTrainingAuditMain` audits the expanded-training experiment. The additional `ForecastDiagnosticsMain` tool writes its comparison against Persistence to `persistence_bootstrap.csv`; `profile_bootstrap.csv` stores the old-versus-full comparison produced during training.

- `experiment.properties`: data hash, Profile IDs, phase/cadence, all window counts and selection rule.
- `training_coverage.csv`: counts of original rows and eligible even/odd windows by training Profile.
- `data_utilization.csv`: unique original rows participating as history, or history plus targets, before and after expansion. Coverage of targets aggregates the five separate horizon models; it is not a claim that every row is a feature of every individual model.
- `comparison.csv`: old/full metrics for both seeds, training duration and reload differences.
- `candidates.csv`: MAE/RMSE/MSE/P95/within-0.2/high-temperature counts and baseline comparisons.
- `validation.csv`, `selection.properties`: recommended model per horizon.
- `profile_bootstrap.csv`: paired improvement intervals obtained by resampling whole validation Profiles, not individual overlapping windows. There are only 10 validation Profiles.
- `predictions_validation_*`, `profiles_validation_*`, `errors_*`, `curve_*`: raw predictions and error diagnostics for the full-data seed-42 candidates.
- `loss_gbdt_h*_s*.csv/png`: training-diagnostic (4096 windows) and validation loss after each boosting stage, generated from the saved models without refitting or trimming. All 200 trees remain fixed.
- `models_gbdt_h*_s*/`: separately saved models, metadata, complete validation metrics, loss curves and save/reload consistency checks.
- `FULL_TRAINING_COMPLETE`: distinguishes this validation-only experiment from the earlier one-time testing workflow.
- `AUDIT_COMPLETE`, `MODEL_SHA256SUMS`: completed coverage/curve audit and checksums for model weights, model metadata and the selection manifest.

To refit a deployment model using all **69** Profiles, the validation/test rows would become training data. That is a separate final-refit choice after model selection; the resulting model would require a new external holdout for an unbiased accuracy claim. It is not performed here because the task requires preserving independent evaluation.
