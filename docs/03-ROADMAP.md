# FraudShield – Detailed Roadmap (14 weeks core + AI fraud-focus phases 10–16, backend + frontend tracks)

> **Small steps:** every task below is split into 1–4 hour steps in [`docs/tasks/`](tasks/README.md) (`phase-NN.md`, 159 tasks, 1,141 steps, about 2,300 hours in total). Work one task at a time: do its steps, run its last `N.M.v` verify step, then mark the task ✅ here.

> **Product focus (decided):** protect the general public from becoming scam victims through **(1) fake / look-alike websites** (e.g. `fleepkart.com` pretending to be `flipkart.com`), **(2) fake messages**, **(3) fraud apps and installed apps that over-collect data and send it to third parties**, using AI + rules. Phases 10–14 deliver this; Phase 15 adds the public-safety extras. Phases 10–11 are backend-only and can start now that Phase 1 is complete.

**Team assumption:** 1 Java developer (BE) + 1 Flutter/Kotlin developer (FE). Both tracks run in parallel inside each phase.
**Numbering:** `P.N` = Phase P, sub-part N. Prefix `BE` = Java backend, `FE` = Flutter/Kotlin app. Example: `BE 4.2`.

## Status legend
| Mark | Meaning |
|---|---|
| ✅ | Code written **and verified** (build + tests + run proof) |
| 🟡 | Code written (first draft) but **not yet verified** |
| ⬜ | Not started |

## How we prove a sub-part is complete (the gate)
A sub-part can be marked ✅ only when **all** are true:
1. **Builds** with no errors (backend: `mvn test` in Docker; app: `flutter analyze` + `flutter build apk`).
2. **Automated test** exists for the logic (unit test, or API call script) and passes.
3. **"Done when"** line in the table below is demonstrated (log, curl output, screenshot or screen recording).
4. **No personal data leak**: only URL/domain, file hash, package names, counters leave the phone. Message text leaves the phone **only** through the explicit opt-in check (14.6–14.7), is never stored or logged.
5. Ticket in Jira moved to Done with the evidence attached.

Phase gate: the whole phase is closed only when every sub-part is ✅ and a short demo is recorded.

---

## Overview

| Phase | Weeks | Goal | BE | FE |
|---|---|---|---|---|
| 0 | 1 | Setup & tooling | 0.1–0.4 | 0.5–0.6 |
| 1 | 2–3 | Backend core API | 1.1–1.9 | – |
| 2 | 2–4 | Mobile base app | 2.1 (contract) | 2.2–2.9 |
| 3 | 4–5 | Call & SMS forwarding protection | 3.1–3.2 | 3.3–3.8 |
| 4 | 5–7 | Link protection | 4.1–4.4 | 4.5–4.10 |
| 5 | 7–8 | File protection | 5.1–5.3 | 5.4–5.8 |
| 6 | 8–9 | Ad-abuse detection | 6.1–6.3 | 6.4–6.8 |
| 7 | 9–11 | Clone & account takeover | 7.1–7.4 | 7.5–7.11 |
| 8 | 11–12 | Hardening | 8.1–8.7 | 8.8–8.13 |
| 9 | 13–14 | Release | 9.1–9.2 | 9.3–9.7 |
| 10 | 15–17 | **Look-alike website engine** (Flipkart vs Fleepkart) | 10.1–10.10, 10.12 | 10.11 |
| 11 | 17–19 | **AI classification + plain-language explanations** | 11.1–11.9 | 11.10 |
| 12 | 19–22 | **Local VPN core** (block fake sites, watch per-app traffic) | 12.5–12.6 | 12.1–12.4, 12.7–12.11 |
| 13 | 22–25 | **Leaky / fraud app protection** (detect, warn again and again, block internet) | 13.1, 13.3, 13.6, 13.7, 13.13, 13.14 | 13.2, 13.4, 13.5, 13.8–13.12 |
| 14 | 25–27 | **Fake message detection** (on-device + opt-in cloud) | 14.1, 14.2, 14.6 | 14.3–14.5, 14.7–14.10 |
| 15 | 27–32 | Public-safety extras (live-scam guard, recovery kit, UPI check, guardian) | 15.1, 15.5, 15.7, 15.9 | 15.2–15.4, 15.6, 15.8, 15.10 |
| 16 | 32–35 | **Call guard** (block scam calls, auto-cut unknown callers, free data only) | 16.1–16.5, 16.14 | 16.6–16.13 |

---

