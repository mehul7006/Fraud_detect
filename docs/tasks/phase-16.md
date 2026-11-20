# Phase 16 – Call guard
## 16.1 [BE] Number reputation store
Goal: a table that holds hashed numbers with category, report count and trust score, and never a raw number.
Depends on: none
- [ ] 16.1.1 | Decide the number format and hash: normalise to E.164 (digits only, country code added), hash with SHA-256, hex lowercase; write the rule and 5 example inputs and outputs. Note that phone numbers are guessable, so the hash protects logs, not against brute force | `docs/number-hash-spec.md` | 2h
- [ ] 16.1.2 | Write the Flyway migration: columns `hash_prefix` (char 5), `hash_suffix` (char 59), `category`, `report_count`, `trust_score`, `updated_at`; primary key on the full hash; index on `hash_prefix`; no column for the raw number | `backend/src/main/resources/db/migration/V10__number_reputation.sql` (use next free V number; V3 in roadmap may already be taken) | 2h
- [ ] 16.1.3 | Add `NumberReputation` entity and `NumberReputationRepository` with `findByHashPrefix(String)` | `domain/NumberReputation.java`, `repo/Repositories.java` | 2h
- [ ] 16.1.4 | Create the shared helper `NumberHasher` (normalise + SHA-256 + split prefix/suffix) used by 15.7 and 16.5 | `service/NumberHasher.java` | 2h
- [ ] 16.1.5 | Unit test the helper with 10 formats (+91 98xxx, 098xxx, spaces, dashes) that must give the same hash | `NumberHasherTest.java` | 2h
- [ ] 16.1.6 | Add a test that reads the DB column list from `information_schema` and fails if any column name contains `number`, `phone` or `msisdn` | `NumberReputationSchemaIT.java` | 2h
- [ ] 16.1.v | Verify gate: migration applies and no raw phone column exists. Run `docker run --rm -v "E:/frud detection/backend":/app -v m2cache:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn verify` | Green `NumberReputationSchemaIT` and `NumberHasherTest` | 1h

## 16.2 [BE] Privacy-preserving range lookup
Goal: `GET /numbers/range/{prefix5}` returns only hash suffixes with category and count.
Depends on: 16.1
- [ ] 16.2.1 | Define response DTO: list of `{suffix, category, count}`; validate prefix is exactly 5 hex chars (else 400 in the standard error JSON) | `dto/Dtos.java` `NumberRangeResponse` | 1h
- [ ] 16.2.2 | Create `NumberController` with the GET endpoint; add path to `SecurityConfig` (authenticated, decide if anonymous is allowed and write the choice) | `controller/NumberController.java`, `security/SecurityConfig.java` | 3h
- [ ] 16.2.3 | Add `Cache-Control: max-age` header so repeat lookups of one prefix are served from the phone cache | `NumberController.java` | 1h
- [ ] 16.2.4 | Make sure no log line or access log prints the prefix together with user id; check `RequestIdFilter` and logging config | `RequestIdFilter.java`, `application.yml` | 2h
- [ ] 16.2.5 | Integration test: seed 3 hashes sharing a prefix; call; client code finds the suffix locally and returns the category | `NumberRangeIT.java` | 3h
- [ ] 16.2.6 | Privacy test: capture log output during the call and grep for the full hash and for the raw number; both must be absent | `NumberRangeIT.java` | 2h
- [ ] 16.2.v | Verify gate: server log and DB contain no full number and no caller identity. Run `mvn verify` in Docker, then `curl -H "Authorization: Bearer <token>" http://localhost:18080/api/v1/numbers/range/ab12c` | Test output and curl JSON | 1h

