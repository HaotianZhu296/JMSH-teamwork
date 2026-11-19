# Java 文件与功能对应表

当前电机模块有 42 个生产 Java 文件，其中 8 个集中在 `legacy`。测试代码有 4 个文件。下面列出每个类在现有调用关系中的职责。主源码根目录为 `src/main/java/ml/motor/`。

发布模型的直接调用入口为 `MotorPredictMain.java`。它读取 61 条 JSONL 历史，加载模型包，并输出五个时长的预测。默认使用仓库内的小样例，可从 `training/` 执行 `./scripts/run-java.sh ml.motor.MotorPredictMain`。

## 日常训练的四个入口

| 文件 | 功能与输入输出 |
|---|---|
| `MotorFullTrainingMain.java` | 当前全量 GBDT 训练入口。读取 CSV 和原实验目录，核验数据哈希、Profile 划分和验证起点。训练 1/5/10/30/60 秒、种子 42/2026，输出新模型、指标、模型选择清单及保存加载检查。 |
| `MotorHorizonExperimentMain.java` | 原多时长 GRU/GBDT 对照实验入口。负责训练配置、候选比较、验证集模型选择和一次性测试入口，还提供实验元数据和窗口清单的文件工具。全量训练入口复用这些工具。 |
| `MotorTrainingAuditMain.java` | 训练后审计。统计原始行覆盖，按选择清单重载五个模型并核对验证结果，生成各棵树阶段的训练/验证曲线及模型校验和。 |
| `ForecastDiagnosticsMain.java` | 误差原因分析。基于验证 Profile 做配对 bootstrap，对比 Persistence，并分析未来负载变化与误差的关系。未来工况仅用于事后分析。输出 `persistence_bootstrap.csv` 和 `validation_regimes.csv`。 |

## 数据、时间窗口与特征

| 文件 | 功能 |
|---|---|
| `MotorReading.java` | 保存一条电机读数、Profile ID、原始样本索引，提供异常检测特征。 |
| `MotorDataReader.java` | 用 Commons CSV 读取数据，按 Profile 保存记录并按 stride 降采样，保留原始 2 Hz 索引。 |
| `ProfileSplitter.java` | 按完整 Profile 划分训练/验证/测试集。69 个 Profile、种子 42 时得到 48/10/11。 |
| `ForecastWindows.java` | 生成过去 61 条的输入窗口和未来时长标签，检查连续性、有限数值及 Profile 边界。全量训练会分别枚举原始奇偶索引组成的两条 1 Hz 序列。 |
| `MotorForecastV2.java` | 提取历史温度、温升速率、负载、电流和功率代理等 30 个特征，同时支持历史 GBDT V2 推理。当前 GBDT 的 33 个特征复用这里的前 30 个。 |
| `MotorGruFeatures.java` | 为 GRU 的每个历史时间点生成固定顺序的 13 个特征，供训练和推理共用。 |

## 模型与统一评估

| 文件 | 功能 |
|---|---|
| `HorizonForecaster.java` | 统一的单时长模型接口，接收历史并返回摄氏度预测。 |
| `TreeHorizonModel.java` | Smile GBDT 的训练、推理、特征扩展至 33 维、独立保存加载及逐棵树诊断预测。 |
| `GruHorizonModel.java` | DJL GRU 网络、训练循环、早停、批量预测、模型保存加载及一致性检查。 |
| `ForecastNormalizer.java` | 仅用训练窗口计算 GRU 输入和目标的标准化参数，负责保存、加载及温度变化量反标准化。 |
| `BaselineForecaster.java` | Persistence 和历史趋势外推基准，用于量化模型是否有实际收益。 |
| `ForecastModelRegistry.java` | 根据 `selection.properties` 加载每个时长选中的模型，并报告缺失或加载失败。 |
| `ForecastEvaluation.java` | 统一计算 MAE、MSE、RMSE、P95、±0.2°C 比例、高温误差、逐 Profile 指标，并生成 CSV/PNG。 |

## 提供给团队的滚动预测接口

| 文件 | 功能 |
|---|---|
| `RollingForecastService.java` | 为每台机器维护 61 条历史，处理预热、重复、断点、Profile 变化及非法输入，调用缓存模型并组合当前状态、预测预警与异常结果。 |
| `ForecastTelemetry.java` | 解析传感器 JSON 的字段、机器 ID、时间戳和索引，兼容团队字段名。 |
| `ForecastEnvelope.java` | Dashboard 输出结构，包含实际温度、各时长预测、目标时间、状态与推理耗时。 |
| `MotorForecastMqttMain.java` | MQTT 进程入口，订阅 `sensors/+/telemetry`，发布 `forecasts/<machineId>`，启动时加载模型。 |
| `MotorForecastReplayMain.java` | 使用验证 Profile 回放数据到 MQTT，供团队联调。 |
| `MotorForecastE2EMain.java` | MQTT 接入验证工具，检查多机器历史、预热、目标时间滚动与异常输入。执行时需要正在运行的 broker 和预测服务。 |

