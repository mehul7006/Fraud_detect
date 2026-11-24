# FraudShield – production deploy (roadmap 9.1)

One server with Docker runs the API behind **Caddy**, which gets and renews the HTTPS certificate by itself.
The database is either a **managed Postgres** (recommended: backups, updates and failover handled by the provider)
or Postgres on the same server (`--profile localdb`).

```
phone ──HTTPS──► Caddy :443 ──► api :8080 (Spring Boot, not published) ──► Postgres (managed, TLS)
                   :80 → redirect to HTTPS        │
                                                  ├─ /keys        (signing key, Firebase file; read-only)
                                                  └─ /app/blocklists (Phase 18 lists; read-only)
```

| File | What it is |
|---|---|
| `Dockerfile` | multi-stage build → JRE 21 image, non-root user (uid 10001), health check on `/actuator/health` |
| `docker-compose.prod.yml` | `api` + `caddy` (+ `db` with `--profile localdb`); only ports 80/443 are opened; log rotation |
| `caddy/Caddyfile` | HTTPS for `$DOMAIN`, HSTS + security headers; serves only `/api/*` and `/actuator/health` |
| `caddy/Caddyfile.staging` | the same with Caddy's internal CA (local staging) |
| `.env.prod.example` | every setting (names only) – copy to `.env.prod` (git-ignored) |
| `backup/pg_backup.sh` | daily `pg_dump` (compressed custom format), retention, optional off-site copy (S3 / R2 / B2) |
| `backup/restore.sh` | restore drill into a scratch DB + row-count comparison, or full restore into the live DB |
| `monitor/check.sh` | health, response time, TLS expiry, disk, backup age → alert webhook (Slack / Telegram), only on change |

## 1. What you need (owner)

1. **A server**: Ubuntu 22.04/24.04 VPS, at least **2 GB RAM** (1 vCPU is enough to start), 20 GB disk,
   in India for low latency (e.g. Mumbai / Bangalore region of any provider).
2. **A domain name** for the API, e.g. `api.fraudshield.in`, with an **A record** (and AAAA if the server has IPv6)
   pointing at the server's IP.
3. **Managed Postgres 16** (any provider) – note host, port, database name, user, password; require TLS.
   Allow connections only from the server's IP. (Or use `--profile localdb` and back it up yourself.)
4. The secrets listed in `.env.prod.example` (meaning of each: `docs/SECRETS.md`). New ones for production:
   `JWT_SECRET` (`openssl rand -base64 48`), `DB_PASSWORD`. **Rotate** every key that was ever pasted in a chat.
5. Optional: an S3-compatible bucket for off-site backups; a Slack webhook or Telegram bot for alerts.

## 2. First deploy

```bash
# on the server
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 git
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw enable
sudo git clone <repo> /opt/fraudshield && cd /opt/fraudshield

cp deploy/.env.prod.example deploy/.env.prod && chmod 600 deploy/.env.prod && nano deploy/.env.prod
mkdir -p deploy/keys deploy/blocklists
# copy (scp) the secret files into deploy/keys/: blocklist-signing-key.pem, firebase-service-account.json
sudo chown -R 10001:10001 deploy/keys && chmod 600 deploy/keys/*        # readable by the api user only
# Phase 18 lists: build on your PC (python ml/blocklists/build_blocklists.py) and copy
# blocklists-play.bin, blocklists-web.bin, manifest.json into deploy/blocklists/

docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.prod up -d --build
#   (Postgres on this server instead: add --profile localdb and set DB_URL=jdbc:postgresql://db:5432/fraudshield)
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.prod ps     # api: healthy
curl -s https://$DOMAIN/actuator/health                                             # {"status":"UP",...}
```

- The first start creates all tables (Flyway V1…V17) and loads the seed data; it takes ~1 minute.
- Then **register the admin account** in the app with the e-mail in `ADMIN_EMAIL` and restart the api once
  (it promotes that account at start-up).
