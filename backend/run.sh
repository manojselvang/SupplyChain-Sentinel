#!/usr/bin/env bash
# Convenience script: loads .env and starts the backend.
# Usage:  ./run.sh
set -a
source "$(dirname "$0")/.env"
set +a

java -jar "$(dirname "$0")/target/supplychain-sentinel-backend-0.1.0.jar" "$@"
