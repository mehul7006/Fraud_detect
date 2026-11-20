# Phase 15 – Public-safety extras
## 15.1 [BE] Live-scam correlation
Goal: four scam signals inside a time window raise one CRITICAL threat; fewer signals do not.
Depends on: 7.2
- [ ] 15.1.1 | Read how `TakeoverService` (7.2) correlates events and decide how `ScamSessionService` reuses it | Design note in PR | 1h
- [ ] 15.1.2 | Define the 4 signal types (LONG_UNKNOWN_CALL, REMOTE_ACCESS_APP, BANKING_APP_OPEN, OTP_NOTIFICATION) as counters-only DTO and add `ThreatType.SCAM_SESSION` | `domain/Enums.java`, `dto/Dtos.java` | 2h
- [ ] 15.1.3 | Migration for a small `scam_signals` table (device id, type, time; no content, auto-expire) | `V7__scam_signals.sql` | 2h
- [ ] 15.1.4 | Add `POST /api/v1/signals/scam` endpoint to receive one signal | `controller/SignalController.java` | 2h
- [ ] 15.1.5 | Write `ScamSessionService`: count distinct signal types in a 10-minute window; 4 gives CRITICAL, 2 gives LOW | `service/ScamSessionService.java` | 4h
- [ ] 15.1.6 | Create the threat once per window (de-dup like 1.3 open threats) | `ScamSessionService.java`, `ThreatService.java` | 2h
- [ ] 15.1.7 | Unit tests with a fake clock: 4 in 10 min, 2 in 10 min, 4 in 30 min | `ScamSessionServiceTest.java` | 3h
- [ ] 15.1.v | Verify gate: four simulated events in 10 min raise one CRITICAL threat; two stay below. Run `mvn verify` in Docker plus curl 4 posts on port 18080 and `GET /threats` | Test output and curl output | 1h

## 15.2 [FE] Signals for live-scam detection
Goal: collect the 4 signals on the phone and send only counters.
Depends on: 15.1
- [ ] 15.2.1 | Check which Android APIs can see call state (`PHONE_STATE` permission) and foreground app (UsageStats, opt-in); write the permission list | Note in `docs/` | 1h
- [ ] 15.2.2 | Create `ScamSignalCollector.kt` with call-state listener: long call (over N minutes) from number not in contacts, only a flag is kept | `mobile/android-native/kotlin/ScamSignalCollector.kt` | 4h
- [ ] 15.2.3 | Add foreground-app check via UsageStats for remote-access apps (AnyDesk, TeamViewer, QuickSupport; verify package names) and banking apps (use 13.13 registry) | `ScamSignalCollector.kt` | 4h
- [ ] 15.2.4 | Hook OTP-notification signal from `AdNotificationListener.kt` (flag only, no code) | `AdNotificationListener.kt` | 2h
- [ ] 15.2.5 | Send signals through `api_client.dart` and `protection_engine.dart`; payload has type and timestamp only | `api_client.dart` `sendScamSignal` | 2h
- [ ] 15.2.6 | Opt-in screen for Usage Access with plain-language reason | `dashboard_screen.dart` card | 2h
- [ ] 15.2.7 | Test: capture the HTTP body and show it has no number, no app text | Proxy log | 1h
- [ ] 15.2.v | Verify gate: all four signals reach the backend as counters, no content | Backend table rows plus request body capture | 1h

## 15.3 [FE] Full-screen STOP warning
Goal: a Hindi/English warning with voice read-out shown over the banking app.
Depends on: 15.2
- [ ] 15.3.1 | Write warning text in English and Hindi (short, plain), review with a non-technical user | `l10n/` strings | 1h
- [ ] 15.3.2 | Create screen with big STOP, 3 short steps, and a "Call 1930" button (verify the helpline number) | `mobile/lib/screens/scam_stop_screen.dart` | 3h
- [ ] 15.3.3 | Show over other apps with a full-screen-intent notification (reuse `AlertNotifier.kt`; check Android 14 permission as in 7.10) | `AlertNotifier.kt` | 3h
- [ ] 15.3.4 | Add voice read-out with Android TextToSpeech (check Hindi voice availability; fall back to English) | `MainActivity.kt` `speak`, `native_bridge.dart` | 3h
- [ ] 15.3.5 | Widget test: screen shows both languages and calls speak once | `mobile/test/scam_stop_screen_test.dart` | 2h
- [ ] 15.3.v | Verify gate: shows over a banking app; spoken warning plays | Screen recording with audio | 1h

