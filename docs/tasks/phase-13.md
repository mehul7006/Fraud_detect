# Phase 13 – Leaky / fraud app protection
## 13.1 [BE] App reputation v2 (extends 6.2)
Goal: server table that says if a package is known good, known bad, or unknown, with signer cert, category and trackers.
Depends on: 6.2
- [ ] 13.1.1 | Check whether 6.2 `app_reputation` exists; if not, create it first. List the columns still missing (signer_cert_hash, category, trackers, verdict) | Short note in the PR plus `V4__app_reputation_v2.sql` column list | 1h
- [ ] 13.1.2 | Write Flyway migration adding signer_cert_hash, category, trackers (text array or JSON), verdict, source, updated_at | `backend/src/main/resources/db/migration/V4__app_reputation_v2.sql` (use next free V number) | 2h
- [ ] 13.1.3 | Add `AppReputation` entity and `AppReputationRepository` (find by package, find by package list) | `domain/AppReputation.java`, `repo/Repositories.java` | 2h
- [ ] 13.1.4 | Build seed file: top 500 apps (package, category, verdict GOOD) from the Play Store pages you check by hand; verify each package name before adding; add a small known-bad list from public reports and record the source of each row | `backend/src/main/resources/seed/app-reputation.csv` | 4h
- [ ] 13.1.5 | Load the seed CSV at startup (idempotent upsert) | `AppReputationSeeder.java` | 2h
- [ ] 13.1.6 | Write unit/integration tests: known-good never flagged, known-bad returns MALICIOUS, unknown returns UNKNOWN | `AppReputationTest.java` | 2h
- [ ] 13.1.v | Verify gate: known-good apps never flagged; known-bad package returns MALICIOUS. Run `docker run --rm -v "E:/frud detection/backend":/app -v m2cache:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn verify` | Green output with `AppReputationTest` listed | 1h

## 13.2 [FE] Installed-app inventory
Goal: full list of installed apps with permissions, installer, signing cert, install time and target SDK.
Depends on: none
- [ ] 13.2.1 | Add installer source (Play vs sideload) to `AppAuditor.audit` using `getInstallSourceInfo` on API 30+ and `getInstallerPackageName` below; verify the API level in current Android docs | Edit `mobile/android-native/kotlin/AppAuditor.kt` | 2h
- [ ] 13.2.2 | Add signing-cert SHA-256 using `GET_SIGNING_CERTIFICATES` (API 28+) with a fallback for older versions | `AppAuditor.kt` function `certHash(pkg)` | 3h
- [ ] 13.2.3 | Add requested vs granted permission lists, firstInstallTime, lastUpdateTime, targetSdk | `AppAuditor.kt` | 2h
- [ ] 13.2.4 | Add a new MethodChannel call `appInventory` in `MainActivity.kt` and a wrapper in `native_bridge.dart` (keep `auditApps` unchanged) | `MainActivity.kt`, `native_bridge.dart` `appInventory()` | 2h
- [ ] 13.2.5 | Write a script that dumps `adb shell pm dump <pkg>` fields for 10 test apps and a Dart test that compares them with the inventory JSON sample | `mobile/test/app_inventory_test.dart`, `docs/` compare notes | 3h
- [ ] 13.2.v | Verify gate: inventory JSON matches `adb shell pm` for 10 test apps | Table of 10 apps (field, adb value, app value) all equal, attached to Jira | 2h

## 13.3 [BE] Permission-vs-purpose rules
Goal: score an app by whether its permissions fit its category, and extend `POST /scan/apps`.
Depends on: 13.1
- [ ] 13.3.1 | Define the category-to-expected-permission table as JSON (torch, calculator, wallpaper, game, shopping, bank, messaging...) and the rule weights | `backend/src/main/resources/rules/permission-purpose.json` | 3h
- [ ] 13.3.2 | Extend `AppInfo` DTO with fields: requested permission list, installer, signerCertHash, targetSdk (all optional so old clients still work) | `dto/Dtos.java` | 2h
- [ ] 13.3.3 | Create `AppPrivacyService` that loads the JSON and returns a score plus reason codes per app (SIDELOADED_SMS_OVERLAY_ACCESSIBILITY = critical) | `service/AppPrivacyService.java` | 4h
- [ ] 13.3.4 | Call it from `ScanController.apps` next to `AdAbuseService`; keep the old response fields | `controller/ScanController.java` | 2h
- [ ] 13.3.5 | Unit test: 20 sample apps scored as expected | `AppPrivacyServiceTest.java` | 3h
- [ ] 13.3.6 | Label 50 apps (benign, leaky, fake) in a CSV and add a precision test (shared with 13.14) | `src/test/resources/app-corpus.csv` | 3h
- [ ] 13.3.v | Verify gate: 20 samples as expected; precision >=90% on 50 labelled apps. Run `mvn test` in Docker | Test report showing the precision number | 1h