## 16.3 [BE] Free number-pattern rules
Goal: JSON rules for foreign codes, telemarketer series and odd lengths, served by `GET /config/number-rules`.
Depends on: none
- [ ] 16.3.1 | Verify the current TRAI telemarketer/service-call number series and the India numbering plan on the TRAI and DoT sites; write the verified list with source URLs and date. Do not guess prefixes | `docs/number-rules-sources.md` | 3h
- [ ] 16.3.2 | Write the rules JSON: version, risky country codes (configurable list), telemarketer series, min/max length, category and label for each rule | `backend/src/main/resources/rules/number-rules.json` | 2h
- [ ] 16.3.3 | Create `NumberRuleService` that loads the JSON and classifies a normalised number into a category or NONE | `service/NumberRuleService.java` | 4h
- [ ] 16.3.4 | Add `GET /api/v1/config/number-rules` returning the JSON with its version; support reload from an external file path set in `application.yml` so rules change without a release | `controller/ConfigController.java`, `application.yml` | 3h
- [ ] 16.3.5 | Write a test table of 30 sample numbers and expected categories | `src/test/resources/number-samples.csv`, `NumberRuleServiceTest.java` | 3h
- [ ] 16.3.6 | Test that changing the external rules file and reloading changes the result | `NumberRuleServiceTest.java` | 2h
- [ ] 16.3.v | Verify gate: 30 samples classified as expected; rules changeable without app release. Run `mvn test` in Docker and curl `http://localhost:18080/api/v1/config/number-rules` | Test output and JSON with version | 1h

## 16.4 [BE] Scam-number delta sync
Goal: signed, versioned delta of hashed scam numbers so the phone decides offline.
Depends on: 16.1
- [ ] 16.4.1 | Choose signature: Ed25519 or HMAC-less public-key signing so the app can verify with an embedded public key; write the choice and key handling (private key from env, never in repo) | `docs/signing-spec.md` | 2h
- [ ] 16.4.2 | Add a `version` (monotonic long) column or table so entries can be selected `since` a version | `V11__number_version.sql` | 2h
- [ ] 16.4.3 | Create `SignatureService` that signs a canonical byte form of the payload (reuse for 12.5 if it exists) | `service/SignatureService.java` | 3h
- [ ] 16.4.4 | Add `GET /api/v1/config/numbers?since=` returning `{version, entries:[{hash,category,count}], signature}`; include only SUSPICIOUS/SCAM entries; cap page size | `controller/ConfigController.java` | 4h
- [ ] 16.4.5 | Test: delta returns only new entries after `since` | `NumberDeltaIT.java` | 2h
- [ ] 16.4.6 | Test: signature verifies with the public key and fails after one byte is changed | `SignatureServiceTest.java` | 2h
- [ ] 16.4.v | Verify gate: delta returns only new entries; tampered payload fails signature check. Run `mvn verify` in Docker and curl `/config/numbers?since=0` then `?since=<latest>` | Test output and two curl results | 1h

## 16.5 [BE] Report a number
Goal: `POST /numbers/report` with one vote per user per number, rate limit and trust weighting.
Depends on: 16.1, 15.7
- [ ] 16.5.1 | Define request: hash of the number (computed on the phone), category; the server never receives the raw number | `dto/Dtos.java` `NumberReportRequest` | 1h
- [ ] 16.5.2 | Create `number_reports` table with unique (user_id, hash) and trust weight | `V12__number_reports.sql` | 2h
- [ ] 16.5.3 | Create `NumberReportService`: store vote, update `report_count` and `trust_score` using `TrustService` from 15.7 | `service/NumberReportService.java` | 4h
- [ ] 16.5.4 | Status rule: 5 trusted reports mark SUSPICIOUS; low-trust reports count less | `NumberReportService.java` | 2h
- [ ] 16.5.5 | Add rate limit (for example 10 reports per user per hour); reuse Bucket4j from 8.1 if done, else a simple in-memory limiter | `controller/NumberController.java`, `RateLimitFilter.java` | 3h
- [ ] 16.5.6 | Tests: 5 trusted users mark SUSPICIOUS; 1 user x 20 reports stays at one vote; 11th report in an hour gives 429 | `NumberReportIT.java` | 3h
- [ ] 16.5.v | Verify gate: 5 trusted reports mark a number SUSPICIOUS; 1 user x 20 does not. Run `mvn verify` in Docker | Green `NumberReportIT` | 1h

