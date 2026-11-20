# Phase 2 – Mobile base
## 2.1 [BE] API contract frozen (OpenAPI/springdoc) so FE can generate/check models
Goal: `/v3/api-docs` lists exactly the endpoints and fields in `docs/04-API-SPEC.md` and a test keeps them in sync.
Depends on: none
- [x] 2.1.1 | Run the existing draft `ApiContractIT` in Docker (`mvn -B -ntp verify -Dit.test=ApiContractIT` with docker socket mounted) | 4 tests pass or a list of failures | 1h
- [x] 2.1.2 | Start the app in Docker on port 18080 (`PORT=18080`, db from `docker-compose.yml`) and save the output of `curl http://localhost:18080/v3/api-docs` | `docs/openapi.json` snapshot | 1h
- [x] 2.1.3 | Diff the snapshot against `docs/04-API-SPEC.md` line by line (fields `mimeType`, `hasFullScreenIntent`, `reasons`, `action`, ThreatEvent fields) and list every difference | note with the diff list | 2h
- [x] 2.1.4 | Fix each difference on the right side: DTO in `Dtos.java` or the spec table in `docs/04-API-SPEC.md` | edited `Dtos.java` / `04-API-SPEC.md` | 2h
- [x] 2.1.5 | Add `@Operation` and `@ApiResponse` annotations (400/401/409 with `ErrorBody` schema) on `AuthController`, `ScanController`, `ThreatController`, `DeviceController` | `/v3/api-docs` shows error responses for `/auth/login` | 3h
- [x] 2.1.6 | Add test `ApiSpecSyncTest` that reads the rows of the implemented-endpoints table in `docs/04-API-SPEC.md` and asserts each method and path exists in `/v3/api-docs` | test passes; fails when a path is removed | 3h
- [x] 2.1.7 | Add a test that the prod profile publishes no docs (`springdoc.api-docs.enabled=false` gives 404) | `ApiDocsDisabledInProdIT` passes | 1h
- [x] 2.1.v | Verify gate: `/v3/api-docs` matches the spec | `mvn verify` green including `ApiSpecSyncTest`, and `docs/openapi.json` saved | 1h

## 2.2 [FE] API client + JWT in secure storage
Goal: login stores the token securely, every request sends it, and the session survives an app restart.
Depends on: 2.1
- [ ] 2.2.1 | Make `ApiClient` testable: constructor takes an `http.Client` and a `FlutterSecureStorage` (keep `ApiClient.instance` as default) in `lib/core/api_client.dart` | code compiles; `flutter analyze` clean | 2h
- [ ] 2.2.2 | Parse the backend error JSON (`message`, `requestId`) into `ApiException` instead of keeping the raw body | `ApiException.message` holds the text; test checks it | 2h
- [ ] 2.2.3 | Wrap timeout and socket errors into one `ApiException(0, ...)` type so screens handle them the same way | test: MockClient that throws gives status 0 | 1h
- [ ] 2.2.4 | Add an `onUnauthorized` callback: any 401 on an authenticated call clears the token and tells the app gate to show login | `test/api_client_test.dart` case "401 clears token" | 2h
- [ ] 2.2.5 | Write unit tests with `http/testing.dart` MockClient: login stores token, bearer header sent, register 409, 400 body | 4 tests pass in `flutter test` (Docker image) | 2h
- [ ] 2.2.6 | Check base URL: document `--dart-define=API_BASE=http://10.0.2.2:18080/api/v1` for the test port, and check that cleartext HTTP to `10.0.2.2` works on the device; if blocked add a debug-only network security config in `tool/setup_android.sh` | note plus a successful login call from the device | 2h
- [ ] 2.2.v | Verify gate: login stores token; restart keeps session | recording or screenshots: login, force-stop app, reopen straight to dashboard | 1h

## 2.3 [FE] Login/Register screens with correct error messages (401/409/network)
Goal: each failure shows a clear plain message and the form blocks bad input before calling the server.
Depends on: 2.2
- [ ] 2.3.1 | Extract an `AuthApi` abstract class (login, register) implemented by `ApiClient` so widget tests can fake it | `lib/core/auth_api.dart`; `LoginScreen` takes it as parameter | 1h
- [ ] 2.3.2 | Add client checks in `lib/screens/login_screen.dart`: valid email format, password at least 8 characters, with messages under the fields | widget test: short password shows text, no API call | 2h
- [ ] 2.3.3 | Map 400 (validation) and timeout to plain messages next to the existing 401/409/network ones; no raw "Error 400" text | function `messageFor(ApiException)` with unit test | 1h
- [ ] 2.3.4 | Add show/hide password, disable the button while busy, guard against double submit | widget test: second tap during busy does nothing | 1h
- [ ] 2.3.5 | Write widget tests with a fake `AuthApi`: 401 shows "Wrong email or password", 409 shows "Email already registered", network shows "Cannot reach server" | `test/login_screen_test.dart` 3 tests pass | 2h
- [ ] 2.3.6 | Manual run against the backend on port 18080: wrong password, duplicate email, server stopped | 3 screenshots | 1h
- [ ] 2.3.v | Verify gate: wrong password shows "Wrong email or password" | screenshot from the device plus the passing widget test name | 1h

