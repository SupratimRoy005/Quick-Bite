#!/usr/bin/env bash
set -e
export PORT="${PORT:-8080}"
if [ -z "${STAFF_PIN:-}" ]; then export STAFF_PIN="change-me"; fi
export DATA_DIR="${DATA_DIR:-.}"
echo "Starting QuickBite on http://localhost:${PORT}"
exec java Server.java
