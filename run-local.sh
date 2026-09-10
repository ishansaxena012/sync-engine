#!/usr/bin/env bash
# Loads .env into the environment and starts the backend. Run from the project root:
#   ./run-local.sh
set -e

if [ ! -f .env ]; then
  echo ".env not found. Copy .env.example to .env and fill in real values first." >&2
  exit 1
fi

set -a
source .env
set +a

./mvnw spring-boot:run