## 2.4 [FE] App gate + navigation + theme
Goal: cold start opens login or dashboard correctly, also when secure storage fails.
Depends on: 2.2
- [ ] 2.4.1 | Make the gate in `lib/main.dart` testable: pass a token loader function to `_Gate` (default `ApiClient.instance.loadToken`) | `FraudShieldApp` accepts an optional loader | 1h
- [ ] 2.4.2 | Handle storage errors: if `loadToken` throws, treat as logged out and show login (no endless spinner) | widget test with throwing loader shows login | 1h
- [ ] 2.4.3 | Connect the `onUnauthorized` callback from 2.2.4 so an expired token returns to login with a short message | widget test: callback switches to login | 2h
- [ ] 2.4.4 | Add a fake method channel helper for tests (`TestDefaultBinaryMessengerBinding`) so `DashboardScreen` can build without native code | `test/helpers/fake_native.dart` | 2h
- [ ] 2.4.5 | Widget tests: no token shows `LoginScreen`, token shows `DashboardScreen`, logout returns to login | `test/app_gate_test.dart` 3 tests pass | 2h
- [ ] 2.4.6 | Check the theme on a device: light and dark, large font setting 130%, no overflow errors | 2 screenshots | 1h
- [ ] 2.4.v | Verify gate: cold start goes to login or dashboard | device test: fresh install shows login; after login and force-stop shows dashboard (2 screenshots) | 1h

## 2.5 [FE] Dashboard with status tiles
Goal: five tiles show forwarding, clone, apps, link and file status, and a failed check never looks green.
Depends on: 2.2
- [ ] 2.5.1 | Fix tile colour: today `ERROR`, `UNKNOWN` and "checking..." show green. Move the rule into a pure function `tileState(title, value)` returning ok, bad or unknown in `lib/screens/dashboard_screen.dart` | function exists; grey for unknown | 1h
- [ ] 2.5.2 | Unit tests for `tileState` covering `ACTIVE`, `RISK 60`, `3 risky`, `ERROR`, `UNKNOWN`, and `OFF` for protection tiles | `test/tile_state_test.dart` passes | 1h
- [ ] 2.5.3 | Let `DashboardScreen` take a `ProtectionEngine` as parameter (today it creates one inside the State) so tests can pass a fake | widget test can set `engine.status` | 1h
- [ ] 2.5.4 | Add "last checked" time per tile (from the engine) and a spinner while checking | tile subtitle shows the time | 2h
- [ ] 2.5.5 | Widget test: set status map and permission flags, check each tile text and colour | `test/dashboard_screen_test.dart` passes | 2h
- [ ] 2.5.6 | Manual run on the device with backend on 18080: start, wait for the first check, tap each refresh | screenshot with all 5 tiles filled | 1h
- [ ] 2.5.v | Verify gate: tiles show forwarding/clone/apps/link/file status | screenshot of the dashboard with 5 tiles plus the widget test name | 1h

## 2.6 [FE] Permission onboarding with plain-language consent (notification access, all-files, phone)
Goal: each permission card explains why, opens the correct system screen, and disappears once granted.
Depends on: 2.5, 0.5
- [ ] 2.6.1 | Add Kotlin `hasPhonePermission` (CALL_PHONE and READ_PHONE_STATE) in `MainActivity.kt` and `NativeBridge.hasPhonePermission()` so the phone card can disappear (it is always shown today) | channel method works on the device | 2h
- [ ] 2.6.2 | Request `POST_NOTIFICATIONS` at runtime on Android 13+ (declared in the manifest but never requested) with a native method and a card | alert notification shows on an Android 13 device | 2h
- [ ] 2.6.3 | Rewrite the card texts in plain short language: what we read, what leaves the phone (only link, file hash, package names), what we never read | updated strings in `dashboard_screen.dart`; reviewed by one non-technical person | 1h
- [ ] 2.6.4 | Add a short consent screen before the first permission request (text plus Continue), shown only once | `lib/screens/consent_screen.dart` | 2h
- [ ] 2.6.5 | Widget tests with a fake method channel: card visible when permission false, hidden when true, button calls the right channel method | `test/permission_cards_test.dart` 3 tests pass | 2h
- [ ] 2.6.6 | Device test: each of the three cards opens the correct system screen and disappears after granting and returning to the app | 3 short recordings or screenshots | 2h
- [ ] 2.6.v | Verify gate: each card opens the correct system screen and disappears when granted | recording of the three flows | 1h

