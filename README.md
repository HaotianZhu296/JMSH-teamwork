# Machine Overheating Detection

A real-time thermal anomaly detection system for industrial machines. The system streams simulated sensor data (temperature, rotational speed, torque, tool wear) through an MQTT broker to a detection service that applies rule-based thresholds and machine-learning anomaly scoring, raises severity-graded alerts, and persists events for review. Designed for maintenance technicians and reliability engineers who need continuous monitoring rather than manual spot-checks. Security controls follow NIST SP 800-82 Rev. 3.

**[Project Specs & Task Assignments (Google Docs)](https://docs.google.com/document/d/1hcGYfW0bl1EBTFzPu4H_aEc1DeIBGTN7JPLeuaUcVMo/edit?usp=sharing)**

## Team

| Member | GitHub Username | Role |
|---|---|---|
| Juntao Li | @Juntao-Li | Dataset survey and data pipeline |
| Mitchell Jindra | @mitchjindra413 | MQTT broker setup and streaming infrastructure |
| Shuo Yang | @ShuoYang-cs | dataset, sensor simulator, train/validation/test split |
| Haotian Zhu | @HaotianZhu296 | Anomaly detection, evaluation, and real-time pipeline |

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

# Stop all services
docker compose down
```

The broker requires authentication. Run `generate-passwords.sh` before `docker compose up`.

## Progress So Far

| Functional Requirement (from Phase 1) | Status | Notes |
|---|---|---|
| FR-1: Sensor simulator publishes dataset records as JSON over MQTT | Done | |
| FR-2: Broker authenticates clients over TLS with topic ACLs | Partial | Mosquitto config and Docker Compose in place; TLS and auth not yet enabled |
| FR-3: Detection service subscribes and validates messages | Done | |
| FR-4: Rule-based checks (absolute limit, rate of rise, persistence) | Done | |
| FR-5: ML anomaly score (Isolation Forest / EWMA) | Partial | Deterministic placeholder model |
| FR-6: Alert manager with severity and dedup | Done | |
| FR-7: Event store persists readings, scores, alerts | Done | |
| FR-8: Live dashboard with alert history | Done | Node-RED with chart, gauge, alert feed |
| FR-9: Offline evaluation (confusion matrix, precision, recall, F1) | Not started | |
| FR-10: Config-driven thresholds and alert settings | Done | |

**Working end to end:** Simulator → MQTT broker → detection service → alert publishing → dashboard.

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
| Shuo Yang | NIST SP 800-82 Rev. 3 review; security requirements analysis; Part A sensor simulator (Java, 1 Hz MQTT replay), message format, train/validation/test split, Docker integration testing |
| Haotian Zhu | Anomaly detection library survey; project scaffolding (Maven, .gitignore, README); integrated all four parts into unified pipeline; bug fixes across MQTT auth, Spring Integration, dashboard, model calibration; directory restructuring (simulator/, detection-alert/, dashboard/, training/) |

## Contributing

- Pull before you start working: `git pull`
- Keep commit messages short and descriptive
- Don't commit `.DS_Store`, build output, IDE settings, or secrets (see `.gitignore`)
