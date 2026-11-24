#!/usr/bin/env bash
# FraudShield database backup (roadmap 9.1). Run daily from cron on the server, e.g.
#   15 2 * * *  cd /opt/fraudshield && bash deploy/backup/pg_backup.sh >> /var/log/fraudshield-backup.log 2>&1
#
# Connection comes from the environment (deploy/.env.prod is read if present): DB_URL (jdbc:postgresql://host:port/db?...),
# DB_USER, DB_PASSWORD. Runs pg_dump in a postgres:16 container, so the server needs no Postgres client installed.
# Output: $BACKUP_DIR/fraudshield-<UTC time>.dump  (custom format = already compressed; restore with restore.sh)
# Keeps $BACKUP_RETENTION_DAYS days. Optional off-site copy: BACKUP_S3_URI (+ BACKUP_S3_ENDPOINT, AWS_* from env).
# No credentials are written to disk or to the log.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${ENV_FILE:-$HERE/../.env.prod}"
if [ -f "$ENV_FILE" ]; then set -a; . "$ENV_FILE"; set +a; fi

: "${DB_URL:?DB_URL not set}"; : "${DB_USER:?DB_USER not set}"; : "${DB_PASSWORD:?DB_PASSWORD not set}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/fraudshield}"
RETENTION="${BACKUP_RETENTION_DAYS:-14}"
NETWORK="${BACKUP_DOCKER_NETWORK:-}"          # e.g. fraudshield_default when the DB is the compose "db" service

# jdbc:postgresql://host:port/db?params  ->  host / port / db / sslmode
rest="${DB_URL#jdbc:postgresql://}"
hostport="${rest%%/*}"; dbq="${rest#*/}"; db="${dbq%%\?*}"
host="${hostport%%:*}"; port="${hostport##*:}"; [ "$port" = "$hostport" ] && port=5432
sslmode=prefer; case "$DB_URL" in *sslmode=require*) sslmode=require;; *sslmode=verify-full*) sslmode=verify-full;; esac

mkdir -p "$BACKUP_DIR"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
out="$BACKUP_DIR/fraudshield-$stamp.dump"
tmp="$out.partial"

net_args=(); [ -n "$NETWORK" ] && net_args=(--network "$NETWORK")
docker run --rm "${net_args[@]}" -e PGPASSWORD="$DB_PASSWORD" -e PGSSLMODE="$sslmode" postgres:16 \
    pg_dump -h "$host" -p "$port" -U "$DB_USER" -d "$db" -Fc -Z 6 --no-owner > "$tmp"
# a dump must never be empty or truncated
[ -s "$tmp" ] || { echo "$(date -u +%FT%TZ) backup FAILED: empty dump"; rm -f "$tmp"; exit 1; }
docker run --rm -i postgres:16 pg_restore --list < "$tmp" > /dev/null || { echo "$(date -u +%FT%TZ) backup FAILED: unreadable dump"; rm -f "$tmp"; exit 1; }
mv "$tmp" "$out"
echo "$(date -u +%FT%TZ) backup ok: $out ($(du -h "$out" | cut -f1))"

if [ -n "${BACKUP_S3_URI:-}" ]; then
    ep=(); [ -n "${BACKUP_S3_ENDPOINT:-}" ] && ep=(--endpoint-url "$BACKUP_S3_ENDPOINT")
    docker run --rm -v "$BACKUP_DIR":/b:ro -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION \
        amazon/aws-cli:2.22.0 s3 cp "/b/$(basename "$out")" "${BACKUP_S3_URI%/}/" "${ep[@]}" --only-show-errors
    echo "$(date -u +%FT%TZ) copied off-site to ${BACKUP_S3_URI%/}/"
fi

# retention (local copies; set a lifecycle rule on the bucket for the off-site ones)
find "$BACKUP_DIR" -maxdepth 1 -name 'fraudshield-*.dump' -mtime +"$RETENTION" -print -delete | sed 's/^/removed old backup: /'