## 16.6 [FE] CallGuardService and role request
Goal: FraudShield becomes the call-screening app and receives `onScreenCall` for incoming calls.
Depends on: none
- [ ] 16.6.1 | Verify in current Android docs: `RoleManager.ROLE_CALL_SCREENING` (API 29) and the manifest rules for `CallScreeningService`; note the exact API level and intent | Note in `docs/call-guard-notes.md` | 1h
- [ ] 16.6.2 | Create `CallGuardService.kt` extending `CallScreeningService`; in `onScreenCall` log the call and respond with allow for now | `mobile/android-native/kotlin/CallGuardService.kt` | 2h
- [ ] 16.6.3 | Add the service to the manifest with `android.permission.BIND_SCREENING_SERVICE` and the `android.telecom.CallScreeningService` intent filter | `mobile/android/app/src/main/AndroidManifest.xml` | 1h
- [ ] 16.6.4 | Add role request in `MainActivity.kt` (`RoleManager.createRequestRoleIntent`) and result handling; methods `isCallGuardRoleHeld` and `requestCallGuardRole` | `MainActivity.kt` | 3h
- [ ] 16.6.5 | Add Dart wrappers and a dashboard card "Turn on call guard" that disappears when the role is held | `native_bridge.dart`, `dashboard_screen.dart` | 3h
- [ ] 16.6.6 | Add graceful message for Android 9 and below ("Call guard needs Android 10 or newer") by checking `Build.VERSION.SDK_INT` | `MainActivity.kt`, `dashboard_screen.dart` | 1h
- [ ] 16.6.7 | Add the READ_CONTACTS permission request flow with a plain-language reason (needed for the always-allow list) | `MainActivity.kt`, `dashboard_screen.dart` | 2h
- [ ] 16.6.v | Verify gate: role granted on an Android 10+ device; a test call (from a second phone) logs `onScreenCall` in `adb logcat -s CallGuard` | Logcat extract | 1h

## 16.7 [FE] Local decision engine
Goal: decide allow, silence, reject or warn in under 500 ms, fully offline.
Depends on: 16.6
- [ ] 16.7.1 | Define the decision model: input (normalised number, mode, flags) and output (action, reason code, label source) | `mobile/android-native/kotlin/CallGuardEngine.kt` data classes | 1h
- [ ] 16.7.2 | Always-allow checks: saved contact (ContactsContract lookup by number), starred contact, numbers dialled in the last N days (CallLog outgoing; needs READ_CALL_LOG, check Play policy and ask only if needed) | `CallGuardEngine.kt` `AllowList` | 4h
- [ ] 16.7.3 | Scam list check: local table of hashes from 16.4 in SQLite, lookup by full hash | `ScamNumberStore.kt` | 3h
- [ ] 16.7.4 | Pattern rules check: load cached `number-rules.json` from 16.3 with a built-in fallback copy in assets | `NumberPatternRules.kt`, `assets/number-rules.json` | 3h
- [ ] 16.7.5 | User-mode logic for the 4 modes (warn only, silence unknown, reject known scam only, reject all unknown) in the fixed check order | `CallGuardEngine.kt` `decide()` | 3h
- [ ] 16.7.6 | Write at least 30 unit-test cases: contact, starred, recent outgoing, scam, foreign, telemarketer, unknown, each of 4 modes, empty/private number | `mobile/android-native/test/CallGuardEngineTest.kt` (create test folder or move to the Android module test dir) | 4h
- [ ] 16.7.7 | Measure decision time with 10000-entry scam table on a mid-range phone | Log with average and max ms | 1h
- [ ] 16.7.v | Verify gate: >=30 unit-test cases pass, decision <500 ms, works in airplane mode. Run unit tests with the Flutter/Android Docker image or on device and show timing log | Test output and timing log | 2h

## 16.8 [FE] Settings screen
Goal: user picks one of 4 modes and manages the always-allow list.
Depends on: 16.7
- [ ] 16.8.1 | Create screen with 4 radio options and a one-line explanation each | `mobile/lib/screens/call_guard_settings_screen.dart` | 3h
- [ ] 16.8.2 | Store the mode in SharedPreferences readable by native code (use the same prefs file name as Kotlin) | `call_guard_settings_screen.dart`, `CallGuardEngine.kt` | 2h
- [ ] 16.8.3 | Add warning dialog before enabling mode 4: "Doctors, delivery and banks may be blocked"; require explicit confirm | `call_guard_settings_screen.dart` | 2h
- [ ] 16.8.4 | Add always-allow list manager (add number, remove number, import from contacts shortcut) stored locally | `call_guard_settings_screen.dart`, `AllowListStore.kt` | 4h
- [ ] 16.8.5 | Link the screen from the dashboard | `dashboard_screen.dart` | 1h
- [ ] 16.8.6 | Widget test: each mode is saved; mode 4 shows the warning and cancel keeps the old mode | `mobile/test/call_guard_settings_test.dart` | 2h
- [ ] 16.8.v | Verify gate: each mode changes the engine result; warning shown before mode 4. Run `flutter test test/call_guard_settings_test.dart` in `ghcr.io/cirruslabs/flutter:stable` plus a device test | Test output and screen recording | 1h