## Phase 0 – Setup (Week 1)
| ID | Track | Deliverable | Files / where | Done when | Status |
|---|---|---|---|---|---|
| 0.1 | BE | Repo structure, docs, feasibility, API spec | `README.md`, `docs/` | Folder exists, team has read `01-FEASIBILITY.md` | ✅ |
| 0.2 | BE | Reproducible build without installing Java (Docker Maven) | `backend/docker-compose.yml` | `mvn test` passes inside `maven:3.9-eclipse-temurin-21` | ✅ |
| 0.3 | BE | CI pipeline (GitHub Actions: `mvn test`, `flutter analyze`, `flutter test`) | `.github/workflows/ci.yml`, `mobile/tool/setup_android.sh`, `.gitignore` | Push triggers green build | ✅ (GitHub Actions run #7 on commit 9ec8641: Backend and Mobile jobs both green - mvn verify, flutter analyze, flutter test, Kotlin unit tests, debug APK build) |
| 0.4 | BE | Config profiles `dev`/`prod`, secrets from env (`JWT_SECRET`, DB URL) | `application-dev.yml`, `application-prod.yml`, `config/ProdConfigGuard.java` | In prod, app refuses to start if any of DB_URL/DB_USER/DB_PASSWORD/JWT_SECRET is missing or weak (7 tests + real start checked) | ✅ |
| 0.5 | FE | Flutter project generated + Kotlin/manifest merged | `mobile/android/` | `flutter build apk --debug` succeeds | ✅ (setup script, `flutter analyze` clean, 106 tests, debug APK builds natively on Windows; same script CI uses) |
| 0.6 | FE | App ID/namespace `com.fraudshield.app`, minSdk 26, signing config (debug) | `android/app/build.gradle` | APK installs on emulator | ✅ (applicationId com.fraudshield.app, minSdk 26 confirmed with dumpsys; installs and runs on an Android 13 emulator) |

---

## Phase 1 – Backend core (Weeks 2–3)  *all BE*
| ID | Deliverable | Files | Done when | Status |
|---|---|---|---|---|
| 1.1 | Spring Boot project + PostgreSQL + Flyway schema (users, devices, threat_events, blocklist) | `pom.xml`, `V1__init.sql`, `application.yml` | App boots, Flyway applies V1, JPA `validate` passes | ✅ |
| 1.2 | Auth: register/login, BCrypt, JWT, 401/409/400 status codes | `AuthController`, `JwtService`, `SecurityConfig` | Dup email→409, bad pw→401, no token→401, empty body→400 | ✅ |
| 1.3 | Device registration + clone-risk scoring (SIM change, root, emulator, Play Protect, RAT apps, duplicate fingerprint) | `CloneRiskService`, `DeviceController` | SIM change moves score 55→85; repeated heartbeats create only 1 open threat | ✅ |
| 1.4 | URL scan engine (blocklist, IP host, punycode, shortener, risky TLD, brand spoof, bait words, APK link) | `UrlScanService` | phishing sample=MALICIOUS(85), google.com=SAFE(0), blocklisted=100 | ✅ |
| 1.5 | File scan engine (hash blocklist, executable ext, double ext, magic bytes mismatch) | `FileScanService` | `invoice.pdf.apk`→MALICIOUS/DELETE; EICAR hash→MALICIOUS | ✅ |
| 1.6 | App ad-abuse scoring | `AdAbuseService` | spam sample app scores ≥70 | ✅ |
| 1.7 | Threat events API (list, report from device, resolve, delete-my-data) | `ThreatController`, `ThreatService` | create→list→resolve→DELETE /me all return correct codes | ✅ |
| 1.8 | Global JSON error format `{timestamp,status,error,message,path,requestId}` + request ID in logs and `X-Request-Id` header | `error/GlobalExceptionHandler`, `ErrorBody`, `RequestIdFilter` | 400/401/404/405/409 all return same JSON shape; no stack traces leaked | ✅ |
| 1.9 | Integration tests with Testcontainers (real PostgreSQL) covering 1.2–1.8 | `ApiFlowIT.java` (19 tests), failsafe plugin in `pom.xml` | `mvn verify`: 4 unit + 19 integration tests pass (running in CI is tracked in 0.3) | ✅ |

---

## Phase 2 – Mobile base (Weeks 2–4)
| ID | Track | Deliverable | Files | Done when | Status |
|---|---|---|---|---|---|
| 2.1 | BE | API contract frozen (OpenAPI/springdoc) so FE can generate/check models | `OpenApiConfig.java`, `ApiContractIT.java` (4 tests) | `/v3/api-docs` lists every endpoint in the spec, bearer auth declared, auth endpoints public; disabled in prod | ✅ |
| 2.2 | FE | API client + JWT in secure storage | `lib/core/api_client.dart` | Login stores token; restart keeps session | ✅ (12 tests: token stored, Bearer header, restart keeps session, logout, error mapping, file path never sent; real Keystore storage not covered) |
| 2.3 | FE | Login/Register screens with correct error messages (401/409/network) | `lib/screens/login_screen.dart` | Wrong pw shows "Wrong email or password" | ✅ (7 widget tests cover every error message) |
| 2.4 | FE | App gate + navigation + theme | `lib/main.dart` | Cold start goes to login or dashboard | ✅ (5 widget tests; on device: cold start, saved session survives a restart in real secure storage) |
| 2.5 | FE | Dashboard with status tiles | `dashboard_screen.dart` | Tiles show forwarding/clone/apps/link/file status | ✅ (widget tests; on device: tiles show live status) |
| 2.6 | FE | Permission onboarding with plain-language consent (notification access, all-files, phone) | `dashboard_screen.dart` (cards) | Each permission card opens correct system screen and disappears when granted | ✅ (widget tests; on device: all cards shown, gone once granted. Found+fixed 2 gaps: Android 13 notification permission was never requested, phone-permission card never hid) |
| 2.7 | FE | Threat history screen (list, resolve, pull-to-refresh) | `threats_screen.dart` | Shows threats created by BE test calls | ✅ (4 widget tests: list, empty, failure, Resolve) |
| 2.8 | FE | Blocking alert popup system (queue, critical = not dismissible by tap-outside) | `dashboard_screen.dart` `_drain()` | 2 alerts at once show one after another | ✅ (7 widget tests; on device: red critical popup with a single "I understand" button) |
| 2.9 | FE | Flutter unit + widget tests, `flutter analyze` clean | `test/` | 0 analyzer errors, tests pass | ✅ (25 tests pass, `flutter analyze` clean) |

---

## Phase 3 – Call & SMS forwarding (Weeks 4–5)
| ID | Track | Deliverable | Files | Done when | Status |
|---|---|---|---|---|---|
| 3.1 | BE | `CALL_FORWARDING` / `SMS_FORWARDING` threat types + de-dup (one OPEN per type, DB-enforced, safe under concurrent reports) | `ThreatService`, `V2__phase3_forwarding.sql` (`uq_threat_open_once`), `ForwardingIT` | Report accepts and stores; repeat returns 200 with same threat; 8 simultaneous reports create exactly 1 | ✅ |
| 3.2 | BE | Carrier response pattern table served from server so it can be updated without app release | `carrier_patterns` table, `GET /config/carriers` (ETag/304), `CarrierClassifier`, `CarrierConfigService` | Endpoint serves ordered rules + version; 16-sample corpus classifies correctly. **Seed rules are generic, not real carrier captures** (real ones come with FE 3.4) | ✅ |
| 3.3 | FE | Kotlin USSD query `*#21#`, `*#67#`, `*#61#`, `*#62#` | `CallForwardChecker.kt` | On real SIM returns carrier text | 🟡 (all 4 codes implemented; on the emulator the query reaches the phone system and Android returns a reply. Needs a real SIM to confirm the carrier text) |
| 3.4 | FE | Parse response → ACTIVE/OFF/UNKNOWN | `protection_engine.dart` `looksForwarded` | Unit test on ≥10 real carrier texts (Jio, Airtel, Vi, BSNL) | 🟡 (classifier + server rules + 16 synthetic samples pass. Still needs >=10 REAL carrier captures; the tile now shows unreadable carrier wording so they can be collected) |
| 3.5 | FE | One-tap disable `##002#` + re-check | `CallForwardChecker.kt`, engine | After disable, tile turns OFF | ✅ (logic tested incl. re-check; whether a carrier honours ##002# needs a real SIM) |
| 3.6 | FE | Fallback: open dialer with code when USSD API unsupported | `MainActivity.kt` `dialCode` | Works on Android 8–14 devices without permission | ✅ (tests; on device: "Check manually" opens the dialer with *#21#) |
| 3.7 | FE | SMS-stealer audit: list apps with SMS permission + notification listener | `AppAuditor.kt` | Test app with READ_SMS appears in list | ✅ (on emulator: installed a test app declaring READ_SMS; the audit screen lists it under "Installed by you (1)" as "SMS Test App - Reads SMS" with an Uninstall button, plus the 14 system apps) |
| 3.8 | FE | SMS forwarding guidance screen (carrier steps) | `dashboard_screen.dart` | Dialog content reviewed by non-technical user | ✅ (tests; on device: three checks shown, button opens the audit) |

---

## Phase 4 – Link protection (Weeks 5–7)
| ID | Track | Deliverable | Files | Done when | Status |
|---|---|---|---|---|---|
| 4.1 | BE | Google Safe Browsing integration (hash-prefix/URL lookup) behind interface | `UrlScanService.externalReputation` | Known test URL from Google returns +score | ✅ (live: Google's test pages testsafebrowsing.appspot.com/s/malware.html and /phishing.html return MALICIOUS with reason "Listed by Google Safe Browsing"; wikipedia stays SAFE; key kept only in the git-ignored `.env`) |
| 4.2 | BE | Result cache (host → verdict, 24h) to cut cost/latency | Caffeine cache | Second scan of same host <5 ms | ✅ (Caffeine, key = URL without query, failures never cached; 2 cache tests, cached lookup 0 ms) |
| 4.3 | BE | Blocklist admin API + CSV import (ADMIN role) | `BlocklistController` | Added domain is MALICIOUS next scan | ✅ (V3 migration, role in JWT, `/admin/**` ADMIN-only, CSV import, `ADMIN_EMAIL` bootstrap; `AdminFeedbackIT`) |
| 4.4 | BE | False-positive report endpoint + review queue | `POST /scan/url/feedback` | Report stored and visible to admin | ✅ (host-only storage, duplicate → 409, admin queue + close with ADD_TO_BLOCKLIST; `AdminFeedbackIT`) |
| 4.5 | FE | Notification listener captures notifications from all apps | `AdNotificationListener.kt` | Test notification with URL stored in pending list | ✅ (on device: listener stored link events from posted notifications) |
| 4.6 | FE | URL extraction regex (http, www, bare domains, .apk) with unit test | `AdNotificationListener.kt` | 20 sample strings extract correctly | ✅ (`UrlExtractor.kt` + 20 JUnit cases: `20 tests completed, 0 failed`; Dart port with the same 20 samples; CI step added) |
| 4.7 | FE | Scan queue → backend → verdict → popup/notification | `protection_engine.dart` `_handleLink` | WhatsApp phishing text triggers popup in ≤20 s | ✅ (on emulator: notification with a blocklisted link -> MALICIOUS_LINK recorded and popup path in 15 s, limit 20 s; unit tests for SAFE/SUSPICIOUS/MALICIOUS, dedupe, max 3 parallel. Works while the app is alive; background = 7.11) |
| 4.8 | FE | Share-to-FraudShield (receive text intent in Flutter and scan) | `MainActivity.kt` + Dart handler | Share a link from browser → verdict shown | ✅ (Share → FraudShield on emulator: shared `example-phish.test` link → backend recorded MALICIOUS_LINK → "DANGEROUS LINK" popup; 3 engine tests) |
| 4.9 | FE | Per-app monitoring toggles + "trust this domain" list | settings screen | Disabled app's links ignored | ✅ (settings screen on emulator: added trusted domain `example-phish.test`, saved on the phone, a later notification link on it was ignored; per-app switch covered by unit test) |
| 4.10 | FE | Offline queue: links captured offline scanned when online | Dart/SQLite | Airplane-mode test passes | ✅ (on emulator: backend stopped, link captured -> queued; backend restarted -> scanned within 38 s, queue emptied; 3 unit tests) |

---

## Phase 5 – File protection (Weeks 7–8)
| ID | Track | Deliverable | Files | Done when | Status |
|---|---|---|---|---|---|
| 5.1 | BE | VirusTotal hash lookup (hash only, never file) | `FileScanService` | EICAR hash detected by VT | ⬜ |
| 5.2 | BE | Extra heuristics: PDF with `/JavaScript` `/Launch`, Office macro flag (needs larger `headerHex`/flags from app) | DTO + `FileScanService` | Test PDFs score correctly | ⬜ |
| 5.3 | BE | File threat stats endpoint (counts by type) | `GET /threats/summary` | Dashboard numbers match | ⬜ |
| 5.4 | FE | Folder scanner (Downloads, WhatsApp, WhatsApp Business, Telegram) + SHA-256 | `FileScanner.kt` | New file in Downloads reported within 20 s | 🟡 |
| 5.5 | FE | First-run baseline (don't flag existing library) | `FileScanner.kt` | Existing files ignored, new file flagged | 🟡 |
| 5.6 | FE | PDF/Office flag extraction (scan first 64 KB for `/JS`, `/Launch`, `vbaProject`) | `FileScanner.kt` | Test files produce flags | ⬜ |
| 5.7 | FE | Delete flow with confirmation; restricted to scan roots | `FileScanner.delete`, popup action | File gone; deleting `/system` path refused | 🟡 |
| 5.8 | FE | All-files-access permission UX + Android 11–14 testing | `MainActivity.kt` | Granted/denied flows both OK | 🟡 |

---

## Phase 6 – Ad-abuse detection (Weeks 8–9)
| ID | Track | Deliverable | Files | Done when | Status |
|---|---|---|---|---|---|
| 6.1 | BE | Tune scoring weights from beta data; config via `application.yml` | `AdAbuseService` | Precision ≥90% on 30 labelled apps | ⬜ |
| 6.2 | BE | App reputation table (package → known good/bad), seeded with top 500 apps | `app_reputation` + migration | Known-good apps never flagged | ⬜ |
| 6.3 | BE | Per-device app history (detect new risky app installed) | `device_apps` | "New app since last scan" in response | ⬜ |
| 6.4 | FE | Notification counting per package per hour | `AdNotificationListener.kt` | Spam test app reaches ≥20/h | 🟡 |
| 6.5 | FE | Overlay / full-screen-intent / accessibility / listener permission audit | `AppAuditor.kt` | Flags test app with SYSTEM_ALERT_WINDOW | 🟡 |
| 6.6 | FE | UsageStats: apps launched without user action / background starts (needs Usage Access) | new `UsageAuditor.kt` | Detects app that opens itself | ⬜ |
| 6.7 | FE | Uninstall flow + "Ignore this app" | `MainActivity.kt` `uninstallApp` | System uninstall dialog appears | 🟡 |
| 6.8 | FE | Risky apps screen (list with reasons) | new screen | All flagged apps visible with reasons | ⬜ |

---

## Phase 7 – Clone & account takeover (Weeks 9–11)
| ID | Track | Deliverable | Files | Done when | Status |
|---|---|---|---|---|---|
| 7.1 | BE | Multi-device clone rules (same account, 2 active devices, same fingerprint/IMEI-hash) | `CloneRiskService` | Duplicate fingerprint adds +50 | ✅ (rule) / ⬜ (multi-device test) |
| 7.2 | BE | Takeover event correlation (OTP + new device within 10 min → CRITICAL) | `TakeoverService` | Two events raise severity | ⬜ |
| 7.3 | BE | Push alerts (FCM) when threat created from another source | FCM integration | Test push received | ⬜ |
| 7.4 | BE | Safe-contact/emergency contact SMS alert (opt-in) | `NotifyService` | Contact receives alert | ⬜ |
| 7.5 | FE | Device fingerprint, SIM hash, root, emulator, Play Protect, RAT apps | `DeviceInfo.kt` | Output matches backend DTO | 🟡 |
| 7.6 | FE | Hourly heartbeat | `protection_engine.dart` | Backend `last_seen` updates | 🟡 |
| 7.7 | FE | Unexpected WhatsApp/Telegram login-code detector (never stores the code) | `AdNotificationListener.kt` | Test notification "Telegram code" raises alert | 🟡 |
| 7.8 | FE | New-device/linked-device notice detector | `AdNotificationListener.kt` | Test notice raises CRITICAL | 🟡 |
| 7.9 | FE | WhatsApp & Telegram guided wizards (open app + steps) | `takeover_wizard_screen.dart` | Opens apps, steps readable | 🟡 |
| 7.10 | FE | Persistent critical popup (full-screen intent) + Android 14 permission check | `AlertNotifier.kt` | Shows on lock screen | 🟡 |
| 7.11 | FE | **Foreground service / WorkManager** so checks run when app closed | new `ProtectionService.kt` | Link scanned while app swiped away | ⬜ **(critical gap)** |

---

## Phase 8 – Hardening (Weeks 11–12)
| ID | Track | Deliverable | Done when | Status |
|---|---|---|---|---|
| 8.1 | BE | Rate limiting per user/IP (Bucket4j) | 100 req/min limit returns 429 | ⬜ |
| 8.2 | BE | Refresh tokens + logout/revoke | Old token rejected after logout | ⬜ |
| 8.3 | BE | HTTPS/TLS, CORS, security headers | SSL Labs grade A | ⬜ |
| 8.4 | BE | No-PII logging review; log scrubbing | Grep of logs finds no email/URL query strings | ⬜ |
| 8.5 | BE | Unit + integration coverage ≥80% (JaCoCo) | Report ≥80% | ⬜ |
| 8.6 | BE | Dependency/vulnerability scan (OWASP dep-check) | 0 high CVEs | ⬜ |
| 8.7 | BE | Load test (k6): 200 req/s on `/scan/url` | p95 < 300 ms | ⬜ |
| 8.8 | FE | Battery profiling (<3%/day) | Battery Historian report | ⬜ |
| 8.9 | FE | Certificate pinning + obfuscation (R8) | Release build proguard OK | ⬜ |
| 8.10 | FE | Error/empty/offline states on every screen | Manual checklist done | ⬜ |
| 8.11 | FE | Localisation: English + Hindi | Language switch works | ⬜ |
| 8.12 | FE | Accessibility (TalkBack, large text) | Checklist passes | ⬜ |
| 8.13 | FE | Real-device test matrix (Samsung, Xiaomi, Realme, Pixel × Android 10–14) | Matrix filled, bugs closed | ⬜ |

---

## Phase 9 – Release (Weeks 13–14)
| ID | Track | Deliverable | Done when | Status |
|---|---|---|---|---|
| 9.1 | BE | Production deploy (Docker + managed Postgres), backups, monitoring (health, alerts) | Staging→prod promoted | ⬜ |
| 9.2 | BE | Data-deletion + privacy endpoints verified | `DELETE /me` removes all rows | ✅ (endpoint) / ⬜ (audit) |
| 9.3 | FE | Play Console declaration forms (QUERY_ALL_PACKAGES, MANAGE_EXTERNAL_STORAGE, notification listener, CALL_PHONE) with demo video | Forms submitted | ⬜ |
| 9.4 | FE | Privacy policy + in-app consent screens | URL live, linked in app | ⬜ |
| 9.5 | FE | Signed release AAB + versioning | AAB uploads to internal track | ⬜ |
| 9.6 | FE | Crash reporting (Firebase Crashlytics) | Test crash appears | ⬜ |
| 9.7 | FE | Closed beta (20 users), feedback triage | 2 weeks, crash-free ≥99% | ⬜ |

---

## Phase 10 – Look-alike website engine (Weeks 15–17)
**Goal:** given a link, decide "is this a fake copy of a real brand?" and tell the user which real site it imitates. Example: `fleepkart.com`, `flipkarrt.com`, `flipkart-sale.xyz`, `f1ipkart.com` are flagged as fake Flipkart; `flipkart.com` and unrelated `flickr.com` are SAFE.
**How it works (layers, each adds to an explainable score):** 1) look-alike name match → 2) domain facts (age, certificate, redirects) → 3) safe page fetch and comparison with the real brand → 4) combined verdict with reason codes. AI (Phase 11) sits on top; rules still give a hard floor.

| ID | Track | Deliverable | Files / where | Done when | Status |
|---|---|---|---|---|---|
| 10.1 | BE | Brand registry: table `brands` (name, official domains, keywords, category, favicon hash) seeded with ≥200 Indian + global brands (Flipkart, Amazon, Myntra, SBI, HDFC, ICICI, PhonePe, Paytm, GPay, Jio, Airtel, India Post, IRCTC, UIDAI, Income Tax, WhatsApp, Telegram, Instagram, Facebook, Google, Netflix…) | `V2__brands.sql`, `BrandRepository` | Migration applies; ≥200 brands each with ≥1 official domain; lookup by registrable domain returns brand | ⬜ |
| 10.2 | BE | Domain normaliser: registrable domain (public-suffix list), punycode/IDN decode, strip `www`, detect subdomain tricks | `DomainNormalizer` | `login.flipkart.com.evil.xyz` → `evil.xyz`; `xn--` IDN decoded; 30-case unit test passes | ⬜ |
| 10.3 | BE | Look-alike matcher: Damerau-Levenshtein ≤2, homoglyph/confusable map (`rn→m`, `vv→w`, `0→o`, `1→l`, Cyrillic `а`), QWERTY-neighbour typos, repeated/missing letters, brand+keyword combos (`flipkart-offer`), brand in subdomain or path, wrong TLD | `LookalikeService` | `fleepkart.com`, `flipkarrt.com`, `f1ipkart.com`, `flipkart-sale.xyz` → impersonates Flipkart, score ≥80; official `flipkart.com`/`flipkart.in` → SAFE | ⬜ |
| 10.4 | BE | Popular-domain allowlist (Tranco top 10k seed file) to prevent false positives | `popular-domains.txt`, `LookalikeService` | `flickr.com`, `flipboard.com`, `amazonaws.com` are NOT flagged as brand look-alikes | ⬜ |
| 10.5 | BE | Domain intelligence via RDAP: domain age, registrar, country; cached; timeout-safe | `DomainIntelService` | New (<30 days) domain adds score; RDAP timeout does not fail the scan (degrades); stub-server test passes | ⬜ |
| 10.6 | BE | TLS + redirect facts: certificate issuer/age/hostname mismatch, redirect chain (max 5), final host differs from first | `TlsRedirectService` | Local test servers: mismatch cert and 4-hop redirect detected; localhost/private IPs refused (SSRF guard) | ⬜ |
| 10.7 | BE | Safe page fetcher: strict timeout, 1 MB cap, no cookies, no JavaScript execution (v1), blocks private ranges; extracts title, favicon hash, forms, form-action domains, password/OTP/card fields, external scripts, meta-refresh | `PageFetcher`, `PageFeatures` | Fixture HTML parsed correctly; SSRF test (169.254.x, 10.x, localhost, DNS-rebinding) all refused | ⬜ |
| 10.8 | BE | Page-vs-brand comparison: brand name/logo/favicon hash shown on a non-official domain; login/payment form posting to a different domain; "verify KYC / prize / account blocked" wording | `PageBrandCheck` | Fixture "fake Flipkart" page on non-official domain → MALICIOUS with reason "copies Flipkart page" | ⬜ |
| 10.9 | BE | Risk combiner v2 in `UrlScanService`: layered score, reason **codes** (`LOOKALIKE_BRAND`, `NEW_DOMAIN`, `FORM_TO_OTHER_DOMAIN`, …); response adds `impersonatedBrand` and `officialUrl` | `UrlScanService`, `ScanResponse` | Response = `{verdict,score,reasons[],reasonCodes[],impersonatedBrand,officialUrl}`; all 1.4 tests still pass | ⬜ |
| 10.10 | BE | Time budget + cache: whole scan ≤3 s, each layer degrades independently, 24 h host cache | `UrlScanService`, Caffeine | Slow RDAP/fetch still returns a verdict ≤3 s; second scan of same host <50 ms | ⬜ |
| 10.11 | FE | Fake-site warning screen: "This looks like Flipkart but is NOT Flipkart", letters that differ highlighted (`fleepkart` vs `flipkart`), buttons: **Go to real site**, Close, Report | `fake_site_screen.dart` | Widget test + screenshot; button opens the official URL | ⬜ |
| 10.12 | BE | Labelled test corpus (≥100 real-style Indian phishing URLs + ≥100 legitimate) and accuracy report | `src/test/resources/url-corpus.csv`, `AccuracyTest` | Precision ≥95%, recall ≥90%; every false positive listed in the report | ⬜ |

---

## Phase 11 – AI classification and explanations (Weeks 17–19)
**Goal:** catch **new** fake sites that are not on any list, and explain every verdict in simple Hindi/English. **Design rule:** the ML model and rules decide; the LLM only *explains* (it can never turn a MALICIOUS verdict into SAFE, and it never sees page text directly).

| ID | Track | Deliverable | Files / where | Done when | Status |
|---|---|---|---|---|---|
| 11.1 | BE | Training-data pipeline: public phishing feeds (OpenPhish, PhishTank, URLhaus) + Tranco benign; license check recorded | `ml/build_dataset.py`, `ml/DATA-LICENSES.md` | ≥20k rows, balanced split, reproducible with one command | ⬜ |
| 11.2 | BE | Feature extractor (URL lexical: length, digits, hyphens, entropy, subdomain count, brand tokens, TLD risk + Phase 10 domain/page features); **same code for training and serving** | `FeatureExtractor` (Java) + test vectors | Java and Python give identical features on 100 sample URLs | ⬜ |
| 11.3 | BE | Model training (logistic baseline + gradient-boosted trees), export to ONNX (no Python in production), versioned | `ml/train.py`, `models/url-v1.onnx` | Held-out AUC ≥0.97; recall ≥90% at false-positive ≤1% | ⬜ |
| 11.4 | BE | Model serving in Spring (ONNX Runtime Java) with versioning and fallback to rules if the model fails to load | `MlScoreService` | Scan returns `mlScore`; deleting the model file → rules-only verdict, app still boots | ⬜ |
| 11.5 | BE | Combine rules + ML policy: ML can raise the score; an official/allow-listed domain can never be flagged; rule hard-blocks stay | `UrlScanService` | 30 golden cases give stable verdicts (snapshot test) | ⬜ |
| 11.6 | BE | LLM explainer (Claude API): input = domain + reason codes + brand only (no user data, no raw page text); output ≤2 sentences en/hi; cached by reason-set; timeout → template | `ExplainService` | 10 sample verdicts explained; API key missing → template text; prompt-injection test (hostile page title never reaches the prompt) passes | ⬜ |
| 11.7 | BE | Template explanations (offline fallback) en + hi for every reason code | `explanations_en.properties`, `explanations_hi.properties` | Test fails if any reason code lacks en or hi text | ⬜ |
| 11.8 | BE | Feedback loop: `POST /scan/url/feedback` (false positive / false negative), review queue, export of labelled rows for retraining (merges with 4.4) | `FeedbackController` | Report stored; export script outputs labelled CSV | ⬜ |
| 11.9 | BE | Evaluation harness + CI gate: metrics printed every build, build fails if recall or precision drops below target | `ml/evaluate.py`, CI step | Deliberately degraded model makes CI fail | ⬜ |
| 11.10 | FE | "Why is this risky?" panel in alert and threat history, language switch en/hi | `alert_dialog`, `threats_screen.dart` | Widget test shows explanation in both languages | ⬜ |

---

## Phase 12 – Local VPN core (Weeks 19–22)
**Goal:** one on-device VPN (`VpnService`, no root, traffic never leaves the phone except the **domain** sent for checking) that (a) **blocks fake websites in any browser or app**, (b) records **which app talks to which domain** and how much, (c) later **blocks a leaking app's internet** (13.9).
**Honest limits:** it sees domains and byte counts only, never page content or full URLs of HTTPS sites. Apps that use DNS-over-HTTPS can bypass DNS checks (12.3 handles known DoH hosts). The user must accept the system VPN prompt.

| ID | Track | Deliverable | Files / where | Done when | Status |
|---|---|---|---|---|---|
| 12.1 | FE | `VpnService` skeleton: TUN interface, foreground notification, start/stop, plain-language consent screen (what is seen / not seen) | `FraudVpnService.kt`, `vpn_consent_screen.dart` | VPN key icon appears; internet keeps working | ⬜ |
| 12.2 | FE | Packet parser (IPv4/IPv6, UDP DNS queries and answers) and mapping to app UID (`getConnectionOwnerUid`, API 29+; fallback for older) | `PacketParser.kt`, `UidResolver.kt` | Log shows domain + app name while browsing; unit test with captured packets | ⬜ |
| 12.3 | FE | DNS forwarding + local cache, selectable upstream DNS, handling of known DoH hosts (block or warn) | `DnsProxy.kt` | Sites load normally; added latency <20 ms; DoH test host handled | ⬜ |
| 12.4 | FE | Domain-check pipeline: local fast filter (hashed blocklist) → async server verdict → on MALICIOUS return NXDOMAIN and show full-screen "Blocked fake site" alert | `DomainGuard.kt`, `ProtectionEngine` | Opening a known test fake domain in Chrome is blocked and alert appears in ≤1 s | ⬜ |
| 12.5 | BE | Blocklist sync `GET /config/blocklist?since=` (delta of hashed domains, versioned, signed) | `ConfigController` | Delta returns only new entries; tampered signature rejected by app | ⬜ |
| 12.6 | BE | Light endpoint `POST /scan/domain` (domain only, no path/query) for the VPN path; never logs the domain with user id | `ScanController` | Contract test; log grep finds no domain linked to a user | ⬜ |
| 12.7 | FE | Privacy controls: only the domain leaves the phone; toggle "local-only mode" (blocklist only, no server calls) | `settings_screen.dart` | Network capture shows only domain; local-only mode makes zero scan calls | ⬜ |
| 12.8 | FE | Per-app traffic accounting (bytes up/down, domains contacted, counts), local SQLite, 30-day rolling | `TrafficStore.kt` | "Top apps" screen shows real numbers matching Android data-usage within 10% | ⬜ |
| 12.9 | FE | Battery and stability: Always-on VPN support, restart after reboot, wifi↔mobile switch, 24 h soak | `FraudVpnService.kt` | 24 h soak with no crash; battery use <3%/day | ⬜ |
| 12.10 | FE | Conflict handling: another VPN active, Private DNS on, VPN revoked by user; clear guidance screens | `vpn_state_screen.dart` | Each state tested manually and shows the right message | ⬜ |
| 12.11 | FE | Play policy: VpnService declaration text + prominent in-app disclosure | `docs/play-vpn-declaration.md` | Text reviewed against current policy | ⬜ |

---

## Phase 13 – Leaky / fraud app protection (Weeks 22–25)
**Goal:** find apps that ask for unnecessary data, contain trackers, send data to third parties, or are fake copies of real apps. Then **warn again and again** and **block the app's internet**. Android does not allow one app to force-uninstall another, so the strongest real actions are: block its network (13.9), repeat the warning (13.10), one-tap uninstall dialog, and guided permission revoke (13.8).

| ID | Track | Deliverable | Files / where | Done when | Status |
|---|---|---|---|---|---|
| 13.1 | BE | App reputation v2 (extends 6.2): package, signer-cert hash, category, known trackers, verdict; seeded with top 500 apps + known-bad list | `app_reputation` migration | Known-good apps never flagged; known-bad package returns MALICIOUS | ⬜ |
| 13.2 | FE | Installed-app inventory: permissions requested/granted, installer (Play vs sideload), signing-cert hash, install time, target SDK | `AppAuditor.kt` | Inventory JSON matches `adb shell pm` output for 10 test apps | ⬜ |
| 13.3 | BE | Permission-vs-purpose rules: category → expected permissions (e.g. torch app asking contacts + SMS + location = high; sideloaded + SMS + overlay + accessibility = critical); extend `POST /scan/apps` | `AppPrivacyService` | 20 sample apps scored as expected; precision ≥90% on 50 labelled apps | ⬜ |
| 13.4 | FE | Tracker-SDK scan: match an app's embedded SDK class prefixes against a ~400 tracker signature list (Exodus-style) | `TrackerScanner.kt`, `trackers.json` | App with Facebook/AppsFlyer SDK shows them by name | ⬜ |
| 13.5 | FE | Network-behaviour analyser on 12.8 data: many third-party domains, unknown analytics hosts, high background upload | `AppNetworkAnalyzer.kt` | Test app sending to 10 ad domains is flagged; normal app is not | ⬜ |
| 13.6 | BE | Domain categorisation list (ads / analytics / tracker / malware / unknown) with delta sync | `domain_categories` table, `GET /config/domains` | Known ad domain returns category `ads` | ⬜ |
| 13.7 | BE | Combined app privacy score + reasons en/hi (permissions + trackers + traffic + sideload) | `AppPrivacyService` | Response `{score,verdict,reasonCodes[],reasons[]}` for each app | ⬜ |
| 13.8 | FE | Risky-app detail screen: what it can access, who it talks to (last 7 days), buttons **Block internet**, **Revoke permissions** (deep link), **Uninstall**, **Ignore** | `app_detail_screen.dart` | Every button works on a test device; Ignore is remembered | ⬜ |
| 13.9 | FE | Per-app network block inside the VPN (drop packets by UID), persisted, auto-cleaned on uninstall | `AppFirewall.kt` | Blocked test app has no connectivity; other apps unaffected; survives reboot | ⬜ |
| 13.10 | FE | Repeat-warning scheduler with escalation (at detection, +1 h, daily, until fixed); frequency cap so users are not spammed; stops on uninstall/Ignore | `WarnScheduler.kt` | Reminders fire on schedule; cap respected; stops when app is gone | ⬜ |
| 13.11 | FE | New-install watcher (`PACKAGE_ADDED`) → scan immediately → alert "Just installed X; it wants …" | `InstallReceiver.kt` | Installing a test app raises an alert in <10 s | ⬜ |
| 13.12 | FE | Fake-app check: name/icon looks like a known brand app but package or signing cert is not the official one | `FakeAppDetector.kt` | Fake "PhonePe" with wrong package/cert is flagged; real PhonePe is not | ⬜ |
| 13.13 | BE | Official app registry: brand → official package names + signing-cert hashes (≥100 apps: banks, UPI, shopping, social) | `official_apps` migration | Lookup by package returns brand + cert; fake package returns none | ⬜ |
| 13.14 | BE | Evaluation set: 50 labelled apps (benign + leaky + fake) with metrics report | `AppAccuracyTest` | Precision ≥90%, recall ≥85%; false positives listed | ⬜ |

---

## Phase 14 – Fake message detection (Weeks 25–27)
**Goal:** tell the user "this message looks like a scam" and why. **Privacy design (option C):** an **on-device** model checks messages first (text never leaves the phone). The user may tap **"Check with cloud AI"** for a deeper check; that text is sent over TLS, never stored, never logged. Links inside any message always go through the Phase 10–12 website pipeline.

| ID | Track | Deliverable | Files / where | Done when | Status |
|---|---|---|---|---|---|
| 14.1 | BE | Labelled scam-message corpus (English, Hindi, Hinglish): bank/KYC block, lottery/prize, India Post/courier, electricity disconnect, fake job/task, loan, "digital arrest", OTP request, UPI refund; plus genuine bank/OTP/delivery messages. No real personal data | `ml/messages/` | ≥2000 messages, ≥9 scam categories, privacy review done | ⬜ |
| 14.2 | BE | Train a small multilingual classifier (TF-IDF + linear first; distilled transformer if needed) and export to TFLite ≤10 MB | `ml/train_msg.py`, `models/msg-v1.tflite` | Held-out F1 ≥0.90; model file ≤10 MB | ⬜ |
| 14.3 | FE | On-device inference through Kotlin/TFLite + MethodChannel, with model update from server | `MessageClassifier.kt` | Classification <200 ms on a mid-range phone; model updates without app release | ⬜ |
| 14.4 | FE | Input paths: share text to FraudShield, "Check this message" paste box, optional notification text check with per-app toggle | `check_message_screen.dart`, `AdNotificationListener.kt` | Shared scam text shows verdict; disabled app's notifications are ignored | ⬜ |
| 14.5 | FE | On-device rule layer before ML: urgency words, "account will be blocked", asks for OTP/PIN/CVV, "click to claim", suspicious sender-ID patterns | `message_rules.dart` | Unit tests on ≥40 samples; rules explain themselves | ⬜ |
| 14.6 | BE | Opt-in cloud check `POST /scan/message`: text processed in memory, category + LLM explanation returned, nothing stored, nothing logged | `MessageScanController` | Log and DB grep find no message text; request without consent flag is refused (400) | ⬜ |
| 14.7 | FE | Consent flow for cloud check: first-time explanation + per-message confirm; clear "sent to server" label | `cloud_consent_dialog.dart` | Cloud call never fires without the tap | ⬜ |
| 14.8 | FE | Verdict screen: "Looks like a fake KYC scam", what to do, never click; links in the message scanned and merged into one verdict | `message_verdict_screen.dart` | Message with a fake-Flipkart link shows both message and link verdicts | ⬜ |
| 14.9 | FE | Hindi and Hinglish support: UI strings and test messages | `l10n/`, tests | 30 Hindi/Hinglish scam samples detected | ⬜ |
| 14.10 | FE | False-positive guard: 500 genuine bank/OTP/delivery messages must stay SAFE | `message_fp_test` | False-positive rate <2% | ⬜ |

---

## Phase 15 – Public-safety extras (Weeks 27–32)
**Goal:** features that help ordinary people during and after a scam. Built only after Phases 10–14 are verified.

| ID | Track | Deliverable | Files / where | Done when | Status |
|---|---|---|---|---|---|
| 15.1 | BE | **Live-scam correlation**: long call from unknown number + remote-access/screen-share app active + banking/UPI app opened + OTP notification within a window → CRITICAL (extends `TakeoverService`) | `ScamSessionService` | Four simulated events in 10 min raise one CRITICAL threat; two events stay below it | ⬜ |
| 15.2 | FE | Signals for 15.1: call state, remote-access app foreground, banking app foreground (Usage Access, opt-in) | `ScamSignalCollector.kt` | All four signals reach backend as counters, no content | ⬜ |
| 15.3 | FE | Full-screen "STOP" warning in plain Hindi/English with voice read-out | `scam_stop_screen.dart` | Shows over banking app; spoken warning plays | ⬜ |
| 15.4 | FE | OTP-share guard: OTP notification while a call is active → warning "Do not read this out"; the code is never stored | `AdNotificationListener.kt` | Test notification during test call raises warning | ⬜ |
| 15.5 | BE | UPI / QR pre-payment check `POST /scan/upi` (new/unknown VPA, community reports, name mismatch, collect-request vs pay QR) | `UpiScanService` | Reported VPA flagged; collect-request QR flagged | ⬜ |
| 15.6 | FE | **Recovery kit** with "golden hour" timer: 1930 helpline, cybercrime.gov.in link, bank block numbers, freeze checklist, evidence pack (timestamps, numbers, app names, user-picked screenshots) | `recovery_kit_screen.dart` | One-tap call/links work; evidence ZIP created locally | ⬜ |
| 15.7 | BE | Crowd reports for numbers, links, UPI IDs (hashed, de-duplicated, trust-weighted, moderation queue) | `ReportController` | 5 trusted reports mark an item SUSPICIOUS; abuse test (1 user × 20 reports) does not | ⬜ |
| 15.8 | FE | Guardian mode: trusted family member gets an alert when a CRITICAL scam situation is detected; opt-in, no message content (extends 7.4) | `guardian_screen.dart` | Guardian receives alert; user can switch off any time | ⬜ |
| 15.9 | BE | Fake customer-care number checker against official brand directory + community reports | `CareNumberService` | Fake support number for a known brand is flagged; official one is not | ⬜ |
| 15.10 | FE | Scam drills: short simulated scenarios (fake police call, KYC, lottery) with score, Hindi/English | `drills_screen.dart` | 5 scenarios playable; completion saved locally | ⬜ |

---

## Phase 16 – Call guard (Weeks 32–35)
**Goal:** (1) automatically block calls from known scam numbers, (2) give the user an **auto-cut option for unknown callers**, (3) show a notification saying who/what the caller is. **Free data only: no paid API is used anywhere in this phase.**
**How it works:** Android `CallScreeningService` (Android 10+; the user sets FraudShield as the "caller ID & spam app"). The decision is made **on the phone from a cached list** (the system gives only a few seconds). Order of checks: always-allow list (saved contacts, starred, numbers the user called recently) → known-scam list → free pattern rules → the user's chosen mode.
**User modes:** 1) Warn only (default) · 2) Silence unknown callers · 3) Reject known scam numbers only · 4) Reject all unknown callers (shows a warning that doctors, delivery and banks may be blocked).
**What "caller name" means here (free):** the saved contact name, else a label such as "Reported as bank fraud by 42 people", "Foreign number" or "Telemarketer series". A real person's name from a paid database is **not** promised. If the phone or operator already supplies a caller display name, we show it.
**Privacy:** the phone sends only the first 5 characters of a number's hash (HIBP-style range lookup); the server never learns which number called and never stores call logs.

