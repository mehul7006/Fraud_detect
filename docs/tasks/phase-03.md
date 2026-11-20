# Phase 3 – Call & SMS forwarding
## 3.1 [BE] `CALL_FORWARDING` / `SMS_FORWARDING` threat types + de-dup
Goal: the report endpoint stores both forwarding types and does not create a second open threat of the same type.
Depends on: none
- [ ] 3.1.1 | Read `ThreatController.report` and `ThreatService.recordOnce`: `report` calls `record`, so duplicates are possible; write down which types must be de-duplicated (CALL_FORWARDING, SMS_FORWARDING, CLONE_RISK) and which must repeat (MALICIOUS_LINK, MALICIOUS_FILE) | decision note in a code comment above `report` | 1h
- [ ] 3.1.2 | Add a set `DEDUP_TYPES` in `ThreatService` and route reports of those types through `recordOnce` | `ThreatService.java` edited | 1h
- [ ] 3.1.3 | Return the existing open threat (or 201 with the same id) instead of silently ignoring, so the app can resolve it later; keep status 201 for the first report | `ThreatController.report` returns `ThreatDto` | 2h
- [ ] 3.1.4 | Unit test `ThreatServiceTest`: second CALL_FORWARDING while open is not stored; after resolve a new one is stored | test passes in Docker `mvn test` | 2h
- [ ] 3.1.5 | Integration test in `ApiFlowIT` (or a new `ForwardingThreatIT`): POST twice for CALL_FORWARDING and SMS_FORWARDING, GET `/threats?status=OPEN` returns exactly 2 rows | IT passes with Testcontainers | 2h
- [ ] 3.1.6 | Update `docs/04-API-SPEC.md` with the de-dup rule and the response body of `POST /threats`; check `ApiSpecSyncTest` from 2.1 still passes | edited spec file | 1h
- [ ] 3.1.v | Verify gate: report endpoint accepts and stores; duplicate open ones are not repeated | curl on port 18080: two `POST /api/v1/threats` with type `CALL_FORWARDING`, then `GET /api/v1/threats?status=OPEN` shows one row | 1h

## 3.2 [BE] Carrier response pattern table (per country/carrier regex) served from server so it can be updated without app release
Goal: the server serves carrier answer patterns and the app uses them, with the built-in parser as fallback.
Depends on: 3.4
- [ ] 3.2.1 | Design the pattern format: fields `country`, `carrier`, `ussdCode`, `forwardedRegex`, `offRegex`, `language`, `version`; limit regex length and allow only a safe subset because Dart and Java regex differ | design block in the migration comment and `docs/04-API-SPEC.md` | 2h
- [ ] 3.2.2 | Create the Flyway migration for table `carrier_patterns` (next free version number after `V1__init.sql`) with 4 seed rows for Jio, Airtel, Vi, BSNL taken from the real texts collected in 3.3 and 3.4 (no invented texts) | `db/migration/V2__carrier_patterns.sql` (or next free number); app boots | 2h
- [ ] 3.2.3 | Add entity `CarrierPattern` and `CarrierPatternRepository` | classes compile; JPA validate passes | 1h
- [ ] 3.2.4 | Add `ConfigController` with `GET /api/v1/config/carriers` returning `{version, patterns[]}`; allow it for logged-in users, add `ETag` or version so the app can skip unchanged data | curl output on port 18080 | 2h
- [ ] 3.2.5 | Test that every stored regex compiles in Java and is shorter than the limit (guards bad rows), plus controller test for 401 without token | `CarrierPatternTest`, `ConfigControllerIT` pass | 2h
- [ ] 3.2.6 | App side: add `ApiClient.carriers()` in `lib/core/api_client.dart`, cache the answer in secure storage, and let `ProtectionEngine` parse with server patterns first, then `looksForwarded` as fallback | `lib/core/carrier_patterns.dart` plus unit test with fake server answer | 3h
- [ ] 3.2.7 | App test for the fallback: server unreachable and bad regex both fall back to the built-in parser without crash | `test/carrier_patterns_test.dart` passes | 2h
- [ ] 3.2.v | Verify gate: app fetches patterns, falls back to built-in | curl output of `GET /api/v1/config/carriers` plus the passing fallback test, and device log line showing patterns loaded | 1h