## 16.9 [FE] Notification for each screened call
Goal: tell the user what happened to each call with Block, Not spam and Report buttons.
Depends on: 16.7
- [ ] 16.9.1 | Create `CallNotifier.kt` with a separate notification channel for call guard | `mobile/android-native/kotlin/CallNotifier.kt` | 2h
- [ ] 16.9.2 | Build the text from the decision, e.g. "Unknown number, reported 42 times, rejected"; use the label from 16.11 when ready | `CallNotifier.kt` | 2h
- [ ] 16.9.3 | Add three action buttons through PendingIntents to a `BroadcastReceiver` | `CallActionReceiver.kt` | 3h
- [ ] 16.9.4 | Implement Block number (add to local block list) and Not spam (add to allow-list) | `CallActionReceiver.kt`, `AllowListStore.kt` | 2h
- [ ] 16.9.5 | Implement Report: open the app at the report screen with the number (see 16.12); hash is sent only after the user confirms | `CallActionReceiver.kt`, `main.dart` route | 3h
- [ ] 16.9.6 | Handle Android 13 `POST_NOTIFICATIONS` permission request and a fallback when it is denied | `MainActivity.kt`, `dashboard_screen.dart` | 2h
- [ ] 16.9.7 | Test notification text for allowed, silenced and rejected calls | `CallNotifierTest.kt` | 2h
- [ ] 16.9.v | Verify gate: a notification appears for allowed, silenced and rejected calls; all three buttons work | Screen recording of 3 test calls | 2h

## 16.10 [FE] Blocked-calls log with undo
Goal: 30-day local log of screened calls with one-tap undo.
Depends on: 16.9
- [ ] 16.10.1 | Create `CallLogStore.kt` with an SQLite table (time, number hash, display number, action, reason); display number is stored only on the phone | `mobile/android-native/kotlin/CallLogStore.kt` | 3h
- [ ] 16.10.2 | Write a log entry from `CallGuardService.onScreenCall` after each decision | `CallGuardService.kt` | 1h
- [ ] 16.10.3 | Delete entries older than 30 days (on app start and daily) | `CallLogStore.kt` | 1h
- [ ] 16.10.4 | MethodChannel `blockedCalls()` and `undoBlock(number)` with Dart wrappers | `MainActivity.kt`, `native_bridge.dart` | 2h
- [ ] 16.10.5 | Create screen with the list, filter by action, and Undo button which adds to allow-list | `mobile/lib/screens/blocked_calls_screen.dart` | 3h
- [ ] 16.10.6 | Network check: run the app with a proxy and show that no number or hash is sent while screening and undoing (only on Report) | Proxy log note in `docs/` | 2h
- [ ] 16.10.v | Verify gate: rejected call listed; undo moves number to allow-list; no number leaves the phone except on Report | Screen recording and empty proxy log | 1h

## 16.11 [FE] Caller label
Goal: show a truthful label: contact name, category label, or system display name.
Depends on: 16.7
- [ ] 16.11.1 | Write the label rules in order: contact name, scam-list category with count, pattern category ("Foreign number", "Telemarketer series"), system-supplied display name if present, else "Unknown number" | `CallerLabeler.kt` header comment | 1h
- [ ] 16.11.2 | Get the contact name via `ContactsContract.PhoneLookup` | `mobile/android-native/kotlin/CallerLabeler.kt` | 2h
- [ ] 16.11.3 | Read the system display name from `Call.Details` (`getCallerDisplayName`, check availability and API level) | `CallGuardService.kt`, `CallerLabeler.kt` | 2h
- [ ] 16.11.4 | Look up the report count from the local scam store (count stored with the hash in 16.4) | `ScamNumberStore.kt`, `CallerLabeler.kt` | 2h
- [ ] 16.11.5 | Test with 10 sample numbers incl. one that must give "Unknown number" and never a made-up name | `CallerLabelerTest.kt` | 2h
- [ ] 16.11.6 | Use the label in `CallNotifier` and in the blocked-calls list | `CallNotifier.kt`, `blocked_calls_screen.dart` | 1h
- [ ] 16.11.v | Verify gate: labels correct for 10 sample numbers; no real name is ever invented | Test output | 1h

