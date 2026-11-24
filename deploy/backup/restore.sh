#!/usr/bin/env bash
# Restore a FraudShield backup (roadmap 9.1).
#
#   bash deploy/backup/restore.sh <file.dump>                 # restore drill: into a NEW scratch database, then
#                                                             # compares row counts of every table with the live DB
#   bash deploy/backup/restore.sh <file.dump> --into-live     # disaster recovery: OVERWRITES the live database
#                                                             # (stop the api first: docker compose ... stop api)
#
# Connection from the environment / deploy/.env.prod like pg_backup.sh (DB_URL, DB_USER, DB_PASSWORD,
# BACKUP_DOCKER_NETWORK). The scratch database is dropped at the end of a drill unless KEEP_SCRATCH=1.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${ENV_FILE:-$HERE/../.env.prod}"
if [ -f "$ENV_FILE" ]; then set -a; . "$ENV_FILE"; set +a; fi
dump="${1:?usage: restore.sh <file.dump> [--into-live]}"; mode="${2:-drill}"
[ -s "$dump" ] || { echo "no such dump: $dump"; exit 1; }
: "${DB_URL:?}"; : "${DB_USER:?}"; : "${DB_PASSWORD:?}"

rest="${DB_URL#jdbc:postgresql://}"; hostport="${rest%%/*}"; dbq="${rest#*/}"; db="${dbq%%\?*}"
host="${hostport%%:*}"; port="${hostport##*:}"; [ "$port" = "$hostport" ] && port=5432
sslmode=prefer; case "$DB_URL" in *sslmode=require*) sslmode=require;; *sslmode=verify-full*) sslmode=verify-full;; esac
net_args=(); [ -n "${BACKUP_DOCKER_NETWORK:-}" ] && net_args=(--network "$BACKUP_DOCKER_NETWORK")
pg() { docker run --rm -i "${net_args[@]}" -e PGPASSWORD="$DB_PASSWORD" -e PGSSLMODE="$sslmode" postgres:16 "$@"; }
psql_db() { local d="$1"; shift; pg psql -h "$host" -p "$port" -U "$DB_USER" -d "$d" -v ON_ERROR_STOP=1 -tAq "$@"; }

# row count of every table in the public schema (exact counts, small DB)
counts() {
    local d="$1"
    for t in $(psql_db "$d" -c "select tablename from pg_tables where schemaname='public' order by 1"); do
        echo "$t $(psql_db "$d" -c "select count(*) from public.\"$t\"")"
    done
}

if [ "$mode" = "--into-live" ]; then
    read -r -p "This OVERWRITES database '$db' on $host. Type the database name to continue: " ok
    [ "$ok" = "$db" ] || { echo "aborted"; exit 1; }
    pg pg_restore -h "$host" -p "$port" -U "$DB_USER" -d "$db" --clean --if-exists --no-owner --single-transaction < "$dump"
    echo "restored into $db - start the api again; Flyway will check the schema version"
    exit 0
fi

scratch="${SCRATCH_DB:-fraudshield_restore_test}"
psql_db "$db" -c "drop database if exists \"$scratch\"" >/dev/null
psql_db "$db" -c "create database \"$scratch\"" >/dev/null
pg pg_restore -h "$host" -p "$port" -U "$DB_USER" -d "$scratch" --no-owner --single-transaction < "$dump"
echo "restored $(basename "$dump") into scratch database $scratch"

live="$(counts "$db")"; restored="$(counts "$scratch")"
diff_out="$(diff <(echo "$live") <(echo "$restored") || true)"
tables=$(echo "$restored" | grep -c . || true); rows=$(echo "$restored" | awk '{s+=$2} END {print s+0}')
echo "tables: $tables, rows: $rows"
if [ -z "$diff_out" ]; then
    echo "RESTORE DRILL OK: every table has the same row count as the live database"
else
    echo "RESTORE DRILL: differences (live < > restored) - expected only for rows written after the backup:"
    echo "$diff_out"
fi
[ "${KEEP_SCRATCH:-0}" = "1" ] || psql_db "$db" -c "drop database \"$scratch\"" >/dev/null