- Building on a 2 GB server works (Maven runs once per release). Alternative: build the image on CI / your PC,
  push it to a registry and set `API_IMAGE=<registry>/fraudshield-api:<version>`; then `up -d` without `--build`.

## 3. Point the app at it

```bash
cd mobile
flutter build appbundle --release --obfuscate --split-debug-info=build/symbols \
    --dart-define=API_BASE=https://<DOMAIN>/api/v1 --dart-define=FS_FLAVOUR=play
```
A release build refuses an `http://` address. See `docs/RELEASE.md` for signing and the upload checklist.

## 4. Backups and monitoring (cron)

```bash
sudo crontab -e
15 2 * * *   cd /opt/fraudshield && bash deploy/backup/pg_backup.sh >> /var/log/fraudshield-backup.log 2>&1
*/5 * * * *  cd /opt/fraudshield && bash deploy/monitor/check.sh   >> /var/log/fraudshield-monitor.log 2>&1
```
- With `--profile localdb` set `BACKUP_DOCKER_NETWORK=fraudshield_default` so `pg_dump` can reach the `db` service.
- Managed Postgres keeps its own backups too; ours are an extra, provider-independent copy. Set
  `BACKUP_S3_URI` for an off-site copy (and a lifecycle rule on the bucket).
- Run `monitor/check.sh` **also from a second machine** (or use any free uptime service on
  `https://<DOMAIN>/actuator/health`) – a monitor on the same server cannot report that the server is down.
  Set `MONITOR_LOCAL_CHECKS=0` there.

**Restore drill (monthly):** `bash deploy/backup/restore.sh /var/backups/fraudshield/<newest>.dump` restores into a
scratch database, compares the row count of every table with the live database, and drops the scratch DB.

**Disaster recovery:** `docker compose … stop api`, then
`bash deploy/backup/restore.sh <file.dump> --into-live` (asks you to type the database name), then `up -d`.

## 5. Upgrade, rollback, staging → production

- **Upgrade:** `git pull && docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.prod up -d --build`.
  Flyway applies new migrations at start; the old container keeps serving until the new one is built.
  Take a backup first (`pg_backup.sh`) when a release adds a migration.
- **Rollback:** check out the previous tag and `up -d --build` again. Flyway migrations are forward-only: if the bad
  release added a migration, restore the pre-upgrade backup (`--into-live`) before starting the old version.
- **Staging → production:** run the same compose file with a staging env file (another domain, e.g.
  `staging-api.<domain>`, its own database). When staging passes the release checklist, tag the commit and deploy
  that tag to production. The local proof of this file set is described below.

## 6. Local staging proof (prepared, not yet run)

`deploy/staging/` (git-ignored) holds a local env file with generated secrets, a copy of the dev signing key, the
Phase 18 lists and `run_staging_proof.sh`, which brings the stack up as compose project `fs-staging` on ports
28080/28443 (`Caddyfile.staging`, `staging.localhost`, internal CA), checks Flyway, HTTPS + headers, blocked paths,
register/login/scan, backup + restore drill and the monitor alert path, then removes everything again.
`staging/compose.staging-jar.yml` runs it from a prebuilt jar when there is no room to build the image.

On 2026-11-24 it could not run on the development PC: drive C: (where Docker Desktop keeps its disk) was full, the
image build crashed Docker Desktop, and free RAM was below 1 GB. Run it after moving Docker's disk image to another
drive (Docker Desktop → Settings → Resources → Advanced → Disk image location) or on the staging server itself:
`cd deploy && bash staging/run_staging_proof.sh`.

## Security notes

- Only 80/443 are open; the api and Postgres are not reachable from outside the compose network.
- Caddy serves only `/api/*` and `/actuator/health`; the health answer is `UP`/`DOWN` only (no details).
  Swagger / API docs are off in the `prod` profile.
- The api runs as a non-root user with read-only mounts for keys and lists; secrets come from `.env.prod`
  (mode 600) – never from the image.
- `server.forward-headers-strategy: framework` (prod profile) makes the rate limiter see the real client IP behind
  Caddy.
