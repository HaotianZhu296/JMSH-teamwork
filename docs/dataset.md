# Dataset overview

Source: Kaggle *Electric Motor Temperature* (`measures_v2.csv`), measurements of a permanent magnet synchronous motor on a test bench at Paderborn University. See the [dataset page](https://www.kaggle.com/datasets/wkirgsn/electric-motor-temperature) for the original description; its license is not stated in the sources we checked, so check it there before redistributing the file (it is not stored in this repository).

## Shape

- 1,330,816 rows, 13 columns, sampled at 2 Hz, in 69 measurement sessions (`profile_id`). Sessions are independent; the dataset authors advise not to predict one session from another, so the train/validation/test split is by whole profile (`config/profile_split.csv`).
- Temperatures are in °C and not standardized. Ranges in the whole file: `stator_winding` 18.6 to 141.4 °C, `ambient` 8.8 to 30.7 °C, `pm` 20.9 to 113.6 °C.
- The file has no failure or overheating label. Any "overheating" label is defined by the project (a threshold on `stator_winding`), not by the dataset.
- Columns: `u_q`, `coolant`, `stator_winding`, `u_d`, `stator_tooth`, `motor_speed`, `i_d`, `i_q`, `pm`, `stator_yoke`, `ambient`, `torque`, `profile_id`.

## What the simulator sends

Every second row of a profile is kept (2 Hz to 1 Hz) and published as one JSON message; see [sensor-message-spec.md](sensor-message-spec.md). After this reduction the 69 profiles contain 665,422 readings, the same number the training module reports for its reader. `pm`, `stator_tooth` and `stator_yoke` are not sent.

## Per profile

The temperature columns are `stator_winding`, which the detection service reads as `processTemperature`. "Seconds >= X" counts 1 Hz readings at or above X °C. 80 and 95 °C are the warning and critical thresholds in `detection-alert/application.yaml`; 100 and 120 °C are the experiment thresholds used by the training module. None of these is a manufacturer's safety limit.

| Profile | Split | Seconds (1 Hz) | Max stator winding (°C) | Seconds >= 80 °C | Seconds >= 95 °C | Seconds >= 100 °C | Seconds >= 120 °C |
|---|---|---|---|---|---|---|---|
| 2 | train | 9679 | 103.3 | 3426 | 3099 | 2728 | 0 |
| 3 | validation | 9624 | 39.1 | 0 | 0 | 0 | 0 |
| 4 | train | 16712 | 113.4 | 2243 | 768 | 492 | 0 |
| 5 | train | 7394 | 43.4 | 0 | 0 | 0 | 0 |
| 6 | train | 20194 | 121.0 | 6909 | 4989 | 3195 | 19 |
| 7 | train | 7326 | 39.9 | 0 | 0 | 0 | 0 |
| 8 | validation | 9379 | 46.0 | 0 | 0 | 0 | 0 |
| 9 | train | 10168 | 54.5 | 0 | 0 | 0 | 0 |
| 10 | train | 7628 | 93.6 | 410 | 0 | 0 | 0 |
| 11 | train | 3944 | 74.5 | 0 | 0 | 0 | 0 |
| 12 | train | 10971 | 50.0 | 0 | 0 | 0 | 0 |
| 13 | train | 17953 | 39.4 | 0 | 0 | 0 | 0 |
| 14 | train | 9299 | 67.3 | 0 | 0 | 0 | 0 |
| 15 | train | 9062 | 114.1 | 4916 | 4703 | 4596 | 0 |
| 16 | train | 10323 | 59.2 | 0 | 0 | 0 | 0 |
| 17 | test | 7982 | 72.3 | 0 | 0 | 0 | 0 |
| 18 | train | 18866 | 64.1 | 0 | 0 | 0 | 0 |
| 19 | train | 5205 | 102.3 | 4073 | 3603 | 2575 | 0 |
| 20 | train | 21986 | 125.3 | 8171 | 4676 | 4159 | 709 |
| 21 | train | 8661 | 104.8 | 3170 | 2848 | 2564 | 0 |
| 23 | test | 5928 | 124.5 | 3033 | 2880 | 2815 | 2453 |
| 24 | test | 7508 | 123.3 | 4359 | 4124 | 4016 | 2762 |
| 26 | train | 8333 | 124.4 | 4552 | 4423 | 4372 | 4094 |
| 27 | validation | 17681 | 138.6 | 7047 | 5446 | 5094 | 111 |
| 29 | test | 10679 | 141.4 | 4995 | 4257 | 3720 | 1227 |
| 30 | train | 11932 | 114.3 | 785 | 538 | 463 | 0 |
| 31 | train | 7794 | 112.9 | 4025 | 3093 | 2080 | 0 |
| 32 | train | 10480 | 131.4 | 4801 | 3669 | 3232 | 687 |
| 36 | validation | 11305 | 133.8 | 2342 | 1727 | 1508 | 646 |
| 41 | train | 8350 | 124.8 | 5792 | 2592 | 2117 | 227 |
| 42 | train | 8460 | 102.0 | 2705 | 588 | 158 | 0 |
| 43 | train | 4222 | 122.1 | 1229 | 527 | 384 | 55 |
| 44 | validation | 13171 | 114.2 | 3864 | 2226 | 1026 | 0 |
| 45 | train | 8571 | 114.8 | 4269 | 1991 | 1148 | 0 |
| 46 | train | 1090 | 127.5 | 1090 | 1090 | 1089 | 226 |
| 47 | train | 1088 | 93.9 | 488 | 0 | 0 | 0 |
| 48 | train | 10992 | 127.8 | 4928 | 2504 | 1733 | 305 |
| 49 | train | 5408 | 128.0 | 3216 | 2129 | 1750 | 624 |
| 50 | train | 5405 | 121.2 | 2297 | 1348 | 1074 | 29 |
| 51 | train | 3131 | 122.8 | 2735 | 1933 | 1382 | 24 |
| 52 | train | 1863 | 129.5 | 828 | 631 | 542 | 258 |
| 53 | validation | 16221 | 114.6 | 5751 | 1887 | 1124 | 0 |
| 54 | train | 5404 | 104.8 | 2930 | 869 | 261 | 0 |
| 55 | train | 5404 | 117.8 | 2692 | 985 | 808 | 0 |
| 56 | train | 16562 | 131.6 | 12018 | 8565 | 6887 | 1954 |
| 57 | validation | 7202 | 131.6 | 4263 | 2629 | 1464 | 283 |
| 58 | train | 16691 | 131.1 | 9378 | 5531 | 4807 | 1589 |
| 59 | test | 3738 | 132.7 | 2674 | 2050 | 1821 | 563 |
| 60 | train | 7272 | 128.9 | 2592 | 1851 | 1624 | 294 |
| 61 | validation | 7258 | 129.7 | 3716 | 2461 | 1988 | 551 |
| 62 | validation | 12800 | 101.3 | 1666 | 399 | 14 | 0 |
| 63 | train | 8334 | 104.8 | 3729 | 152 | 107 | 0 |
| 64 | train | 3125 | 95.3 | 918 | 28 | 0 | 0 |
| 65 | test | 20047 | 129.2 | 8410 | 4747 | 3847 | 903 |
| 66 | train | 18238 | 128.3 | 6967 | 3258 | 2760 | 378 |
| 67 | test | 5568 | 89.4 | 129 | 0 | 0 | 0 |
| 68 | test | 11666 | 126.2 | 5533 | 1883 | 1284 | 198 |
| 69 | train | 7675 | 113.3 | 4535 | 2925 | 1826 | 0 |
| 70 | train | 12839 | 120.6 | 1991 | 920 | 559 | 8 |
| 71 | train | 7328 | 104.8 | 1984 | 508 | 161 | 0 |
| 72 | test | 7651 | 104.8 | 2581 | 1006 | 318 | 0 |
| 73 | train | 8393 | 131.6 | 3344 | 1730 | 1350 | 179 |
| 74 | test | 11881 | 129.3 | 6499 | 3128 | 2392 | 620 |
| 75 | train | 6736 | 130.1 | 3013 | 2007 | 1739 | 335 |
| 76 | train | 11094 | 117.9 | 3819 | 2435 | 1285 | 0 |
| 78 | test | 4223 | 100.8 | 558 | 163 | 15 | 0 |
| 79 | validation | 15577 | 128.2 | 8052 | 3169 | 2053 | 318 |
| 80 | train | 11912 | 121.1 | 4325 | 2059 | 1113 | 17 |
| 81 | train | 8836 | 104.6 | 2818 | 465 | 228 | 0 |

Profiles: 69, total simulated seconds: 665422
- train: 48 profiles, 448333 s, 37 profiles reach 95 °C
- validation: 10 profiles, 120218 s, 8 profiles reach 95 °C
- test: 11 profiles, 96871 s, 9 profiles reach 95 °C

## Choosing profiles

- A demo that should show alerts needs a profile that gets hot. Profile 57 (validation) reaches 131.6 °C and is the simulator's default. Profiles 3 and 8 (validation) stay below 47 °C and never trigger anything.
- Test profiles are for the final evaluation only.
- Most profiles reach the warning range for a while (see the table); a long replay of a single profile therefore shows alerts without injecting a scenario.

## Reproduce this table

```bash
python3 scripts/dataset_profile_stats.py data/measures_v2.csv config/profile_split.csv
```

The script uses only the Python standard library and reads the whole file once.
