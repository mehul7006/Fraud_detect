#!/usr/bin/env bash
# FraudShield uptime / health monitor (roadmap 9.1). Run every 5 minutes from cron, ideally from a SECOND machine
# (a monitor on the same server cannot report that the server is down):
#   */5 * * * *  bash /opt/fraudshield/deploy/monitor/check.sh >> /var/log/fraudshield-monitor.log 2>&1
#
# Checks: public health endpoint (HTTPS, through Caddy), response time, TLS certificate days left, and - when run on
# the server - disk usage and age of the newest backup. Alerts go to ALERT_WEBHOOK_URL (JSON {"text": ...}; works
# with Slack incoming webhooks and Telegram sendMessage?chat_id=...). Alerts are sent only when the state CHANGES
# (problem -> one alert, recovered -> one "resolved" message), so a long outage does not flood the channel.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${ENV_FILE:-$HERE/../.env.prod}"
if [ -f "$ENV_FILE" ]; then set -a; . "$ENV_FILE"; set +a; fi

URL="${MONITOR_URL:-https://${DOMAIN:?DOMAIN or MONITOR_URL must be set}/actuator/health}"
CURL_EXTRA=(); [ "${MONITOR_INSECURE:-0}" = "1" ] && CURL_EXTRA=(-k)   # local staging only (internal CA)
MAX_MS="${MONITOR_MAX_MS:-3000}"
CERT_MIN_DAYS="${MONITOR_CERT_MIN_DAYS:-14}"
DISK_MAX_PCT="${MONITOR_DISK_MAX_PCT:-85}"
BACKUP_MAX_HOURS="${MONITOR_BACKUP_MAX_HOURS:-26}"
STATE="${MONITOR_STATE_FILE:-/tmp/fraudshield-monitor.state}"

problems=()

# 1. health through the public URL
body="$(curl -sS "${CURL_EXTRA[@]}" -m 10 -o - -w '\n%{http_code} %{time_total}' "$URL" 2>&1)"
code="$(echo "$body" | tail -1 | cut -d' ' -f1)"; secs="$(echo "$body" | tail -1 | cut -d' ' -f2)"
ms="$(awk -v s="${secs:-0}" 'BEGIN {printf "%d", s*1000}')"
if [ "$code" != "200" ] || ! echo "$body" | grep -q '"UP"'; then
    problems+=("health check failed: HTTP ${code:-none} from $URL")
elif [ "$ms" -gt "$MAX_MS" ]; then
    problems+=("health check slow: ${ms} ms (limit ${MAX_MS} ms)")
fi

# 2. TLS certificate expiry (only for https URLs with a real certificate)
if [[ "$URL" == https://* ]] && [ "${MONITOR_INSECURE:-0}" != "1" ]; then
    hostport="${URL#https://}"; hostport="${hostport%%/*}"; h="${hostport%%:*}"; p="${hostport##*:}"; [ "$p" = "$hostport" ] && p=443
    end="$(echo | openssl s_client -servername "$h" -connect "$h:$p" 2>/dev/null | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2)"
    if [ -n "$end" ]; then
        days=$(( ( $(date -d "$end" +%s) - $(date +%s) ) / 86400 ))
        [ "$days" -lt "$CERT_MIN_DAYS" ] && problems+=("TLS certificate expires in $days days")
    else
        problems+=("could not read the TLS certificate of $h")
    fi
fi

# 3. disk + newest backup (only meaningful on the server itself)
if [ "${MONITOR_LOCAL_CHECKS:-1}" = "1" ]; then
    pct="$(df -P "${BACKUP_DIR:-/}" 2>/dev/null | awk 'NR==2 {gsub("%","",$5); print $5}')"
    [ -n "$pct" ] && [ "$pct" -gt "$DISK_MAX_PCT" ] && problems+=("disk ${pct}% full")
    if [ -n "${BACKUP_DIR:-}" ]; then
        newest="$(ls -1t "$BACKUP_DIR"/fraudshield-*.dump 2>/dev/null | head -1)"
        if [ -z "$newest" ]; then
            problems+=("no backup found in $BACKUP_DIR")
        else
            age_h=$(( ( $(date +%s) - $(stat -c %Y "$newest") ) / 3600 ))
            [ "$age_h" -gt "$BACKUP_MAX_HOURS" ] && problems+=("newest backup is ${age_h} h old")
        fi
    fi
fi

alert() {
    local text="$1"
    echo "$(date -u +%FT%TZ) ALERT: $text"
    if [ -n "${ALERT_WEBHOOK_URL:-}" ]; then
        local json; json="$(printf '%s' "$text" | sed 's/\\/\\\\/g; s/"/\\"/g')"
        curl -sS -m 10 -X POST -H 'Content-Type: application/json' -d "{\"text\": \"$json\"}" "$ALERT_WEBHOOK_URL" > /dev/null \
            || echo "$(date -u +%FT%TZ) could not reach the alert webhook"
    fi
}

prev="$(cat "$STATE" 2>/dev/null || echo OK)"
if [ ${#problems[@]} -eq 0 ]; then
    echo "$(date -u +%FT%TZ) OK health ${ms} ms"
    [ "$prev" != "OK" ] && alert "FraudShield RESOLVED: all checks OK again ($URL)"
    echo OK > "$STATE"
    exit 0
fi
now="$(printf '%s; ' "${problems[@]}")"
[ "$prev" != "$now" ] && alert "FraudShield PROBLEM: $now"
echo "$now" > "$STATE"
exit 1
