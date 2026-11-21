#!/usr/bin/env bash
# Download the Electric Motor Temperature dataset (measures_v2.csv) from Kaggle.
# Usage: ./scripts/download-dataset.sh [target-dir]      (default target-dir: data)
# Set FORCE=1 to download again even if the file exists.
# Needs curl and unzip. The download is about 120 MB; the CSV is about 300 MB.
#
# This uses Kaggle's public download URL, which did not ask for a login when this script was written.
# If Kaggle changes that, download the file by hand from
# https://www.kaggle.com/datasets/wkirgsn/electric-motor-temperature and put measures_v2.csv in the target dir.

set -euo pipefail

TARGET_DIR="${1:-data}"
URL="${DATASET_URL:-https://www.kaggle.com/api/v1/datasets/download/wkirgsn/electric-motor-temperature}"
FILE="measures_v2.csv"
EXPECTED_LINES=1330817   # header + 1,330,816 rows
EXPECTED_HEADER="u_q,coolant,stator_winding,u_d,stator_tooth,motor_speed,i_d,i_q,pm,stator_yoke,ambient,torque,profile_id"

for tool in curl unzip; do
    command -v "$tool" >/dev/null || { echo "error: '$tool' is required" >&2; exit 1; }
done

verify() {
    local csv="$1"
    [ "$(head -n 1 "$csv" | tr -d '\r')" = "$EXPECTED_HEADER" ] || return 1
    [ "$(wc -l < "$csv" | tr -d ' ')" = "$EXPECTED_LINES" ] || return 1
}

mkdir -p "$TARGET_DIR"
if [ -f "$TARGET_DIR/$FILE" ] && [ "${FORCE:-0}" != "1" ]; then
    if verify "$TARGET_DIR/$FILE"; then
        echo "$TARGET_DIR/$FILE already exists and looks correct. Use FORCE=1 to download again."
        exit 0
    fi
    echo "warning: $TARGET_DIR/$FILE exists but does not look like the expected file; downloading again" >&2
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "Downloading from Kaggle (about 120 MB) ..."
curl -fL --retry 3 --progress-bar -o "$WORK/archive.zip" "$URL" || {
    echo "error: download failed. Download $FILE by hand (see the top of this script)." >&2
    exit 1
}

unzip -o -q "$WORK/archive.zip" "$FILE" -d "$WORK" || {
    echo "error: the download is not the expected zip (it may be a login page)." >&2
    exit 1
}

verify "$WORK/$FILE" || {
    echo "error: $FILE does not match the expected header or row count (${EXPECTED_LINES} lines)." >&2
    exit 1
}

mv "$WORK/$FILE" "$TARGET_DIR/$FILE"
echo "Saved $TARGET_DIR/$FILE"