## 2.7 [FE] Threat history screen (list, resolve, pull-to-refresh)
Goal: list shows threats from the backend, resolve works, errors can be retried.
Depends on: 2.2
- [ ] 2.7.1 | Give `ThreatsScreen` an API parameter (interface with `threats()` and `resolve(id)`) so tests can fake it | `lib/screens/threats_screen.dart` | 1h
- [ ] 2.7.2 | Fix pull-to-refresh on empty and error states (today refresh only works when the list is not empty): use a scrollable child | widget test pulls down on an empty list and reloads | 1h
- [ ] 2.7.3 | Add an error state with a Retry button and a snackbar when `resolve` fails | widget test: error, then retry shows the list | 2h
- [ ] 2.7.4 | Show readable date and time instead of raw ISO text; sort open items first | date helper with unit test | 1h
- [ ] 2.7.5 | Widget tests for list, resolve tap, empty message | `test/threats_screen_test.dart` passes | 2h
- [ ] 2.7.6 | Create threats with curl on port 18080 (`POST /api/v1/threats`, and `POST /api/v1/scan/url` with the blocklisted domain `example-phish.test`) and view them in the app | screenshot with 3 threats | 1h
- [ ] 2.7.v | Verify gate: shows threats created by backend test calls | screenshot of the list, then a Resolve tap showing a green check | 1h

## 2.8 [FE] Blocking alert popup system (queue, critical = not dismissible by tap-outside)
Goal: alerts show one after another; critical ones cannot be dismissed by tap-outside or the back button.
Depends on: 2.5
- [ ] 2.8.1 | Move the queue logic out of `_drain()` into a plain class `AlertQueue` in `lib/core/alert_queue.dart` | class with `add`, `next`, `isBusy` | 2h
- [ ] 2.8.2 | Drop duplicate alerts (same title and body queued or shown in the last 10 minutes) so the hourly heartbeat does not repeat popups | unit test for dedupe | 2h
- [ ] 2.8.3 | Wrap critical dialogs in `PopScope(canPop: false)` so the Android back button cannot close them | widget test: back press keeps the dialog | 1h
- [ ] 2.8.4 | Unit tests for `AlertQueue`: 2 alerts give 2 dialogs in order, critical first | `test/alert_queue_test.dart` passes | 2h
- [ ] 2.8.5 | Widget test: add 2 alerts at once, first dialog visible, tap action, second appears | `test/alert_popup_test.dart` passes | 2h
- [ ] 2.8.6 | Device test: trigger two alerts (one CRITICAL) from a debug button or fake engine; tap outside and press back | recording | 1h
- [ ] 2.8.v | Verify gate: 2 alerts at once show one after another | recording or widget test output | 1h

## 2.9 [FE] Flutter unit + widget tests, `flutter analyze` clean
Goal: analyzer shows 0 errors and all Flutter tests pass in Docker and in CI.
Depends on: 0.5, 2.2, 2.3, 2.5, 2.7, 2.8
- [ ] 2.9.1 | Check `mobile/analysis_options.yaml` exists after `flutter create` and includes `flutter_lints`; run `flutter analyze` in Docker and list the issues | analyzer output saved | 1h
- [ ] 2.9.2 | Fix all analyzer errors and warnings in `lib/` and `test/` | `No issues found` | 2h
- [ ] 2.9.3 | Make `ProtectionEngine` take an `ApiClient` and a bridge object as constructor parameters (defaults keep today's behaviour) | `lib/core/protection_engine.dart` compiles; app unchanged | 3h
- [ ] 2.9.4 | Engine tests with fakes: `checkDevice` sets `RISK` for score 60, `checkApps` sets `2 risky`, failures set `ERROR` | `test/engine_status_test.dart` 3 tests pass | 3h
- [ ] 2.9.5 | Run `flutter test --coverage` in Docker and note the percentage for `lib/core` | `coverage/lcov.info` created; number in a note | 1h
- [ ] 2.9.6 | Confirm the CI job `mobile` runs analyze and test with the same result | green Actions run | 1h
- [ ] 2.9.v | Verify gate: 0 analyzer errors, tests pass | outputs of `flutter analyze` and `flutter test` in Docker showing `No issues found` and `All tests passed` | 1h