## 15.4 [FE] OTP-share guard
Goal: warn "do not read this out" when an OTP arrives during a call; never store the code.
Depends on: 15.2
- [ ] 15.4.1 | Add OTP-pattern detection (keywords "OTP", "one time password", digit group) that only produces a boolean | `AdNotificationListener.kt` | 2h
- [ ] 15.4.2 | Check call-active state from `ScamSignalCollector.kt` | `ScamSignalCollector.kt` `isCallActive()` | 2h
- [ ] 15.4.3 | Raise the warning through `AlertNotifier` when both are true | `AdNotificationListener.kt` | 2h
- [ ] 15.4.4 | Make sure the text is not written to prefs or logs; add a code check or test | `AdNotificationListenerTest.kt` | 2h
- [ ] 15.4.5 | Send the OTP signal to 15.1 as a counter | `protection_engine.dart` | 1h
- [ ] 15.4.v | Verify gate: test OTP notification during a test call raises the warning; no OTP in logs (`adb logcat` grep) | Recording and empty grep | 1h

## 15.5 [BE] UPI / QR pre-payment check
Goal: `POST /scan/upi` flags risky payee IDs and collect requests.
Depends on: 15.7
- [ ] 15.5.1 | Define request: VPA or full `upi://` URI, optional payee name; response: verdict, reasons | `dto/Dtos.java` | 1h
- [ ] 15.5.2 | Write a UPI URI parser (pa, pn, am, mode; detect collect vs pay) after checking the NPCI/UPI linking spec | `service/UpiUriParser.java` | 3h
- [ ] 15.5.3 | Rules: VPA format, handle suffix is unknown, name mismatch with brand directory, collect request | `service/UpiScanService.java` | 4h
- [ ] 15.5.4 | Use 15.7 crowd reports (hashed VPA) as an extra signal | `UpiScanService.java` | 2h
- [ ] 15.5.5 | Add endpoint in `ScanController` | `ScanController.java` | 1h
- [ ] 15.5.6 | Unit and API tests: reported VPA flagged, collect-request flagged, normal pay QR safe | `UpiScanServiceTest.java` | 3h
- [ ] 15.5.v | Verify gate: reported VPA flagged; collect-request QR flagged. Run `mvn verify` in Docker and curl on port 18080 | Test and curl output | 1h

## 15.6 [FE] Recovery kit with golden-hour timer
Goal: one screen with timer, helpline calls, links, checklist and a local evidence ZIP.
Depends on: none
- [ ] 15.6.1 | Verify the 1930 helpline and cybercrime.gov.in link are current; list bank block numbers only from official bank pages | `docs/recovery-kit-sources.md` | 2h
- [ ] 15.6.2 | Create screen skeleton and golden-hour timer (starts when user taps "I was scammed") | `mobile/lib/screens/recovery_kit_screen.dart` | 3h
- [ ] 15.6.3 | Add one-tap call (dialer intent) and open-link buttons | `recovery_kit_screen.dart`, `NativeBridge.dial` | 2h
- [ ] 15.6.4 | Add the freeze checklist with tick boxes saved locally | `recovery_kit_screen.dart` | 2h
- [ ] 15.6.5 | Build evidence pack: timestamps, numbers, app names from local logs; user picks screenshots | `evidence_pack.dart` | 4h
- [ ] 15.6.6 | Create the ZIP locally and share through the system share sheet | `evidence_pack.dart`, `MainActivity.kt` | 3h
- [ ] 15.6.7 | Widget test for the timer and checklist | `mobile/test/recovery_kit_test.dart` | 2h
- [ ] 15.6.v | Verify gate: one-tap call and links work; evidence ZIP created locally and opens with the expected files | Screen recording and ZIP listing | 1h

