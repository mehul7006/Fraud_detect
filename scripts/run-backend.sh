#!/usr/bin/env bash
# Starts PostgreSQL + the backend in Docker on http://localhost:18080, reading every secret from ../.env
# Usage: bash scripts/run-backend.sh [stop]
set -euo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

docker rm -f fs-api fs-db >/dev/null 2>&1 || true
[ "${1:-}" = "stop" ] && { docker network rm fsnet >/dev/null 2>&1 || true; echo stopped; exit 0; }
[ -f .env ] || { echo "Missing .env - copy .env.example to .env and fill it in"; exit 1; }

docker network create fsnet >/dev/null 2>&1 || true
docker run -d --name fs-db --network fsnet -e POSTGRES_DB=fraudshield -e POSTGRES_USER=fraud \
  -e POSTGRES_PASSWORD=fraud postgres:16 >/dev/null
sleep 6
docker run -d --name fs-api --network fsnet -p 18080:8080 --env-file .env \
  -e DB_URL=jdbc:postgresql://fs-db:5432/fraudshield -v "$PWD/backend/target":/app -w /app \
  eclipse-temurin:21-jre java -jar fraudshield-backend-0.1.0.jar >/dev/null
echo "backend starting on http://localhost:18080 (secrets loaded from .env)"
