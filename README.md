# Machine Overheating Detection

A real-time thermal anomaly detection system for industrial machines. The system streams simulated sensor data (temperature, rotational speed, torque, tool wear) through an MQTT broker to a detection service that applies rule-based thresholds and machine-learning anomaly scoring, raises severity-graded alerts, and persists events for review. Designed for maintenance technicians and reliability engineers who need continuous monitoring rather than manual spot-checks. Security controls follow NIST SP 800-82 Rev. 3.

**[Project Specs & Task Assignments (Google Docs)](https://docs.google.com/document/d/1hcGYfW0bl1EBTFzPu4H_aEc1DeIBGTN7JPLeuaUcVMo/edit?usp=sharing)**

## Team

| Member | GitHub Username | Role                                                   |
|---|---|--------------------------------------------------------|
| Juntao Li | @Juntao-Li | Dataset survey and data pipeline                       |
| Mitchell Jindra | @mitchjindra413 | MQTT broker setup, Docker and detection-alert service  |
| Shuo Yang | @ShuoYang-cs | dataset, sensor simulator, train/validation/test split |
| Haotian Zhu | @HaotianZhu296 | Anomaly detection, evaluation, and real-time pipeline  |

## System Architecture

**Figure 1: End-to-End Pipeline**

```
┌──────────────┐     MQTT      ┌──────────────┐     MQTT      ┌──────────────┐
│   Simulator  │──telemetry──▶│   Mosquitto   │──telemetry──▶│  Detection   │
│   (Part A)   │   sensors/   │    Broker     │   sensors/   │   Service    │
│              │  +/telemetry │  (auth+ACL)   │  +/telemetry │   (Part C)   │
└──────────────┘              └──────┬────────┘              └──────┬───────┘
                                     │                              │
                                     │  MQTT alerts/{machineId}     │
                                     │◀─────────────────────────────┘
                                     │
                              ┌──────▼────────┐              ┌──────────────┐
                              │   Dashboard   │              │  Event Store │
                              │  (Node-RED)   │              │   (Part D)   │
                              │  charts+alerts│              │   SQLite     │
                              └───────────────┘              └──────────────┘
```

Sensor readings flow left-to-right through the MQTT broker. The detection service evaluates each reading against rule-based thresholds and a predictive model, then publishes alerts back to the broker. The dashboard subscribes to both telemetry and alert topics for live visualization. The event store persists readings, scores, and alerts for offline analysis.

## Performance and Detection Targets

<!-- TODO: temporarily added, not fully tested — targets pending offline evaluation -->

| Metric | Target | Notes |
|---|---|---|
| Precision | ≥ 0.85 | Minimize false alarms for maintenance technicians |
| Recall | ≥ 0.90 | Catch at least 90% of real overheating events |
| F1 Score | ≥ 0.87 | Balanced harmonic mean |
| Detection latency | ≤ 2 s | From sensor reading to alert decision |
| End-to-end latency | ≤ 5 s | From simulator publish to dashboard display |
| False positive rate | ≤ 10% | Acceptable operational noise floor |

These targets are configured in `detection-alert/src/main/resources/application.yaml` under `performance-targets` and enforced by `MetricsCollector`. Offline evaluation with confusion matrix reporting (FR-9) will validate these against the held-out test split.

## Repository Structure

```
JMSH-teamwork/
  config/              Mosquitto broker configuration (ACL, passwords)
  dashboard/           Node-RED live dashboard (Dockerfile, flows, settings)
  detection-alert/     Spring Boot detection & alerting service (Part C)
  simulator/           Sensor data simulator over MQTT (Part A)
  training/            ML model training & evaluation (Part B)
  data/                Electric Motor Temperature dataset (download separately)
  src/                 Event store, security, dashboard data provider (Part D)
  docker-compose.yml   Full-stack orchestration
```

## Setup and Run

**Prerequisites:** Java 17+, Maven (wrapper included), Docker

```bash
# Clone
git clone https://github.com/HaotianZhu296/JMSH-teamwork.git
cd JMSH-teamwork

# Generate MQTT broker passwords (requires mosquitto_passwd)
./config/generate-passwords.sh

# Download the Electric Motor Temperature dataset from Kaggle
# Place measures_v2.csv in data/

# Start all services (broker, dashboard, detection, simulator)
docker compose up -d --build

# Or run the simulator locally instead of in Docker:
cd simulator
mvn clean compile
MQTT_USERNAME=simulator MQTT_PASSWORD=sim_pass_2024 \
  mvn -q exec:java -Dexec.mainClass="edu.northeastern.cs5010.mhd.pipeline.SensorSimulator"

# Run event store CLI (import/query readings, scores, alerts)
cd ..
./mvnw -q exec:java -Dexec.mainClass="edu.northeastern.cs5010.mhd.store.EventStoreMain" \
  -Dexec.args="summary events.db"

# Run all tests
./mvnw clean test              # root module (Part D event store, security)
cd simulator && mvn clean test && cd ..   # simulator (Part A)
cd detection-alert && ./mvnw clean test && cd ..  # detection-alert (Part C, needs MQTT broker running)

# Stop all services
docker compose down
```

The broker requires authentication. Run `generate-passwords.sh` before `docker compose up`.

## Progress So Far

| Functional Requirement (from Phase 1) | Status | Notes |
|---|---|---|
| FR-1: Sensor simulator publishes dataset records as JSON over MQTT | Done | |
| FR-2: Broker authenticates clients with topic ACLs | Done | Password auth and per-topic ACLs enforced; TLS planned for Phase 3 |
| FR-3: Detection service subscribes and validates messages | Done | |
| FR-4: Rule-based checks (absolute limit, rate of rise, persistence) | Done | |
| FR-5: ML anomaly score (Isolation Forest / EWMA) | Partial | Deterministic placeholder model |
| FR-6: Alert manager with severity and dedup | Done | |
| FR-7: Event store persists readings, scores, alerts | Done | |
| FR-8: Live dashboard with alert history (core) | Done | Node-RED with chart, gauge, alert feed; moved from stretch to core |
| FR-9: Offline evaluation (confusion matrix, precision, recall, F1) | Partial | MetricsCollector added with concrete targets; full evaluation pending |
| FR-10: Config-driven thresholds and alert settings | Done | |

**Working end to end:** Simulator → MQTT broker → detection service → alert publishing → dashboard.

## Testing So Far

- **Framework:** JUnit 5 with JaCoCo coverage
- **Root module (Part D):** 34 tests — EventStore, CsvImporter, BrokerAclValidator, DashboardDataProvider
- **Simulator (Part A):** 24 tests (1 skipped) — MotorDataReader, OverheatScenario, SensorSimulator, TelemetryCsv, TelemetryJson
- **Detection-alert (Part C):** 4 tests — DetectionService threshold/model evaluation, Spring context load
- **Total: 62 tests, all passing**

## Next Steps (Phase 3)

- Build offline evaluation runner with confusion matrix reporting against performance targets
- Add TLS encryption to the MQTT broker (auth and ACLs already in place)
- Complete NIST SP 800-82 threat model and control mapping
- Run two optimization rounds with before/after metrics
- Validate detection latency (≤ 2 s) and end-to-end latency (≤ 5 s) under load

## Team Contributions

| Member | Contributions This Phase |
|---|---|
| Juntao Li | Dataset survey and selection; Java-based ML development (Isolation Forest, Random Forest, GBDT, GRU); temperature forecasting and early-warning detection; feature engineering, model training, optimization, evaluation (MAE/RMSE, Precision/Recall); model serialization, loss visualization, and real-time inference integration |
| Mitchell Jindra | Detection-Alert Service; Mosquitto broker setup; Docker Compose configuration |
| Shuo Yang | NIST SP 800-82 Rev. 3 review; security requirements analysis; Part A sensor simulator (Java, 1 Hz MQTT replay), message format, train/validation/test split, Docker integration testing |
| Haotian Zhu | Anomaly detection library survey; project scaffolding (Maven, .gitignore, README); integrated all four parts into unified pipeline; bug fixes across MQTT auth, Spring Integration, dashboard, model calibration; directory restructuring (simulator/, detection-alert/, dashboard/, training/); added architecture diagram, performance targets, and MetricsCollector |

## Contributing

- Pull before you start working: `git pull`
- Keep commit messages short and descriptive
- Don't commit `.DS_Store`, build output, IDE settings, or secrets (see `.gitignore`)