## 15.7 [BE] Crowd reports
Goal: hashed, de-duplicated, trust-weighted reports for numbers, links and UPI IDs with a moderation queue.
Depends on: none
- [ ] 15.7.1 | Design the reports table: item type, hash, reporter id, weight, status; no raw value | `V8__reports.sql` | 2h
- [ ] 15.7.2 | Entity and repository for reports plus a unique key (user, item) | `domain/Report.java`, `repo/Repositories.java` | 2h
- [ ] 15.7.3 | Trust weight: new account low, older account with confirmed reports higher; write the formula | `service/TrustService.java` | 3h
- [ ] 15.7.4 | `POST /api/v1/reports` with hashing on the server for links/UPI and 5-char-prefix-safe hashing for numbers (share the hash helper with 16.1) | `controller/ReportController.java` | 3h
- [ ] 15.7.5 | Threshold logic: 5 trusted reports mark SUSPICIOUS | `service/ReportService.java` | 2h
- [ ] 15.7.6 | Moderation queue endpoint for admin (list, approve, reject) | `ReportController.java` | 3h
- [ ] 15.7.7 | Abuse test: 1 user x 20 reports does not change status | `ReportServiceTest.java` | 2h
- [ ] 15.7.v | Verify gate: 5 trusted reports mark an item SUSPICIOUS; 1 user x 20 does not. Run `mvn verify` in Docker | Test output | 1h

## 15.8 [FE] Guardian mode
Goal: a trusted contact gets an alert on CRITICAL scam situations; opt-in; no message content.
Depends on: 7.4, 15.1
- [ ] 15.8.1 | Check how 7.4 sends alerts (SMS or push) and decide what is reused | Note in PR | 1h
- [ ] 15.8.2 | Create guardian screen: add one contact, explain what is shared, on/off switch | `mobile/lib/screens/guardian_screen.dart` | 3h
- [ ] 15.8.3 | Add API calls to save/remove the guardian (consent timestamp stored) | `api_client.dart`, backend endpoint in `DeviceController.java` or `GuardianController.java` | 3h
- [ ] 15.8.4 | Trigger the alert when a CRITICAL scam threat is created; text has only "possible scam on X's phone" | `protection_engine.dart` | 2h
- [ ] 15.8.5 | Allow switching off at any time and confirm to the user | `guardian_screen.dart` | 1h
- [ ] 15.8.6 | Widget test and API test for off switch | `mobile/test/guardian_test.dart` | 2h
- [ ] 15.8.v | Verify gate: guardian receives alert on a simulated CRITICAL; after switch-off no alert is sent | Screenshot of received alert and log | 1h

## 15.9 [BE] Fake customer-care number checker
Goal: flag support numbers that are not on the official brand list.
Depends on: 15.7, 10.1
- [ ] 15.9.1 | Create the official care-number table (brand, number, source URL); verify each number on the brand's own site | `V9__care_numbers.sql`, `seed/care-numbers.csv` | 4h
- [ ] 15.9.2 | Repository and seeder | `CareNumberRepository`, `CareNumberSeeder.java` | 2h
- [ ] 15.9.3 | Service: normalise number, check official list, check community reports (hash) | `service/CareNumberService.java` | 3h
- [ ] 15.9.4 | Endpoint `POST /scan/care-number` with brand + number (number checked by hash against reports) | `ScanController.java` | 2h
- [ ] 15.9.5 | Tests: fake number for a known brand flagged, official one not | `CareNumberServiceTest.java` | 2h
- [ ] 15.9.v | Verify gate: fake support number for a known brand flagged; official one not. Run `mvn verify` in Docker and curl on port 18080 | Test and curl output | 1h

## 15.10 [FE] Scam drills
Goal: five short playable scenarios with score, Hindi and English.
Depends on: none
- [ ] 15.10.1 | Write the 5 scenarios as JSON (fake police call, KYC, lottery, electricity, fake job) with choices and the right answer | `mobile/assets/drills/*.json` | 4h
- [ ] 15.10.2 | Write Hindi versions and have them reviewed | `mobile/assets/drills/*_hi.json` | 3h
- [ ] 15.10.3 | Create the drill player screen (steps, choice buttons, feedback after each) | `mobile/lib/screens/drills_screen.dart` | 4h
- [ ] 15.10.4 | Scoring and local save of completion | `drills_screen.dart`, SharedPreferences | 2h
- [ ] 15.10.5 | Widget test for one full run and for saving | `mobile/test/drills_test.dart` | 2h
- [ ] 15.10.v | Verify gate: 5 scenarios playable; completion stays after restart | Screen recording | 1h
