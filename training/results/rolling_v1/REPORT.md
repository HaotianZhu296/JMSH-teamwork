# 多 Horizon 电机温度预测开发与实验报告

执行日期：2026-11-07（America/Los_Angeles）。Java 17.0.21.0.2；DJL 0.39.0/PyTorch CPU 2.7.1；Smile 3.1.1；XChart 4.0.4；Apple Silicon。

## 结论与实验边界

1 秒预测在 validation 达到 MAE 0.055605°C；独立 test 为 0.059730°C。Persistence 本身已经低于 0.2°C（validation 0.086169°C），所以不能将达标本身解释为强预测能力。部署候选相对基线的 MAE 改善分别为 35.47% / 36.09%。两个训练种子均改善，validation 的 10 个 Profile 全部改善。
五个 Horizon 均选择 GBDT，而非强制部署 GRU。原有 RF、IF、V2、V4 保留。1/5 秒适合短期监控；10 秒提供额外提前量但 MAE 已高于 0.2°C；30/60 秒用于提前预警，不能理解为同等级精度或工业安全保证。

## 统一验证集结果

全部使用完全相同的 8000 个 validation 起点；预测 Celsius = 当前温度 + 模型 delta。改善率 = (Baseline MAE − Model MAE) / Baseline MAE × 100%。各 Horizon 独立训练。

| Horizon | Model | MAE °C | RMSE °C | Baseline MAE | Baseline RMSE | MAE 改善 | ±0.2°C 内 |
|---|---|---:|---:|---:|---:|---:|---:|
| 1s | GBDT_seed42 | 0.055605 | 0.101345 | 0.086169 | 0.155479 | 35.47% | 95.21% |
| 5s | GBDT_seed42 | 0.145787 | 0.263539 | 0.346959 | 0.639562 | 57.98% | 76.50% |
| 10s | GBDT_seed42 | 0.241490 | 0.431775 | 0.668405 | 1.219240 | 63.87% | 63.96% |
| 30s | GBDT_seed42 | 0.876021 | 1.620342 | 1.824525 | 3.292230 | 51.99% | 42.58% |
| 60s | GBDT_seed42 | 1.950163 | 3.617523 | 3.294472 | 5.935334 | 40.80% | 30.64% |

| Horizon | MSE °C² | P95 absolute error °C | Future ≥100°C count | High-temp MAE °C |
|---|---:|---:|---:|---:|
| 1s | 0.010271 | 0.194798 | 941 | 0.093445 |
| 5s | 0.069453 | 0.514704 | 939 | 0.246151 |
| 10s | 0.186430 | 0.908486 | 940 | 0.442206 |
| 30s | 2.625510 | 3.711180 | 950 | 1.491128 |
| 60s | 13.086471 | 7.904772 | 952 | 3.333989 |

完整的 30 个模型/基准结果在 `candidates.csv`。两次 GRU seed42/2026 MAE 分别为：1s 0.059659/0.058982；5s 0.154188/0.158121；10s 0.256124/0.270648；30s 0.904598/0.909467；60s 2.134695/2.099937。GBDT 两种子均优于对应 GRU。选择规则是种子平均 MAE 决定模型家族，再固定部署预先指定 seed42，不挑选幸运种子。

## 一次性独立测试

训练和选择冻结后，仅对 11 个 testing Profiles 的 19116 个窗口评估一次；未根据测试结果继续调参。`test.lock` 保存选择文件 SHA256。

| Horizon | Test MAE °C | Test RMSE °C | Baseline MAE | Baseline RMSE | MAE 改善 |
|---|---:|---:|---:|---:|---:|
| 1s | 0.059730 | 0.102674 | 0.093465 | 0.155413 | 36.09% |
| 5s | 0.175707 | 0.299206 | 0.391338 | 0.647301 | 55.10% |
| 10s | 0.293122 | 0.469233 | 0.740439 | 1.194691 | 60.41% |
| 30s | 1.075068 | 1.772761 | 1.991103 | 3.112985 | 46.01% |
| 60s | 2.433682 | 3.858264 | 3.486825 | 5.395826 | 30.20% |