| ID | Track | Deliverable | Files / where | Done when | Status |
|---|---|---|---|---|---|
| 16.1 | BE | Number reputation store: hashed number, category, report count, trust score, updated time | `V3__number_reputation.sql`, `NumberReputationRepository` | Migration applies; no raw phone number column exists | ⬜ |
| 16.2 | BE | Privacy-preserving lookup `GET /numbers/range/{prefix5}`: returns only matching hash suffixes with category and count | `NumberController` | Test: server log and DB contain no full number and no caller identity; matches found by suffix on the client | ⬜ |
| 16.3 | BE | Free number-pattern rules shipped as JSON: risky foreign country codes (configurable), telemarketer and service-call number series (to be confirmed against current TRAI rules), odd lengths; `GET /config/number-rules` | `NumberRuleService` | 30 sample numbers classified as expected; rules changeable without an app release | ⬜ |
| 16.4 | BE | Scam-number delta sync `GET /config/numbers?since=` (hashed, versioned, signed) so the phone can decide offline | `ConfigController` | Delta returns only new entries; tampered signature rejected by the app | ⬜ |
| 16.5 | BE | Report a number `POST /numbers/report` (authenticated, rate-limited, one vote per user per number, trust-weighted; shares logic with 15.7) | `NumberReportService` | 5 trusted reports mark a number SUSPICIOUS; 1 user × 20 reports does not | ⬜ |
| 16.6 | FE | `CallGuardService` (`CallScreeningService`), manifest entry, role request flow; graceful message on Android below 10 | `CallGuardService.kt`, `AndroidManifest` | Role granted on an Android 10+ device; a test call reaches `onScreenCall` | ⬜ |
| 16.7 | FE | Local decision engine: always-allow list → scam list → pattern rules → user mode; decision in <500 ms, fully offline | `CallGuardEngine.kt` | ≥30 unit-test cases pass (contact, starred, recent outgoing, scam, foreign, unknown, each mode) | ⬜ |
| 16.8 | FE | Settings screen: the 4 modes, always-allow list manager, clear warning text for "Reject all unknown" | `call_guard_settings_screen.dart` | Each mode changes the engine result; warning shown before enabling mode 4 | ⬜ |
| 16.9 | FE | Notification for each screened call: "Unknown number, reported 42 times, rejected" with buttons **Block number**, **Not spam**, **Report**; Android 13 notification permission handled | `CallNotifier.kt` | Notification appears for allowed, silenced and rejected calls; the three buttons work | ⬜ |
| 16.10 | FE | Blocked-calls log (local database, 30 days) with one-tap undo ("Not spam" adds to allow-list) | `blocked_calls_screen.dart`, `CallLogStore.kt` | Rejected call listed; undo moves number to allow-list; no number leaves the phone except on Report | ⬜ |
| 16.11 | FE | Caller label: saved contact name, else category label ("Foreign number", "Reported as bank fraud by N people", "Telemarketer series"), else system-provided display name if any | `CallerLabeler.kt` | Labels correct for 10 sample numbers; never claims a real name it does not have | ⬜ |
| 16.12 | FE | Report-to-government guide: copy the number and open the free government fraud-reporting portal (URL verified before release) | `report_number_screen.dart` | Button opens the portal; number copied to clipboard | ⬜ |
| 16.13 | FE | Real-device test: Android 10–14, Samsung/Xiaomi/Realme background limits, 5-second screening limit | Test matrix in `docs/` | Matrix filled; rejects work with the app closed | ⬜ |
| 16.14 | BE | Privacy audit: no call log, no full number, no caller identity stored or logged on the server; privacy-policy text updated | Review checklist | Log and DB grep clean; policy text reviewed | ⬜ |