## 13.4 [FE] Tracker-SDK scan
Goal: show which tracker SDKs (Facebook, AppsFlyer...) are inside an installed app.
Depends on: 13.2
- [ ] 13.4.1 | Verify the licence and terms of the Exodus Privacy tracker list before copying anything; decide to use it or build your own list | Licence note in `docs/` | 1h
- [ ] 13.4.2 | Create `trackers.json` with about 400 entries: name, class prefix, category | `mobile/android/app/src/main/assets/trackers.json` (check where assets live in this project) | 4h
- [ ] 13.4.3 | Write `TrackerScanner.kt`: read class names of the APK (`DexFile` or parse dex from `sourceDir`) and match prefixes | `mobile/android-native/kotlin/TrackerScanner.kt` | 4h
- [ ] 13.4.4 | Run the scan on a background thread, cache result per package + version code | `TrackerScanner.kt` cache | 2h
- [ ] 13.4.5 | Expose via MethodChannel `trackersFor(pkg)` and `native_bridge.dart` | `MainActivity.kt`, `native_bridge.dart` | 1h
- [ ] 13.4.6 | Test with a test APK that includes the Facebook SDK and one with AppsFlyer | Screenshot/log of names found | 2h
- [ ] 13.4.v | Verify gate: app with Facebook/AppsFlyer SDK shows them by name | Log line `trackers=[Facebook SDK, AppsFlyer]` for the test app | 1h

## 13.5 [FE] Network-behaviour analyser
Goal: flag apps that talk to many third-party ad/analytics hosts or upload a lot in the background.
Depends on: 12.8, 13.6
- [ ] 13.5.1 | Define the 3 rules and thresholds in a config (>=10 third-party domains/day, unknown analytics hosts, background upload above X MB) | `AppNetworkAnalyzer.kt` constants plus note | 1h
- [ ] 13.5.2 | Read 7-day rows from `TrafficStore.kt` (created in 12.8) | `TrafficStore.kt` query `last7Days(pkg)` | 2h
- [ ] 13.5.3 | Match domains against the cached domain-category list from 13.6 | `AppNetworkAnalyzer.kt` | 2h
- [ ] 13.5.4 | Write analyser returning reason codes per app | `AppNetworkAnalyzer.kt` | 3h
- [ ] 13.5.5 | Unit test with fake traffic rows: one app with 10 ad domains, one normal app | `AppNetworkAnalyzerTest.kt` | 2h
- [ ] 13.5.v | Verify gate: test app sending to 10 ad domains is flagged; normal app is not | Test output | 1h

## 13.6 [BE] Domain categorisation list
Goal: serve a list of domains labelled ads / analytics / tracker / malware with delta sync.
Depends on: none
- [ ] 13.6.1 | Pick free list sources (for example public ad/tracker host lists); verify each licence and record it | `docs/DATA-LICENSES-domains.md` | 2h
- [ ] 13.6.2 | Migration for `domain_categories` (domain, category, source, version, updated_at) | `V5__domain_categories.sql` | 2h
- [ ] 13.6.3 | Import script/loader that normalises and de-duplicates the lists | `DomainCategoryImporter.java` plus `seed/domains.csv` | 3h
- [ ] 13.6.4 | Add `GET /api/v1/config/domains?since=` returning only rows newer than version | `ConfigController.java` (create if 12.5 not done) | 3h
- [ ] 13.6.5 | Add the endpoint to `SecurityConfig` rules and the API spec | `SecurityConfig.java`, list the new endpoint in a note for the API-spec owner | 1h
- [ ] 13.6.6 | Integration test: known ad domain returns `ads`; delta returns only new rows | `DomainCategoryIT.java` | 2h
- [ ] 13.6.v | Verify gate: known ad domain returns category `ads` | curl output on port 18080 | 1h