这些类属于模型交付接口。团队的 `simulator/` 产生传感器数据，`detection-alert/` 负责现有检测和警报，`dashboard/` 负责显示。此处的推理接口被当前测试、MQTT 入口和团队联调用到。

## 异常检测及原接口兼容

| 文件 | 保留的实际用途 |
|---|---|
| `MotorIsolationForestTrainer.java` | 训练 Isolation Forest 并估计异常阈值，由历史 `legacy.MotorMain` 调用。 |
| `MotorModelStore.java` | 加载原 RF、Isolation Forest 和阈值配置。当前 MQTT 服务仍从此处获取异常模型。 |
| `CurrentThermalDetector.java` | 根据当前温度、阈值和温升速率判断热状态，当前滚动服务仍调用。 |
| `DetectionConfig.java` | 当前/未来温度阈值、温升阈值和异常阈值配置，供新旧服务使用。 |
| `MotorSensorMessage.java` | 原传感器消息结构及到 `MotorReading` 的转换，当前 JSON 解析仍复用。 |
| `DetectionResult.java` | 原检测接口的结果对象。 |
| `MotorDetectionFacade.java` | 向调用者提供原 RF/异常检测接口，初始化模型和服务。 |
| `UnifiedDetectionService.java` | 原 RF 60 秒预测与热状态、异常检测的组合服务。 |
| `ForecastDatasetBuilder.java` | 原 RF 特征及样本构建，原模型元数据加载仍检查这些特征名。 |
| `TemperatureForecastTrainer.java` | 原 RF 训练和预测，`UnifiedDetectionService` 使用其预测方法。 |

## 历史实验目录

以下文件位于 `src/main/java/ml/motor/legacy/`，包名均为 `ml.motor.legacy`。其模型算法及参数保留，命令行和 IntelliJ Run Configuration 需使用新包名。

| 文件 | 用途 | 原输出路径 |
|---|---|---|
| `MotorMain.java` | 原 RF + Isolation Forest 的训练、评估和保存演示 | `models/` |
| `MotorInferenceMain.java` | 加载原 RF/Isolation Forest，使用合成消息检查推理接口和历史重置 | 读取参数指定的目录，默认 `models/` |
| `MotorForecastV2Main.java` | 原单一 60 秒 GBDT V2 实验 | `models_v2/` |
| `MotorGruTrainMain.java` | 原单一 60 秒 GRU V4 实验 | `models_gru_v4/` |
| `MotorLearningCurveMain.java` | 原 RF 训练规模与误差曲线 | `results/learning_curve.csv/png` |
| `MotorAnomalyEvaluator.java` | 原 Isolation Forest 误差/异常分布评估 | 控制台 |
| `TemperatureModelEvaluator.java` | 原 RF 温度误差评估 | 控制台 |
| `EarlyWarningEvaluator.java` | 原过热预警评估 | 控制台 |

## 测试、配置和脚本

| 文件 | 功能 |
|---|---|
| `src/test/java/ml/motor/ForecastPipelineTest.java` | 特征顺序、因果窗口、标签、Profile 隔离、标准化、滚动历史、JSON、异常值和 GRU 保存重载，共 18 个测试用例。 |
| `src/test/java/ml/motor/FullTrainingWindowsTest.java` | 两个 1 Hz 相位的窗口枚举、连续性、复现和 GBDT 分阶段预测，共 5 个用例。 |
| `src/test/java/ml/motor/ForecastDiagnosticsTest.java` | 检查事后 Persistence 诊断不会覆盖已有全量训练对比报告。 |
| `src/test/java/ml/motor/PublishedModelsTest.java` | 校验发布的五个模型、配置及选择清单哈希，验证其样例输出与原始本地模型一致。 |
| `pom.xml` | Java 17 编译和 Smile、DJL、CSV、图表、MQTT、JSON、JUnit 依赖。各依赖均有当前调用者。 |
| `scripts/mvn.sh` | 选择本机 Java 17 和 Maven。 |
| `scripts/run-java.sh` | 编译、生成依赖 classpath，并启动指定 Java 主类。 |
| `scripts/add-forecast-user.sh` | 团队 broker 的 forecast 用户配置辅助脚本。 |
| `integration/forecast.properties` | 推理模型目录、broker 地址、阈值、机器容量和时间戳容差。 |
| `integration/forecast-flow.json` | Node-RED Dashboard 的 MQTT 预测接入模板。 |
| `integration/forecast-acl.conf` | 预测服务 MQTT 主题权限样例。 |

## 训练数据流

```mermaid
flowchart LR
    A[measures_v2.csv] --> B[MotorDataReader]
    B --> C[ProfileSplitter]
    C --> D[ForecastWindows]
    D --> E[MotorFullTrainingMain]
    E --> F[TreeHorizonModel]
    F --> G[ForecastEvaluation]
    G --> H[selection.properties 和模型文件]
    H --> I[MotorTrainingAuditMain]
    H --> J[ForecastModelRegistry]
    J --> K[RollingForecastService]
```