---

## Cost rule and optional paid upgrades
**Rule:** nothing in Phases 10–16 needs a paid service. Free sources used: public phishing feeds, Tranco list, RDAP, free Safe Browsing and VirusTotal tiers (rate-limited, check their terms), the phone's own APIs, your own crowd reports, Android `CallScreeningService`.
**Unavoidable costs later:** server hosting (a free-tier VM works for a beta) and the one-time Google Play developer registration fee. Claude API use for explanations (11.6) costs money per call; the free fallback is the template explanations in 11.7, so the product works without it.
**Optional paid upgrades (not planned):** a commercial caller-ID/number-lookup data feed, a paid phishing-intelligence feed, a bigger LLM budget, managed hosting. Add them only if usage grows.

---

## Weekly schedule
| Week | Backend | Frontend |
|---|---|---|
| 1 | 0.1–0.4 | 0.5–0.6 |
| 2 | 1.1–1.5 | 2.2–2.4 |
| 3 | 1.6–1.9, 2.1 | 2.5–2.9 |
| 4 | 3.1–3.2 | 3.3–3.6 |
| 5 | 4.1–4.2 | 3.7–3.8, 4.5–4.6 |
| 6 | 4.3–4.4 | 4.7–4.9 |
| 7 | 5.1–5.2 | 4.10, 5.4–5.5 |
| 8 | 5.3, 6.1 | 5.6–5.8, 6.4–6.5 |
| 9 | 6.2–6.3, 7.1 | 6.6–6.8, 7.5–7.6 |
| 10 | 7.2–7.3 | 7.7–7.9 |
| 11 | 7.4, 8.1–8.2 | 7.10–7.11, 8.8 |
| 12 | 8.3–8.7 | 8.9–8.13 |
| 13 | 9.1–9.2 | 9.3–9.5 |
| 14 | buffer / fixes | 9.6–9.7 |
| 15–16 | 10.1–10.6 | – (verify the draft FE items) |
| 17 | 10.7–10.12, 11.1–11.2 | 10.11 |
| 18–19 | 11.3–11.9 | 11.10, 12.1 |
| 20–21 | 12.5–12.6 | 12.2–12.4, 12.7 |
| 22 | 13.1, 13.13 | 12.8–12.11, 13.2 |
| 23–24 | 13.3, 13.6, 13.7 | 13.4, 13.5, 13.8–13.9 |
| 25 | 13.14, 14.1 | 13.10–13.12 |
| 26 | 14.2, 14.6 | 14.3–14.5 |
| 27 | buffer | 14.7–14.10 |
| 28–32 | 15.1, 15.5, 15.7, 15.9 | 15.2–15.4, 15.6, 15.8, 15.10 |
| 33–34 | 16.1–16.5 | 16.6–16.8 |
| 35 | 16.14 | 16.9–16.13 |

