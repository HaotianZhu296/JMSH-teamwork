#!/usr/bin/env bash
# Generate self-signed TLS certificates for the Mosquitto broker.
# Usage: ./config/generate-certs.sh [output_dir]
# Default output: config/certs/

set -euo pipefail

OUTDIR="${1:-config/certs}"
DAYS=365
SUBJ_CA="/CN=MHD-CA"
SUBJ_SERVER="/CN=localhost"

mkdir -p "$OUTDIR"

echo "=== Generating CA key and certificate ==="
openssl genrsa -out "$OUTDIR/ca.key" 2048
openssl req -new -x509 -days "$DAYS" -key "$OUTDIR/ca.key" \
    -out "$OUTDIR/ca.crt" -subj "$SUBJ_CA"

echo "=== Generating server key and certificate ==="
openssl genrsa -out "$OUTDIR/server.key" 2048
openssl req -new -key "$OUTDIR/server.key" \
    -out "$OUTDIR/server.csr" -subj "$SUBJ_SERVER"
openssl x509 -req -in "$OUTDIR/server.csr" -CA "$OUTDIR/ca.crt" \
    -CAkey "$OUTDIR/ca.key" -CAcreateserial \
    -out "$OUTDIR/server.crt" -days "$DAYS"
rm -f "$OUTDIR/server.csr" "$OUTDIR/ca.srl"

echo "=== Certificates generated in $OUTDIR ==="
echo "To enable TLS, uncomment the TLS section in config/mosquitto.conf"
echo "and mount $OUTDIR as /mosquitto/certs in Docker."