## 3.3 [FE] Kotlin USSD query `*#21#`, `*#67#`, `*#61#`, `*#62#`
Goal: on a real SIM the app gets the carrier text for all four forwarding queries and never hangs.
Depends on: 0.6
- [ ] 3.3.1 | Review `CallForwardChecker.kt`: `sendUssdRequest` can stay silent forever on some phones, so add a 15 second timeout that returns `ERROR:TIMEOUT` | edited `android-native/kotlin/CallForwardChecker.kt` | 2h
- [ ] 3.3.2 | Handle dual SIM: use the default voice subscription (`createForSubscriptionId`) and return `ERROR:NO_SIM` when no SIM is present | edited `CallForwardChecker.kt`; verify the API behaviour on the Android reference page before coding | 2h
- [ ] 3.3.3 | Add a Dart method `checkAllForwarding()` in `lib/core/protection_engine.dart` that asks the four codes in order and returns a map code to text (today only `*#21#` is checked) | method plus status keys `fwdAll`, `fwdBusy`, `fwdNoAnswer`, `fwdUnreach` | 2h
- [ ] 3.3.4 | Run the app on a phone with a real SIM and request the phone permission; save the exact carrier text for each code | `docs/evidence/3.3-ussd-<carrier>.txt` with 4 texts | 2h
- [ ] 3.3.5 | Repeat on a second carrier or SIM if one is available; note the model, Android version and carrier | second evidence file or a note "one SIM only" | 2h
- [ ] 3.3.6 | Check that `allowedUssd` in `MainActivity.kt` rejects other codes (try `*#06#` from a debug call) | log showing `BAD_CODE` | 1h
- [ ] 3.3.v | Verify gate: on a real SIM returns carrier text | the evidence files from 3.3.4 plus a screenshot of the dashboard tile | 1h

## 3.4 [FE] Parse response → ACTIVE/OFF/UNKNOWN
Goal: the parser returns ACTIVE, OFF or UNKNOWN and is proven on at least 10 real carrier texts.
Depends on: 3.3
- [ ] 3.4.1 | Define `enum ForwardState { active, off, unknown }` and `parseForwardState(String)` next to `looksForwarded` in `lib/core/protection_engine.dart`; keep `looksForwarded` working for old callers | function plus enum compile | 1h
- [ ] 3.4.2 | Collect at least 10 real answer texts (Jio, Airtel, Vi, BSNL; active and off) from the evidence of 3.3 and from users; mark any text that was typed from memory as `synthetic` and do not count it | `test/fixtures/ussd_responses.json` with `carrier`, `code`, `text`, `expected`, `source` | 3h
- [ ] 3.4.3 | Return UNKNOWN when the text is empty, an error code, or has no known pattern (today unknown text without digits becomes OFF) | updated parser | 2h
- [ ] 3.4.4 | Write a table test reading the fixture file: each text gives the expected state | `test/call_forward_parser_test.dart` with at least 10 real cases passing in Docker `flutter test` | 2h
- [ ] 3.4.5 | Add Hindi and mixed-language cases and the "forwarded to number" cases with spaces or `+91` formats | 4 extra fixture rows and tests | 2h
- [ ] 3.4.6 | Use `parseForwardState` in `checkCallForwarding()` so the tile shows ACTIVE, OFF or UNKNOWN exactly | `engine.status['forwarding']` values checked in a test | 1h
- [ ] 3.4.v | Verify gate: unit test on at least 10 real carrier texts passes | `flutter test test/call_forward_parser_test.dart` output `All tests passed` and fixture file count of real rows | 1h

## 3.5 [FE] One-tap disable `##002#` + re-check
Goal: one tap (with confirmation) disables forwarding, re-checks, and the tile turns OFF.
Depends on: 3.4
- [ ] 3.5.1 | Add a confirmation dialog before the disable action in the alert action of `checkCallForwarding()` ("This turns off all call forwarding on your SIM") | dialog visible before USSD runs | 1h
- [ ] 3.5.2 | Wait 3 seconds after `##002#` before re-checking (carrier needs time) and retry the check once; show a spinner on the tile | edited `lib/core/protection_engine.dart` | 2h
- [ ] 3.5.3 | Handle failure: if `##002#` returns an error or the re-check is still ACTIVE, show the dialer fallback button from 3.6 | alert with fallback action | 2h
- [ ] 3.5.4 | After a successful OFF, resolve the open CALL_FORWARDING threat on the server (find its id via `GET /threats?status=OPEN`, then `POST /threats/{id}/resolve`) | `ApiClient` helper `resolveOpenByType` plus test with MockClient | 2h
- [ ] 3.5.5 | Test on a real SIM: enable forwarding from the phone dialer to a spare number, then use the app button | recording: tile goes ACTIVE, then OFF | 2h
- [ ] 3.5.6 | Unit test the sequence with fakes (bridge returns ACTIVE then OFF): status changes and threat is resolved | `test/disable_forwarding_test.dart` passes | 2h
- [ ] 3.5.v | Verify gate: after disable, tile turns OFF | recording from 3.5.5 and the test name from 3.5.6 | 1h

