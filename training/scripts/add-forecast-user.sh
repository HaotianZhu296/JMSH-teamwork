#!/usr/bin/env bash
# Run from the team repository root, after its existing password-file setup.
set -euo pipefail
: "${FORECAST_MQTT_PASSWORD:?Set FORECAST_MQTT_PASSWORD before creating the forecast account}"
test -f config/pwfile
mosquitto_passwd -b config/pwfile forecast "$FORECAST_MQTT_PASSWORD"
echo 'Forecast account added. Restart the broker to reload its password file and ACL.'