## 数据、参数与公平性

- 原始数据 SHA256：`78f3d150f0f2ad9c5dc7ff24dd12c00d386ad530f48c1589ad24fcd88867d3ad`。使用完整 CSV、stride=2。
- 复用 ProfileSplitter seed42，48/10/11；与最新团队 `config/profile_split.csv` 一致。精确名单在 `profile_split.csv` 和 `experiment.properties`。
- 24000 training / 8000 validation；候选起点每 5 秒，固定随机抽样无放回。所有模型和 Horizon 共用起点，每个起点要求历史 61 条和最长未来标签所需序列均连续。
- GRU 61×13；hidden32，dense16；Adam0.001（区别于旧V4的0.0002）；batch128；最多18 epochs；patience4；MSE normalized delta；最佳 validation MAE checkpoint。统计仅由训练窗口拟合。
- GBDT 200 trees、depth6、leaves24、node_size10、shrinkage0.05、sampling0.8，LeastSquares delta。保留V2的30个因果特征并加入rate1、rate5、rate_acceleration。
- GRU 13维与现有 MotorGruFeatures 完全共用；树使用相同61条窗口的因果摘要。没有未来负载输入，没有跨Profile窗口，没有温度单位混淆。
- 两种子42/2026；训练窗口与验证窗口固定。Loss曲线的训练诊断为最多4096个训练窗口，验证为全部8000，曲线标明该范围。
- 原CSV没有时间戳：检查的是原始行索引，不能发现CSV导出之前已丢失的样本。实时同时检查传感器 timestampMs、sampleIndex 和 profileId。
- 历史V4/V2的指标来自不同验证采样，因此不宣称相对那些旧数字取得严格改善。没有重新训练旧RF/IF，也未开展完整超参数搜索或工业外部验证。

## 误差原因与可靠性

`validation_regimes.csv` 使用未来工况变化做事后诊断，不作为训练或推理输入：未来转速变化≥500或扭矩变化≥20（数据集单位）标记为load_change。60s MAE：变化组4.0943°C（2717个），稳定组0.8475°C（5283个）；30s为1.9150 vs 0.4413°C。这与不可知的未来负载变化导致长提前量误差增加一致，但不能单凭相关性证明因果。
Profile差异显著：60s Profile3 MAE0.1671°C，Profile53为3.8370°C；高温区域60s总体MAE3.3340°C，Profile36高温MAE7.9024°C。1s总体高温MAE0.0934°C，但Profile36高温MAE0.2213°C，不能声称每种工况都≤0.2°C。
相邻窗口高度重叠，因此没有把8000个窗口当作独立样本做显著性检验。`profile_bootstrap.csv` 以整个Profile为cluster重采样2000次、seed4242：1s绝对MAE改善的经验95%区间为[0.01840,0.04478]°C。仅有10个验证Profile，区间仍有局限。两个训练随机种子不能替代更多独立工况数据。60s GRU训练诊断MSE从约10.4降到6.9°C²，而验证MSE在约14.5°C²附近平台，符合过拟合与分布差异的表现；因此没有盲目增大网络。
60s的±0.2°C比例30.6375%，略低于Persistence的30.775%，即模型减少大误差不代表对几乎不变温度的每个小误差都更好。测试集60s MAE升至2.4337°C，表明仍存在跨Profile分布差异。

## 产物位置与图表