## 13.7 [BE] Combined app privacy score
Goal: one score and plain reasons (en/hi) per app from permissions, trackers, traffic and sideload.
Depends on: 13.3, 13.6
- [ ] 13.7.1 | Add optional input fields: trackers[], trafficSummary (counts only), sideloaded | `dto/Dtos.java` | 2h
- [ ] 13.7.2 | Define the score formula and the cap per factor; write it as a table in code comments | `AppPrivacyService.java` | 2h
- [ ] 13.7.3 | Add reason codes and en/hi texts (check Hindi wording with a native speaker) | `explanations_apps_en.properties`, `explanations_apps_hi.properties` | 3h
- [ ] 13.7.4 | Return `{score, verdict, reasonCodes[], reasons[]}` per app in `AppsScanResponse` | `Dtos.java`, `ScanController.java` | 2h
- [ ] 13.7.5 | Test: every reason code has en and hi text; old fields still present | `AppPrivacyServiceTest.java` | 2h
- [ ] 13.7.v | Verify gate: response has all four fields for each app | curl output with 3 sample apps | 1h

## 13.8 [FE] Risky-app detail screen
Goal: one screen per risky app with what it can access, who it talks to, and 4 action buttons.
Depends on: 13.2, 13.7, 13.9
- [ ] 13.8.1 | Create screen skeleton with app name, icon (from native), score and reasons | `mobile/lib/screens/app_detail_screen.dart` | 3h
- [ ] 13.8.2 | Add section "What it can access" from the granted permission list in plain words | `app_detail_screen.dart` | 2h
- [ ] 13.8.3 | Add section "Who it talks to (last 7 days)" from `TrafficStore` via bridge | `native_bridge.dart` `trafficFor(pkg)`, screen | 3h
- [ ] 13.8.4 | Wire Uninstall (existing `NativeBridge.uninstallApp`) and Revoke permissions (deep link to app settings, new `openAppSettings(pkg)` in `MainActivity.kt`) | `MainActivity.kt`, `native_bridge.dart` | 3h
- [ ] 13.8.5 | Wire Block internet to 13.9 and add Ignore stored locally (SharedPreferences set) | `app_detail_screen.dart` | 2h
- [ ] 13.8.6 | Widget test: all four buttons are present and Ignore persists | `mobile/test/app_detail_screen_test.dart` | 2h
- [ ] 13.8.v | Verify gate: every button works on a test device; Ignore remembered after app restart | Screen recording | 1h

## 13.9 [FE] Per-app network block inside the VPN
Goal: drop packets of a chosen app (by UID) so it has no internet; others unaffected.
Depends on: 12.1, 12.2
- [ ] 13.9.1 | Decide the method: `addDisallowedApplication` per app (excluded from VPN) vs packet drop by UID in the TUN loop; check which one really cuts internet and note it | Design note in `docs/` | 2h
- [ ] 13.9.2 | Create `AppFirewall.kt` with a persisted set of blocked packages (SharedPreferences or SQLite) | `mobile/android-native/kotlin/AppFirewall.kt` | 2h
- [ ] 13.9.3 | Hook the block check into the packet loop of `FraudVpnService.kt` using `UidResolver.kt` | `FraudVpnService.kt` | 4h
- [ ] 13.9.4 | Remove entries automatically when a package is uninstalled (`PACKAGE_REMOVED` receiver) | `AppFirewall.kt`, `InstallReceiver.kt` | 2h
- [ ] 13.9.5 | Re-apply blocks after reboot / VPN restart | `FraudVpnService.kt` | 2h
- [ ] 13.9.6 | MethodChannel `blockApp(pkg)` / `unblockApp(pkg)` / `blockedApps()` and Dart wrapper | `MainActivity.kt`, `native_bridge.dart` | 2h
- [ ] 13.9.7 | Unit test the firewall decision with fake UIDs | `AppFirewallTest.kt` | 2h
- [ ] 13.9.v | Verify gate: blocked test app has no connectivity; Chrome still works; still blocked after reboot | Screen recording plus adb log | 2h

## 13.10 [FE] Repeat-warning scheduler
Goal: warn at detection, +1 h, then daily, with a frequency cap, until the app is fixed.
Depends on: 13.2, 13.7
- [ ] 13.10.1 | Define the schedule and cap (for example max 3 reminders/day across all apps) as constants and write the rules | `WarnScheduler.kt` constants | 1h
- [ ] 13.10.2 | Store per-app warning state (first seen, last warned, count) | `WarnScheduler.kt` using SharedPreferences | 2h
- [ ] 13.10.3 | Schedule with WorkManager (check inexact-alarm limits on Android 12+) | `WarnScheduler.kt` | 3h
- [ ] 13.10.4 | Show reminder through `AlertNotifier.show` with escalated text | `AlertNotifier.kt` | 2h
- [ ] 13.10.5 | Cancel on uninstall or Ignore | `WarnScheduler.kt`, `InstallReceiver.kt` | 2h
- [ ] 13.10.6 | Unit test of the schedule with an injectable clock | `WarnSchedulerTest.kt` | 3h
- [ ] 13.10.v | Verify gate: reminders fire on schedule (use shortened test times), cap respected, stops when app removed | Log extract with timestamps | 2h

