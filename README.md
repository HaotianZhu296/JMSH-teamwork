# Machine Overheating Detection

A real-time thermal anomaly detection system for industrial machines. The system streams simulated sensor data (temperature, rotational speed, torque, tool wear) through an MQTT broker to a detection service that applies rule-based thresholds and machine-learning anomaly scoring, raises severity-graded alerts, and persists events for review. Designed for maintenance technicians and reliability engineers who need continuous monitoring rather than manual spot-checks. Security controls follow NIST SP 800-82 Rev. 3.

**[Project Specs & Task Assignments (Google Docs)](https://docs.google.com/document/d/1hcGYfW0bl1EBTFzPu4H_aEc1DeIBGTN7JPLeuaUcVMo/edit?usp=sharing)**

## Team

| Member | GitHub Username | Role |
|---|---|---|
| Juntao Li | @Juntao-Li | Dataset survey and data pipeline |
| Mitchell Jindra | @mitchjindra413 | MQTT broker setup and streaming infrastructure |
| Shuo Yang | @ShuoYang-cs | NIST SP 800-82 security review and controls |
| Haotian Zhu | @HaotianZhu296 | Anomaly detection, evaluation, and real-time pipeline |

## Repository Structure

```
JMSH-teamwork/
  config/              Mosquitto broker configuration
  data/                AI4I 2020 Predictive Maintenance dataset
  docs/                Survey reports, evaluation reports
  src/main/java/       Java source (detection, evaluation, pipeline, models)
    detector/          Detector interface, ThresholdDetector, IsolationForest, EWMA
    evaluation/        ConfusionMatrix, DatasetLoader, EvaluationRunner
    model/             SensorReading, DetectorResult
    pipeline/          SensorSimulator, DetectionService, AlertManager, PipelineMain
    store/             EventStore (SQLite)
  src/main/resources/  detector-config.properties
  src/test/java/       JUnit 5 test suites
  docker-compose.yml   Mosquitto broker (Docker)
```

## Setup and Run

**Prerequisites:** Java 17+, Maven (wrapper included), Docker

```bash
# Clone
git clone https://github.com/HaotianZhu296/JMSH-teamwork.git
cd JMSH-teamwork

# Build and run tests
./mvnw clean test

# Start the MQTT broker
docker compose up -d

# Run the offline evaluation (threshold + ML detectors on the full dataset)
./mvnw -q exec:java -Dexec.mainClass="edu.northeastern.cs5010.mhd.evaluation.EvaluationRunner"

# Run the real-time pipeline (simulator → MQTT → detection → alerts → SQLite)
./mvnw -q exec:java -Dexec.mainClass="edu.northeastern.cs5010.mhd.pipeline.PipelineMain"

# Stop the broker
docker compose down
```

Pipeline configuration via system properties:

| Property | Default | Description |
|----------|---------|-------------|
| `mqtt.broker` | `tcp://localhost:1883` | MQTT broker URL |
| `data.file` | `data/ai4i2020.csv` | Sensor data CSV |
| `data.limit` | `200` | Max readings to publish (0 = all) |
| `sim.interval` | `100` | Milliseconds between readings |
| `db.path` | `events.db` | SQLite database file |

No secrets are needed for local development; the broker runs with anonymous access by default.

## Progress So Far

| Functional Requirement (from Phase 1) | Status | Notes |
|---|---|---|
| FR-1: Sensor simulator publishes dataset records as JSON over MQTT | Not started | |
| FR-2: Broker authenticates clients over TLS with topic ACLs | Partial | Mosquitto config and Docker Compose in place; TLS and auth not yet enabled |
| FR-3: Detection service subscribes and validates messages | Not started | |
| FR-4: Rule-based checks (absolute limit, rate of rise, persistence) | Not started | |
| FR-5: ML anomaly score (Isolation Forest / EWMA) | Not started | |
| FR-6: Alert manager with severity and dedup | Not started | |
| FR-7: Event store persists readings, scores, alerts | Not started | |
| FR-8: Live dashboard with alert history | Not started | Planned for Phase 3 |
| FR-9: Offline evaluation (confusion matrix, precision, recall, F1) | Not started | |
| FR-10: Config-driven thresholds and alert settings | Not started | |

**Working end to end:** The MQTT broker runs via Docker Compose and accepts connections. Core detection and pipeline code is under development.

## Testing So Far

- **Framework:** JUnit 5 with JaCoCo coverage
- Tests will be added alongside core components

## Next Steps (Phase 3)

- Implement and integrate detection pipeline end to end (simulator → broker → detector → alerts → event store)
- Build offline evaluation runner with confusion matrix reporting
- Add TLS encryption and per-topic ACLs to the MQTT broker
- Build a live dashboard (Node-RED or web-based) for alerts and temperature trends
- Complete NIST SP 800-82 threat model and control mapping
- Run two optimization rounds with before/after metrics

## Team Contributions

| Member | Contributions This Phase |
|---|---|
| Juntao Li | Dataset survey (AI4I 2020, CWRU, NASA bearing); dataset comparison and selection |
| Mitchell Jindra | Streaming tools survey; Mosquitto broker setup; Docker Compose configuration |
| Shuo Yang | NIST SP 800-82 Rev. 3 review; security requirements analysis |
| Haotian Zhu | Anomaly detection library survey; project scaffolding (Maven, .gitignore, README) |

## Contributing

- Pull before you start working: `git pull`
- Keep commit messages short and descriptive
- Don't commit `.DS_Store`, build output, IDE settings, or secrets (see `.gitignore`)