- `validation.csv`, `test.csv`, `candidates.csv`：MAE/RMSE/MSE/P95/±0.2比例/高温样本数/两类改善率。
- `profiles_validation_h*_GBDT_seed42.csv`, `profiles_test_h*_GBDT_seed42.csv`：逐Profile MAE/RMSE及Persistence对照。
- `predictions_validation_h*_GBDT_seed42.csv`, `predictions_test_h*_GBDT_seed42.csv`：逐样本起点、目标时刻、实际/预测/基线误差（本地保留，不建议提交普通Git）。
- `errors_*.csv/png`：有符号误差分布，首尾bin含overflow；`curve_*.png`：同一Profile的真实目标时刻曲线，不跨Profile连线。
- `validation_horizons.png`, `test_horizons.png`：多Horizon对比。
- `models_gru_h{1,5,10,30,60}_s{42,2026}/`：checkpoint、normalization.properties、validation_metrics.csv、loss.csv/png、reload_check.csv；loss也复制到顶层 `loss_gru_h*_s*.csv/png` 便于单独提交图表。
- `models_gbdt_h*_s*/`：独立GBDT模型、schema/参数和重载一致性检查；`selection.properties`：部署模型指针。
- `runtime/acceptance.txt`, `runtime/mqtt_trace.jsonl`, `runtime/dashboard_ready.png`, `runtime/dashboard_visible.txt`：真实链路证据。

## 实时数据路径与边界

现有 Simulator → MQTT sensors/<machine>/telemetry → Java RollingForecastService（每台61条缓存）→ forecasts/<machine> → Node-RED新增预测面板。模型在启动时加载一次；每条新记录只推理。各预测目标时间=本轮originTimestampMs+horizon×1000，下轮整体前移1秒。
WARMING_UP/缺失模型/非法值/推理失败返回nullable预测；原预测不充当新预测。界面5秒无更新显示STALE/unavailable。精确重复QoS1消息忽略；Profile、索引、时间戳不连续重置。缓存LRU最多128台；服务同步保护缓存，GRU同步保护native模型。
保留旧IF并真实计算anomalyScore，热状态与统计异常分开。配置沿用团队80/95°C当前阈值；30/60秒预测超过80°C且当前低于warning时产生预测预警。原ML目录100/120°C配置未覆盖。当前Spring阈值/风险评分/alerts和SQLite路径未改动，新增预警只在forecast面板展示，尚未写入Spring的alert数据库。
运行中确认：原Spring仍对真实数据发布报警并保存SQLite。其已有torque critical=1.0阈值和临时风险评分会让许多真实电机样本标CRITICAL；本次没有擅自校准该旧业务逻辑。界面分别标注“当前热状态”和原有综合alert feed，避免将旧风险评分误称为真实预测。

## 测试与运行证据

使用 Java17 + IntelliJ Maven。标准可复现命令：`./scripts/mvn.sh test`；训练/测试/回放命令见模块 README_ROLLING.md。实际调用的Maven为 `/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn`，JAVA_HOME指向ms-17.0.21.0.2。
- 新ML模块：18项JUnit，0失败/0错误/0跳过，含5个horizon参数化target测试、因果特征顺序、无split泄漏、每条连续性、标准化重载、机器隔离、预热/重置、非法输出、JSON null、缺失bundle和真实DJL生命周期。
- 最新team root：34/34；simulator：24/24（显式提供本地真实CSV链接后无跳过）；detection-alert：4/4。根pom不是聚合器，分别运行。
- 初次Spring上下文测试因SQLite父目录缺失失败，创建测试目录后重试。沙箱也阻止MQTT和Mockito attach，最终在获得工具授权后运行并通过；未修改组员测试来隐藏问题。
- 真实认证Mosquitto + ACL：65秒1Hz两机器，138条输出，warmup→READY、目标时间、真实IF、缺样/非法/Profile重置、40/85/105°C状态断言通过。
- 首轮10条READY输出（每条五模型+IF+服务逻辑）latency median3.571917ms，max21.091209ms；这是本机小规模推理延迟，不是完整MQTT→浏览器延迟，也不是吞吐量压测。
- 单独240条真实Profile79回放同时运行原Spring和Dashboard，浏览器确认模型值更新、实际值与预测标签、目标时刻以及原有alerts。
- Docker/TLS部署、大规模多机压力、未知Profile真实硬件、告警precision/recall与事件提前量分布均未验证。30/60秒温度预测评估不等同于报警系统召回率评估。

## Java文件清单

