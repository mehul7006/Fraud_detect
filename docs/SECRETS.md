# Secrets and credentials – where they live and what to do at go-live

**Values never go in git.** Real values live in one place only: `E:\fraud detection\.env` (git-ignored).
`.env.example` (committed) lists the same names with empty values. In production, put the same names in the
hosting platform's secret store (e.g. Render/Railway env vars, AWS Secrets Manager, GCP Secret Manager).

`scripts/run-backend.sh` passes everything in `.env` to the backend, so adding a line there is all that is needed locally.

## Inventory

| Env var | Used for (roadmap) | Where to get it | Status (dev) | Go-live action |
|---|---|---|---|---|
| `SAFE_BROWSING_KEY` | Google Safe Browsing URL lookup (4.1) | console.cloud.google.com → APIs & Services → enable "Safe Browsing API" → Credentials → API key | set, verified live | **Rotate** (was pasted in chat). Restrict key to Safe Browsing API (+ server IP). Free API = non-commercial: switch to **Google Web Risk API** if the app is commercial |
| `VT_ENABLED` | Turns VirusTotal on/off (5.1) | – | `true` | keep `true` |
| `VT_API_KEY` | VirusTotal hash lookup (5.1) | virustotal.com → profile → API key | set, verified live (EICAR 65/67) | **Rotate** (was pasted in chat). Free key = non-commercial, ~4/min, ~500/day: buy a **VirusTotal premium/commercial** licence for a commercial launch, then raise `fraudshield.virustotal.max-per-minute` |
| `ADMIN_EMAIL` | User promoted to ADMIN at start-up (4.3) | your own admin account email | empty | set to the real admin account; register that account first |
| `JWT_SECRET` | Signs login tokens (1.2, 0.4) | generate: `openssl rand -base64 48` | dev default used | **Required in prod** – app refuses to start without it (`ProdConfigGuard`). Never reuse the dev value |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection (1.1) | managed Postgres provider | dev: local Docker `fraud/fraud` | use the managed DB credentials; strong password; TLS |
| `MALWAREBAZAAR_API_KEY` | Downloads real adware samples for the 6.1 accuracy test set (research tool only, not used by the app) | bazaar.abuse.ch → sign in → Account → Auth-Key | set (dev) | not needed in prod; **rotate** (was pasted in chat) |
| `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS` + file `_devtools/keys/fraudshield-upload.jks` | Play **upload key** that signs release AABs (9.5) | made by `tools/release/make_upload_key.py` (random passwords, never printed) | set (dev machine) | **Back up the .jks + passwords** (password manager / offline). Enrol in Play App Signing so Google holds the real app-signing key and a lost upload key can be reset |
| `FIREBASE_CREDENTIALS_FILE` -> `_devtools/keys/firebase-service-account.json` (+ app config `mobile/android-native/google-services.json`) | Push alerts from the server (7.3); app config also used by Crashlytics (9.6) | Firebase console > Project settings > Service accounts > Generate new private key; Add Android app `com.fraudshield.app` > google-services.json | set (dev, project fraudshield-e6d90) | Service-account key is a **secret**: in prod mount it read-only (never in the image), rotate if leaked; restrict the google-services.json API key to the Android app in Google Cloud console |
| `GITHUB_TOKEN` | Lets the assistant read CI logs (0.3) – dev tool only, not used by the app | GitHub → Settings → Developer settings → Fine-grained token (Actions: read-only, this repo only) | set | not needed in prod; **rotate** (was pasted in chat), let it expire |
| `BLOCKLIST_SIGNING_KEY_FILE` | 12.5: signs the VPN blocklist (ECDSA P-256 private key file) | `_devtools/keys/blocklist-signing-key.pem`, made with openssl (public half: `blocklist-signing-public.pem`, built into the app) | set (dev key) | Back it up with the upload key: a new key needs a new app release. Never commit |
| `ANTHROPIC_API_KEY` | LLM explanations (11.6) - optional; without it the server uses the built-in English/Hindi templates | console.anthropic.com → API keys | not set (templates in use) | Model `claude-opus-5-5`, called in the background only, cached per reason set; receives verdict + reason codes + brand, never URLs or page text. Set a monthly spend limit in the Console |
| Android signing keystore + `key.properties` | Signed release AAB (9.5) | `keytool -genkey ...` (keep 2 offline backups) | not yet | create at 9.5; **losing it = cannot update the app on Play**. Never commit (`*.jks`, `*.keystore`, `key.properties` are git-ignored) |
| Firebase `google-services.json` | Crashlytics / FCM push (7.3, 9.6) | Firebase console | not yet | add at 7.3 / 9.6 |

## Go-live checklist
1. Rotate every key that was ever pasted into a chat, email or ticket (currently: `SAFE_BROWSING_KEY`, `VT_API_KEY`, `GITHUB_TOKEN`).
2. Generate a fresh `JWT_SECRET` and production DB password; never reuse dev values.
3. Check the licence of each external API for commercial use (Safe Browsing → Web Risk, VirusTotal → paid licence).
4. Copy every name from `.env.example` into the production secret store with production values.
5. Start the backend with profile `prod` – `ProdConfigGuard` stops start-up if a required secret is missing.
6. Store the Android keystore and its passwords in a password manager plus one offline backup.

## Rules
- Never paste a real key into chat, issues, commits or logs. Put it straight into `.env`.
- The backend logs never print keys (only HTTP status codes of failed calls).
- If a key leaks: revoke it at the provider first, then create a new one and update `.env` / the prod secret store.