> Phases 10–11 are backend-only, so the Java developer can start them now (Phase 1 is complete), while the Flutter developer verifies the existing 🟡 items. The phases above are a **sequence**; if you want the fraud-focus features sooner, swap Phase 10–14 ahead of Phases 6–9.

## Current position
- **Phase 0:** 0.1, 0.2, 0.4 ✅. 0.3 written, awaiting real GitHub run. 0.5–0.6 waiting on the Flutter Docker image download.
- **Phase 1: COMPLETE.** 1.1–1.9 all ✅ (23 automated tests pass). 
- **Phase 2:** complete: BE 2.1 and FE 2.2-2.9 all verified (106 Flutter tests + on-device checks).
- **Phase 3–7 frontend:** first-draft code exists (🟡); verification of the Flutter build/analyze/tests is in progress, then each 🟡 item is checked off against its "Done when".
- **Biggest known gap:** 7.11 (background service). Without it, checks stop when the app is closed.
- **New focus phases 10–15:** all ⬜ (not started). **Recommended next, since Phase 1 is complete:** 10.1 → 10.3 (brand registry + look-alike matcher), because it can be fully tested in Docker and gives the "Fleepkart is fake" result first.

## Decisions taken (change if you disagree)
| Topic | Decision | Reason |
|---|---|---|
| Message text privacy | On-device model first; cloud check only on explicit tap | Keeps the "message text never leaves the phone" promise |
| VPN | Promoted from v2 to core (Phase 12) | Needed to block fake sites in any app and to block leaking apps |
| AI sources | Public phishing datasets for training + Safe Browsing/VirusTotal as extra signals | No licence cost, still strong |
| AI role | ML + rules decide; LLM only explains | Consistent, cheap, hard to manipulate |
| Cost | Free data sources only; no paid API (decided by user) | Keeps the product free to run; paid upgrades listed as optional |
| Caller name | Contact name or category label; no paid name database | Real names are not available for free and crowd-uploaded phonebooks carry legal risk |
| "Block an app" | Block its internet + repeat warning + uninstall dialog; never claim to remove it | Android does not allow forced removal |

## v2 backlog
ML ad-abuse model, family dashboard, Telegram session listing via TDLib (opt-in), iOS companion (limited), JavaScript-rendering page fetcher for the look-alike engine (10.7 v2), browser extension for desktop.