## 13.11 [FE] New-install watcher
Goal: scan a newly installed app within 10 s and alert.
Depends on: 13.2, 13.7
- [ ] 13.11.1 | Check Android rules for `PACKAGE_ADDED` (manifest receivers for implicit broadcasts) and pick registration method | Note in `docs/` | 1h
- [ ] 13.11.2 | Create `InstallReceiver.kt` and register it | `mobile/android-native/kotlin/InstallReceiver.kt`, manifest entry | 2h
- [ ] 13.11.3 | On event, build inventory for that one package and call the backend `/scan/apps` (through Flutter engine or a headless call) | `InstallReceiver.kt`, `protection_engine.dart` `checkApp(pkg)` | 4h
- [ ] 13.11.4 | Build the alert text "Just installed X; it wants ..." from permissions | `AlertNotifier.kt` | 2h
- [ ] 13.11.5 | Skip FraudShield itself and apps on the Ignore list | `InstallReceiver.kt` | 1h
- [ ] 13.11.v | Verify gate: installing a test APK raises an alert in <10 s | `adb install` timestamp vs notification time | 1h

## 13.12 [FE] Fake-app check
Goal: flag apps that look like a known brand but have the wrong package or signing cert.
Depends on: 13.2, 13.13
- [ ] 13.12.1 | Fetch official-app registry (from 13.13) and cache locally | `FakeAppDetector.kt` plus `GET` call in `api_client.dart` | 3h
- [ ] 13.12.2 | Match app label to brand names (normalised, homoglyph-safe) | `FakeAppDetector.kt` | 3h
- [ ] 13.12.3 | If label matches a brand but package or cert hash is not official, return FAKE_APP | `FakeAppDetector.kt` | 2h
- [ ] 13.12.4 | Optional icon similarity: decide if v1 skips it; write the decision | Note in code comment | 1h
- [ ] 13.12.5 | Unit test with a fake "PhonePe" (wrong package) and the real one | `FakeAppDetectorTest.kt` | 2h
- [ ] 13.12.v | Verify gate: fake PhonePe flagged; real PhonePe not | Test run on device with both APKs | 2h

## 13.13 [BE] Official app registry
Goal: table of official package names and signing-cert hashes for 100+ apps.
Depends on: none
- [ ] 13.13.1 | Pick the 100+ apps (banks, UPI, shopping, social) and write the list | `seed/official-apps-list.csv` (brand, package) | 2h
- [ ] 13.13.2 | Collect the real signing-cert SHA-256 for each from an official install (`apksigner verify --print-certs` in a Docker/SDK tool image); never copy from random sites | `seed/official-apps.csv` | 4h
- [ ] 13.13.3 | Migration for `official_apps` (brand, package, cert_hash, source_note) | `V6__official_apps.sql` | 2h
- [ ] 13.13.4 | Seed loader and repository `findByPackageName` | `OfficialAppRepository`, `OfficialAppSeeder.java` | 2h
- [ ] 13.13.5 | Add `GET /api/v1/config/official-apps` for the phone to cache | `ConfigController.java` | 2h
- [ ] 13.13.6 | Test: official package returns brand+cert, fake package returns none | `OfficialAppIT.java` | 2h
- [ ] 13.13.v | Verify gate: lookup by package returns brand + cert; fake package returns none | curl output on port 18080 | 1h

## 13.14 [BE] Evaluation set for app scoring
Goal: measurable accuracy report on 50 labelled apps.
Depends on: 13.3, 13.7
- [ ] 13.14.1 | Finish the 50 labelled rows (benign, leaky, fake) with the permission fields | `src/test/resources/app-corpus.csv` | 3h
- [ ] 13.14.2 | Write `AppAccuracyTest` that runs `AppPrivacyService` over the CSV | `src/test/java/com/fraudshield/AppAccuracyTest.java` | 3h
- [ ] 13.14.3 | Compute precision and recall and print the false-positive/false-negative list to a report file | `target/app-accuracy-report.txt` | 2h
- [ ] 13.14.4 | Add assertions: precision >=90%, recall >=85% | `AppAccuracyTest.java` | 1h
- [ ] 13.14.5 | Tune weights if below target and re-run | `AppPrivacyService.java`, `permission-purpose.json` | 3h
- [ ] 13.14.v | Verify gate: precision >=90%, recall >=85%, false positives listed | `mvn test` in Docker output and the report file | 1h