| 文件 | 作用 |
|---|---|
| ForecastWindows.java | 统一因果窗口、逐边连续性和 Horizon target 索引 |
| ForecastNormalizer.java | 仅训练窗口统计、标准化/反标准化、元数据校验 |
| HorizonForecaster.java | 可替换的 Horizon 推理接口 |
| GruHorizonModel.java | DJL GRU 离线训练、早停、批量评估、缓存推理和真实重载验证 |
| TreeHorizonModel.java | GBDT delta 模型和 V2 热状态特征扩展，独立保存/加载 |
| BaselineForecaster.java | Persistence 和过去 10 秒线性趋势基准 |
| ForecastEvaluation.java | 统一摄氏度指标、逐 Profile/高温评估、预测 CSV 和 XChart 图 |
| MotorHorizonExperimentMain.java | 固定划分和窗口、双种子模型比较、冻结选择、独立测试命令 |
| ForecastDiagnosticsMain.java | 按 Profile bootstrap 和未来负载变化的事后误差诊断 |
| ForecastModelRegistry.java | 按 Horizon 缓存模型并报告缺失模型 |
| ForecastTelemetry.java | 现有团队 MQTT schema 与 compact schema 的严格适配 |
| ForecastEnvelope.java | 实际值、目标时间、预测状态及 nullable 预测的输出协议 |
| RollingForecastService.java | 线程安全、机器隔离、61 条缓存、重置、阈值和 IF 推理 |
| MotorForecastMqttMain.java | MQTT 接入、独立发布客户端、防止 QoS1 死锁、模型只加载一次 |
| MotorForecastReplayMain.java | 真实验证 Profile 79 的 Java MQTT 回放 |
| MotorForecastE2EMain.java | 真实 broker/模型的 65 秒双机器验收 |
| ForecastPipelineTest.java | 18 项 JUnit 验证（含参数化 Horizon 与真实 DJL 生命周期） |

上述16个main类和1个测试类为本次新增。ML本地现有Java文件未重写。团队仓库另接收本地已有的 `MotorGruFeatures.java`，并同步现有 `MotorGruTrainMain.java` 的V4实现；这两个文件是开工前的本地代码，不是本次训练新模型的入口。其余原有motor类只从根src迁移至training/src，内容保留。
跨模块改动：`dashboard/flows.json`新增3节点；`config/acl.conf`新增forecast权限及dashboard读取权限；根README/.gitignore说明模块和忽略产物。Spring检测、simulator和存储Java代码不改动。

## Git提交准备

开工时ML目录为尚无提交的main且有大量A/AM文件，原暂存区未清理；team的data_training为干净0d86c4c。已fetch确认origin/main=3a630e9。只做本地集成，不自动commit或push。
已在team data_training执行普通 `merge --no-commit --no-ff origin/main`，保留MERGE_HEAD供你审查后提交，随后迁移ML到training。不要用 `git add .` 把原ML项目中的IDE暂存文件带入；在team仓库只添加明确文件。
建议提交：training/pom.xml、training/.gitignore、training/src、training/scripts、training/integration、training/README_ROLLING.md、报告/compact CSV/PNG，及根README/.gitignore、dashboard/flows.json、config/acl.conf、旧root motor路径的删除。models、数据集、target、IDE、test credentials和local运行目录均忽略。
模型分享建议：Release/外部artifact storage（IF约38MB，五个GBDT合计约1.6MB），附SHA256与selection/feature/config metadata；也可由团队明确采用Git LFS。本次未上传任何模型或数据。

最终移植回归：直接运行小组仓库 training/target/classes 与小组目录模型，再次完成65秒双机器138消息验收；READY延迟median2.483084ms、max18.252125ms，见 `runtime/final_team/acceptance.txt`。原Spring另对240条真实回放持久化240条记录；浏览器观察到其alerts继续刷新。完整Git命令见模块 `GIT_COMMANDS.md`。

验收结束后已关闭本次创建的隔离MQTT/预测/Spring/Node-RED服务，避免留下后台测试负载。截图和trace保留，启动步骤见README_ROLLING.md。最终源码测试记录：`artifact-final-team-tests.log`。
