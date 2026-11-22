#!/usr/bin/env bash
# 8.6: dependency vulnerability scan (OSV-Scanner, free, no key). Backend: exact resolved libraries via a CycloneDX
# SBOM (runtime scope); app: Flutter pubspec.lock. Exit code != 0 when an unignored vulnerability is found.
set -euo pipefail
R="$(cd "$(dirname "$0")/../.." && pwd)"; OUT="$R/_devtools/security"; mkdir -p "$OUT"
export MSYS_NO_PATHCONV=1
W=$(cygpath -m "$R" 2>/dev/null || echo "$R"); O=$(cygpath -m "$OUT" 2>/dev/null || echo "$OUT")
docker run --rm -v "$W":/repo:ro -v "$O":/out -v m2cache:/root/.m2 maven:3.9-eclipse-temurin-21 sh -c \
  'cp -r /repo/backend /w && cd /w && rm -rf target && mvn -B -ntp -q org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeBom -DincludeTestScope=false -DoutputFormat=json -DoutputName=bom && cp target/bom.json /out/backend-bom.json'
docker run --rm -v "$W":/src:ro ghcr.io/google/osv-scanner:v1.9.2 --config /src/tools/security/osv-scanner.toml \
  --format table --sbom /src/_devtools/security/backend-bom.json -L /src/mobile/pubspec.lock