## 16.12 [FE] Report-to-government guide
Goal: copy the number and open the government fraud-reporting portal.
Depends on: none
- [ ] 16.12.1 | Verify the current government reporting portals (for example the Sanchar Saathi / cybercrime.gov.in pages and the 1930 helpline) and what each one accepts; write URLs and date | `docs/report-portals.md` | 2h
- [ ] 16.12.2 | Create screen: shows number, short steps, "Copy number" and "Open portal" buttons | `mobile/lib/screens/report_number_screen.dart` | 3h
- [ ] 16.12.3 | Implement clipboard copy with a confirmation message | `report_number_screen.dart` | 1h
- [ ] 16.12.4 | Open the portal in the browser through an intent (add the URL list as a constant that can later come from config) | `NativeBridge` `openUrl`, `MainActivity.kt` | 2h
- [ ] 16.12.5 | Add an optional second button "Also report to FraudShield community" that calls 16.5 with the hash after explicit confirm | `report_number_screen.dart`, `api_client.dart` `reportNumber` | 3h
- [ ] 16.12.6 | Widget test: copy puts the number on the clipboard; portal button calls the opener with the verified URL | `mobile/test/report_number_test.dart` | 2h
- [ ] 16.12.v | Verify gate: button opens the portal; number copied to clipboard. Run `flutter test test/report_number_test.dart` in the Flutter Docker image, then check on device | Test output and recording | 1h

## 16.13 [FE] Real-device test
Goal: prove call guard works on Android 10-14 and on aggressive-battery brands with the app closed.
Depends on: 16.6, 16.7, 16.8, 16.9, 16.10, 16.11
- [ ] 16.13.1 | Create the test matrix template: device, Android version, role grant, allow, silence, reject, app closed, after reboot, notes | `docs/call-guard-test-matrix.md` | 1h
- [ ] 16.13.2 | Prepare 6 test numbers (contact, starred, scam-list entry, foreign, unknown, telemarketer series) | Test plan section in the matrix | 1h
- [ ] 16.13.3 | Run the matrix on Android 10 and 11 devices | Filled matrix rows | 3h
- [ ] 16.13.4 | Run on Android 12, 13 and 14 devices (check notification permission on 13+) | Filled matrix rows | 3h
- [ ] 16.13.5 | Run on Samsung, Xiaomi and Realme with the app swiped away and battery saver on; write the settings the user must change (autostart, battery) | Matrix rows plus `docs/battery-settings-guide.md` | 4h
- [ ] 16.13.6 | Measure decision time and check it is far below the 5-second screening limit (check the limit in the Android docs) | Timing log in the matrix | 2h
- [ ] 16.13.7 | File and fix the bugs found | Jira list, fixes | 4h
- [ ] 16.13.v | Verify gate: matrix filled and rejects work with the app closed | Completed `docs/call-guard-test-matrix.md` | 1h

## 16.14 [BE] Privacy audit
Goal: show that the server stores and logs no call log, no full number and no caller identity.
Depends on: 16.2, 16.4, 16.5
- [ ] 16.14.1 | Write the audit checklist: every endpoint of Phase 16, every table, every log line, every error response | `docs/call-guard-privacy-checklist.md` | 2h
- [ ] 16.14.2 | Run all Phase 16 API calls with a marker number and capture the container log | Log file in the scratch folder | 2h
- [ ] 16.14.3 | Grep logs for the raw number, the full hash and the user id next to a prefix | `grep` command and empty result recorded in the checklist | 1h
- [ ] 16.14.4 | Dump the DB schema and rows and grep for the marker number (`pg_dump` in the Postgres container) | Empty result recorded in the checklist | 2h
- [ ] 16.14.5 | Add an automated test that runs a marker number through the endpoints and fails if it appears in logs or DB | `CallGuardPrivacyIT.java` | 3h
- [ ] 16.14.6 | Update the privacy-policy text for call guard (what the phone sends, what the server never sees) and have it reviewed | `docs/privacy-policy-call-guard.md` | 2h
- [ ] 16.14.v | Verify gate: log and DB grep clean; policy text reviewed. Run `mvn verify` in Docker | Green `CallGuardPrivacyIT`, completed checklist, reviewer name and date | 1h
