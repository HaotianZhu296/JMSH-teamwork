#!/usr/bin/env bash
# Generate the Mosquitto password file with default dev credentials.
# Usage: ./config/generate-passwords.sh
# Requires: mosquitto_passwd (installed with Mosquitto)

set -euo pipefail

PWFILE="config/pwfile"

echo "=== Generating Mosquitto password file ==="

# Create file with first user, then append the rest
mosquitto_passwd -c -b "$PWFILE" simulator sim_pass_2024
mosquitto_passwd    -b "$PWFILE" detector  det_pass_2024
mosquitto_passwd    -b "$PWFILE" eventstore evt_pass_2024
mosquitto_passwd    -b "$PWFILE" dashboard  dash_pass_2024

echo "Password file written to $PWFILE"
echo "Users: simulator, detector, eventstore, dashboard"
echo ""
echo "WARNING: Change these passwords before any non-local deployment."
