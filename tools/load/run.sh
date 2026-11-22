#!/usr/bin/env bash
# 8.7: start a throwaway backend + DB (port 18081, no external API keys, rate limits raised), run k6, clean up.
set -euo pipefail
export MSYS_NO_PATHCONV=1
R="$(cd "$(dirname "$0")/../.." && pwd)"; W=$(cygpath -m "$R" 2>/dev/null || echo "$R")
docker rm -f fs-load-api fs-load-db >/dev/null 2>&1 || true
docker network create fsload >/dev/null 2>&1 || true
docker run -d --name fs-load-db --network fsload -e POSTGRES_DB=fraudshield -e POSTGRES_USER=fraud -e POSTGRES_PASSWORD=fraud postgres:16 >/dev/null
sleep 6
docker run -d --name fs-load-api --network fsload -p 18081:8080 --cpus 2 --memory 1g \
  -e DB_URL=jdbc:postgresql://fs-load-db:5432/fraudshield -e SAFE_BROWSING_KEY= -e VT_ENABLED=false \
  -e RATE_LIMIT_PER_MINUTE=1000000 -e RATE_LIMIT_AUTH_PER_MINUTE=1000000 \
  -v "$W/backend/target":/app -w /app eclipse-temurin:21-jre java -jar fraudshield-backend-0.1.0.jar >/dev/null
for i in $(seq 1 60); do [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:18081/api/v1/config/carriers)" = 401 ] && break; sleep 2; done
# warm-up: 30 s at 100/s so the JVM has compiled the hot paths (a cold JVM queues up and the numbers mean nothing)
docker run --rm -v "$W/tools/load":/s -e RATE=100 -e DURATION=30s grafana/k6:0.54.0 run -q /s/scan_url.js >/dev/null 2>&1 || true
docker run --rm -v "$W/tools/load":/s grafana/k6:0.54.0 run --summary-trend-stats "avg,p(50),p(95),p(99),max" /s/scan_url.js
rc=$?
[ "${KEEP:-0}" = 1 ] || { docker rm -f fs-load-api fs-load-db >/dev/null; docker network rm fsload >/dev/null; }
exit $rc