## 3.6 [FE] Fallback: open dialer with code when USSD API unsupported
Goal: when the USSD API cannot run, the app opens the dialer with the code filled in, on Android 8 to 14, without extra permission.
Depends on: 3.3
- [ ] 3.6.1 | Check `dialCode` in `MainActivity.kt`: `Uri.encode` must turn `#` into `%23`; add the encoded value to a log line for the test | log output for `*#21#` | 1h
- [ ] 3.6.2 | In `lib/core/protection_engine.dart`, when `NativeBridge.ussd` returns NO_PERMISSION, UNSUPPORTED or ERROR, set the tile to UNKNOWN and raise a low-severity alert with a button "Open dialer" calling `NativeBridge.dial('*#21#')` | alert and button visible | 2h
- [ ] 3.6.3 | Add the same dialer button for the other three codes and for `##002#` in a small helper screen "Check manually" with the four codes | `lib/screens/manual_forwarding_screen.dart` | 2h
- [ ] 3.6.4 | Test on a device without the phone permission granted (deny it first): button opens the dialer with the code shown | screenshot | 1h
- [ ] 3.6.5 | Test on one older (Android 8 to 10) and one newer (13 or 14) device or emulator image; note results; if only one device is available, say which versions are untested | `docs/evidence/3.6-dialer-matrix.md` | 3h
- [ ] 3.6.v | Verify gate: works on Android 8–14 devices without permission | the matrix file with at least 2 Android versions and screenshots | 1h

## 3.7 [FE] SMS-stealer audit: list apps with SMS permission + notification listener
Goal: a screen lists non-system apps that can read SMS or notifications, using granted permissions only.
Depends on: 0.5, 2.5
- [ ] 3.7.1 | In `AppAuditor.kt` add granted-state checks (`requestedPermissionsFlags` with `REQUESTED_PERMISSION_GRANTED`) for READ_SMS, RECEIVE_SMS, and add `installer` (`getInstallSourceInfo` on API 30+, with fallback) to the output | edited `android-native/kotlin/AppAuditor.kt` | 2h
- [ ] 3.7.2 | Detect enabled notification listeners from `Settings.Secure enabled_notification_listeners` so the list shows apps whose listener is actually switched on, not just declared | new field `listenerEnabled` | 2h
- [ ] 3.7.3 | Keep the `/scan/apps` payload compatible: new fields are optional in `AppInfo` in `Dtos.java` or are not sent yet | contract check with `ApiContractIT` | 1h
- [ ] 3.7.4 | Create `lib/screens/sms_audit_screen.dart`: list of apps with SMS permission or listener, label, package, reason text, buttons "Uninstall" (existing `NativeBridge.uninstallApp`) and "Ignore" | screen opens from the dashboard SMS forwarding tile | 3h
- [ ] 3.7.5 | Build a tiny test APK that only requests READ_SMS (a throwaway Flutter project with the permission in its manifest) and install it | `testapp-sms.apk` built in Docker | 2h
- [ ] 3.7.6 | Widget test with a fake bridge list: only the SMS and listener apps appear, system apps are hidden | `test/sms_audit_screen_test.dart` passes | 2h
- [ ] 3.7.v | Verify gate: test app with READ_SMS appears in list | device screenshot of the list with `testapp-sms` shown | 1h

## 3.8 [FE] SMS forwarding guidance screen (carrier steps)
Goal: a plain-language screen that a non-technical person can follow, with carrier-specific steps checked against official sources.
Depends on: 3.7
- [ ] 3.8.1 | Replace the inline dialog in `lib/screens/dashboard_screen.dart` with a screen `lib/screens/sms_forwarding_screen.dart` (short sentences, numbered steps, big text) | screen reachable from the tile | 2h
- [ ] 3.8.2 | Verify the current official steps and customer care numbers for Jio, Airtel, Vi, BSNL on each carrier site; copy only what you confirmed and keep the source URL and date | `docs/evidence/3.8-carrier-sources.md` | 3h
- [ ] 3.8.3 | Add a carrier picker (4 carriers plus "Other") that shows that carrier's steps and a "Call customer care" button using the dialer (no call permission) | picker and buttons working | 2h
- [ ] 3.8.4 | Add a link to the SMS-stealer list from 3.7 and a short "SIM swap warning signs" block (sudden loss of network) | links work | 1h
- [ ] 3.8.5 | Run a read-through with 2 non-technical users; write down every place they hesitated and fix the wording | feedback notes in `docs/evidence/3.8-review.md`; text updated | 2h
- [ ] 3.8.6 | Widget test: screen shows steps for each carrier and the care button | `test/sms_forwarding_screen_test.dart` passes | 1h
- [ ] 3.8.v | Verify gate: dialog content reviewed by non-technical user | `docs/evidence/3.8-review.md` with 2 reviewers' names (initials) and the final screenshot | 1h
