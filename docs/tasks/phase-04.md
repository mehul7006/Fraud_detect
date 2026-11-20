# Phase 4 – Link protection
## 4.1 [BE] Google Safe Browsing integration (hash-prefix/URL lookup) behind interface
Goal: `UrlScanService` asks a reputation provider (Safe Browsing) and adds score for a listed URL; the scan still works when the provider fails.
Depends on: none
- [ ] 4.1.1 | Verify the current Safe Browsing API: which version to use (Lookup or Update API), the request URL, the free-use terms and the daily quota; write the findings with the doc links and date | `docs/evidence/4.1-safebrowsing-terms.md` | 2h
- [ ] 4.1.2 | Get a free API key and store it only as environment variable `SAFE_BROWSING_KEY` (add to `backend/.env.example` from 0.4; never in git) | `.env.example` line; key absent from repo | 1h
- [ ] 4.1.3 | Create interface `ReputationProvider` (`int score(String url)`) and class `NoopReputationProvider`; change `UrlScanService.externalReputation(String host)` to call the provider with the full URL | new files in `backend/src/main/java/com/fraudshield/service/`; existing tests pass | 2h
- [ ] 4.1.4 | Implement `SafeBrowsingProvider` with `java.net.http.HttpClient`, 2 second timeout, threat types per the doc from 4.1.1; any error returns 0 and logs the status only (no URL) | `SafeBrowsingProvider.java` | 3h
- [ ] 4.1.5 | Unit test with a local stub server (`com.sun.net.httpserver.HttpServer`): match gives score 60, empty answer 0, 500 and timeout give 0 | `SafeBrowsingProviderTest` passes in Docker `mvn test` | 3h
- [ ] 4.1.6 | Wire config: provider enabled only when `fraudshield.safebrowsing.api-key` is set, else Noop; add the property to `application.yml` using `${SAFE_BROWSING_KEY:}` | app boots with and without the key | 1h
- [ ] 4.1.7 | Add a reason text "Listed by Google Safe Browsing" in `UrlScanService` result; a test that all older tests in `ScanServicesTest` and `ApiFlowIT` still pass | green `mvn verify` | 1h
- [ ] 4.1.v | Verify gate: known test URL from Google returns +score | run on port 18080 with the key set: `curl -X POST /api/v1/scan/url` for the Safe Browsing test URL (verify its current address in the Google docs) shows the extra reason and score | 1h

## 4.2 [BE] Result cache (host → verdict, 24h) to cut cost/latency
Goal: a repeated scan of the same host is answered from memory in under 5 ms.
Depends on: 4.1
- [ ] 4.2.1 | Decide the cache key: today the score also uses the path and words in the URL, so a host-only key would hide a bad path on a good host; choose "external reputation per host" plus rules computed fresh, or full URL without query; write the decision | comment block in `UrlScanService.java` | 1h
- [ ] 4.2.2 | Add `spring-boot-starter-cache` and `com.github.ben-manes.caffeine:caffeine` to `backend/pom.xml` and enable caching with a `CacheConfig` class (max 10000 entries, expire 24h after write) | `config/CacheConfig.java`; app boots | 2h
- [ ] 4.2.3 | Apply the cache to the chosen method (`@Cacheable` on a separate bean so the proxy works, not on a private/self-called method) | edited service classes | 2h
- [ ] 4.2.4 | Never cache provider failures: when the provider returned an error, skip caching so the next scan retries | test with a failing stub: second call hits the provider again | 2h
- [ ] 4.2.5 | Enable Caffeine `recordStats` and log hit rate at DEBUG; expose size in `/actuator/health` details only in dev (check actuator is in the pom first) | log line or note | 1h
- [ ] 4.2.6 | Test: second scan of the same host calls the stub provider once; timing check under 5 ms on the second call (use a generous threshold such as 20 ms in the test, report real value in the note) | `UrlScanCacheTest` passes | 2h
- [ ] 4.2.v | Verify gate: second scan of same host takes less than 5 ms | two curl calls on port 18080 with `-w "%{time_total}"` showing the second faster, plus `UrlScanCacheTest` | 1h

## 4.3 [BE] Blocklist admin API + CSV import (ADMIN role)
Goal: only an ADMIN user can add, list, delete and CSV-import blocklist entries, and the next scan treats the domain as malicious.
Depends on: none
- [ ] 4.3.1 | Add a role to users: new Flyway migration (next free version number) `ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER'`, field in `domain/User.java` | migration file; JPA `validate` passes | 2h
- [ ] 4.3.2 | Put the role in the token: add a `role` claim in `JwtService.create` and a `parseRole`; in `SecurityConfig` set the authority `ROLE_ADMIN` from it (keep the principal a `Long` so other controllers do not change) | edited `JwtService.java`, `SecurityConfig.java` | 3h
- [ ] 4.3.3 | Protect `/api/v1/admin/**` with `hasRole("ADMIN")`; 403 uses the same `ErrorBody` JSON | `SecurityConfig` rule; test for 403 as normal user | 2h
- [ ] 4.3.4 | Create `BlocklistController` and `BlocklistService`: `GET /admin/blocklist`, `POST /admin/blocklist {kind,value,reason}`, `DELETE /admin/blocklist/{id}`; normalise domains (lowercase, trim, no scheme); reject duplicates with 409 | classes plus `BlocklistAdminIT` cases | 3h
- [ ] 4.3.5 | Add `POST /admin/blocklist/import` (multipart CSV, columns `kind,value,reason`, max 5000 rows, skip bad lines and return counts `{added,skipped,errors[]}`) | endpoint plus CSV parser unit test with 6 sample lines | 3h
- [ ] 4.3.6 | Seed the first admin without a public endpoint: env var `ADMIN_EMAIL` promotes that user at start-up (documented in `.env.example`) | `AdminBootstrap` class and test | 2h
- [ ] 4.3.7 | Integration tests: user gets 403, admin adds domain, next `/scan/url` for it is MALICIOUS (100), CSV import adds 3 domains | tests pass in Docker `mvn verify` | 3h
- [ ] 4.3.v | Verify gate: added domain is MALICIOUS on next scan | curl sequence on port 18080 (admin POST, then `/scan/url` result with score 100) plus the passing IT name | 1h

## 4.4 [BE] False-positive report endpoint + review queue
Goal: a user can report a wrong verdict and an admin can see and close the reports.
Depends on: 4.3
- [ ] 4.4.1 | Create the migration (next free version) for table `url_feedback` (`id,user_id,host,kind,note,status,created_at`); store the host only, never the full URL or query | `db/migration/V*__url_feedback.sql` | 1h
- [ ] 4.4.2 | Add entity, repository and DTO `FeedbackRequest {url, kind FALSE_POSITIVE or FALSE_NEGATIVE, note max 300}` | classes compile | 2h
- [ ] 4.4.3 | Implement `POST /api/v1/scan/url/feedback` returning 204; strip the URL to host before saving; limit one open report per user and host; 400 for bad kind | `FeedbackController`, `FeedbackService` | 3h
- [ ] 4.4.4 | Admin queue: `GET /api/v1/admin/feedback?status=OPEN` and `POST /admin/feedback/{id}/close` (ADMIN role from 4.3) | endpoints plus IT | 2h
- [ ] 4.4.5 | Add `ADD_TO_BLOCKLIST` / `ALLOW` option on close that adds the host to the blocklist for a confirmed false negative | close request field and test | 2h
- [ ] 4.4.6 | Tests: stored row has host only (no path), duplicate is rejected, user cannot call admin endpoints | `FeedbackIT` passes | 2h
- [ ] 4.4.7 | Update `docs/04-API-SPEC.md` (this endpoint is already listed as planned; move it to implemented) and re-run `ApiSpecSyncTest` | edited spec | 1h
- [ ] 4.4.v | Verify gate: report stored and visible to admin | curl: user reports, admin `GET /admin/feedback` lists it; DB check shows host only | 1h

## 4.5 [FE] Notification listener captures notifications from all apps
Goal: a notification containing a link from any app lands in the pending list without storing message text.
Depends on: 0.5
- [ ] 4.5.1 | Review `AdNotificationListener.kt` for privacy: confirm the notification text is never written to storage, only the extracted URL, the package and counters | review note listing each `prefs.edit()` call | 1h
- [ ] 4.5.2 | Skip noise sources: ongoing notifications, group summaries and our own package; add a per-run cap and de-duplicate the same URL within 5 minutes | edited `AdNotificationListener.kt` | 2h
- [ ] 4.5.3 | Make `isEnabled` robust and show a status in the dashboard "Link protection" tile when the listener is enabled but not connected (call `requestRebind` when needed, API 24+) | edited Kotlin plus tile text | 2h
- [ ] 4.5.4 | Prepare a way to post a test notification with a URL: on a device `adb shell cmd notification post -S bigtext -t Test Tag "visit http://example-phish.test/login"` (verify the command on the device's Android version) | note with the working command | 1h
- [ ] 4.5.5 | Run the test with the listener enabled; read pending events through the app (debug log in `takePending`) | log line showing kind `LINK` with the URL | 2h
- [ ] 4.5.6 | Test with real apps: send yourself a link in WhatsApp and Telegram and an SMS | 3 log lines or screenshots | 2h
- [ ] 4.5.v | Verify gate: test notification with URL stored in pending list | log output of `takePendingEvents` showing the LINK event, plus the `adb` command used | 1h

## 4.6 [FE] URL extraction regex (http, www, bare domains, .apk) with unit test
Goal: a testable URL extractor finds links in 20 sample strings, including bare domains.
Depends on: none
- [ ] 4.6.1 | Move the regex out of `AdNotificationListener.kt` into `android-native/kotlin/UrlExtractor.kt` (`fun extract(text: String): List<String>`) | listener calls `UrlExtractor.extract` | 1h
- [ ] 4.6.2 | Write the 20-sample table first: `http://`, `https://` with query, `www.`, bare `flipkart.com`, `fleepkart.com/offer`, `bit.ly/abc`, `.apk` links, trailing punctuation, links in brackets, Hindi text around a link, two links, no link, and email addresses that must not match | `android-native/test/UrlExtractorTest.kt` with 20 cases | 2h
- [ ] 4.6.3 | Fix the regex: today a bare domain only matches when a `/` follows and only for a short TLD list, so `fleepkart.com` alone is missed; trim trailing `.,)!?`; keep email addresses out | updated `UrlExtractor.kt` | 3h
- [ ] 4.6.4 | Make the test run in Docker: extend `tool/setup_android.sh` to copy `android-native/test/*.kt` to `android/app/src/test/kotlin/com/fraudshield/app/` and add JUnit `testImplementation` to the generated gradle file | edited script | 2h
- [ ] 4.6.5 | Run `./gradlew testDebugUnitTest` in the Flutter image (inside `mobile/android`) and fix failures | test report with 20 passes | 2h
- [ ] 4.6.6 | Add the test run as a step in `.github/workflows/ci.yml` job `mobile` | CI step visible | 1h
- [ ] 4.6.v | Verify gate: 20 sample strings extract correctly | Gradle test report `20 tests completed, 0 failed` | 1h

## 4.7 [FE] Scan queue → backend → verdict → popup/notification
Goal: a link received in a messenger gets a verdict and a popup within 20 seconds, even when scans fail or repeat.
Depends on: 4.5, 4.6, 2.8
- [ ] 4.7.1 | Make `_handleLink` testable: engine takes the API and bridge as parameters (see 2.9.3) and `_handleLink` becomes a public method `handleLink` | edited `lib/core/protection_engine.dart` | 1h
- [ ] 4.7.2 | Avoid scanning the same URL twice in 10 minutes (in-memory map) and limit parallel scans to 3 | unit test: same URL twice gives one API call | 2h
- [ ] 4.7.3 | Handle API errors in `_handleLink`: an exception must not drop the other events in `runFast`; failed links go back to the offline queue (4.10) or are logged for retry | try/catch per event; test | 2h
- [ ] 4.7.4 | Reduce delay: the fast loop is every 20 s, so a link can wait 20 s plus scan time. Trigger `runFast` from the listener event (Kotlin to Dart `MethodChannel` call or an `EventChannel`) so the popup appears in a few seconds | event path from `AdNotificationListener.kt` to `NativeBridge` | 4h
- [ ] 4.7.5 | Unit tests with fakes: SAFE gives no alert, SUSPICIOUS gives MEDIUM alert, MALICIOUS gives CRITICAL alert plus native notification | `test/link_scan_test.dart` 3 tests pass | 2h
- [ ] 4.7.6 | End-to-end on the device with the backend on port 18080: send the blocklisted domain `example-phish.test` in WhatsApp; measure time from message to popup | recording with timer | 2h
- [ ] 4.7.7 | Check the same flow when the app is in the background (known gap: the engine only runs while the app is alive; background service is task 7.11) and record the result | note "works in foreground, background = 7.11" | 1h
- [ ] 4.7.v | Verify gate: WhatsApp phishing text triggers popup in ≤20 s | recording showing message arrival and popup with the elapsed time | 1h

## 4.8 [FE] Share-to-FraudShield (receive text intent in Flutter and scan)
Goal: sharing a link from a browser or messenger to FraudShield shows a verdict screen.
Depends on: 4.6, 4.7
- [ ] 4.8.1 | Check the share intent-filter is in the generated `AndroidManifest.xml` (merged by `tool/setup_android.sh`) and that `MainActivity` has `launchMode` that gets `onNewIntent` | grep output in a note; fix the script if missing | 1h
- [ ] 4.8.2 | In `MainActivity.kt` read `Intent.EXTRA_TEXT` in `onCreate` and `onNewIntent`, keep the last shared text in a variable and add channel method `takeSharedText` (returns and clears it) | edited Kotlin | 2h
- [ ] 4.8.3 | Add `NativeBridge.takeSharedText()` in `lib/core/native_bridge.dart` and extract the first link with the extractor (call a native `extractUrl` method or share the same regex) | Dart method plus unit test | 2h
- [ ] 4.8.4 | Handle the shared text in the dashboard on start and on resume: call `engine.handleLink(url, 'share')`; if no link is found show "No link found in what you shared" | `lib/screens/dashboard_screen.dart` | 2h
- [ ] 4.8.5 | Show the verdict even when it is SAFE (the share path must always answer): a result dialog with verdict, score, reasons | `lib/screens/link_result_screen.dart` | 3h
- [ ] 4.8.6 | If the user is not logged in when sharing, keep the text and scan after login | widget test with the fake gate | 2h
- [ ] 4.8.7 | Device test: share from Chrome, from WhatsApp, and share a text with no link | 3 screenshots | 1h
- [ ] 4.8.v | Verify gate: share a link from browser → verdict shown | recording from Chrome share sheet to the verdict screen | 1h

## 4.9 [FE] Per-app monitoring toggles + "trust this domain" list
Goal: the user can switch link monitoring off per app and trust domains; ignored items never reach the server.
Depends on: 4.7
- [ ] 4.9.1 | Add `shared_preferences` to `mobile/pubspec.yaml` and create `lib/core/settings_store.dart` with disabled package set and trusted domain set (load, save, add, remove) | class plus unit test with `SharedPreferences.setMockInitialValues` | 2h
- [ ] 4.9.2 | Domain match rule: a trusted `example.com` covers its subdomains but not `example.com.evil.xyz`; implement `isTrusted(host)` and test those cases | `test/settings_store_test.dart` passes | 2h
- [ ] 4.9.3 | Apply the filter in `handleLink`: skip when the source package is disabled or the host is trusted, before any server call | unit test: fake API receives 0 calls | 2h
- [ ] 4.9.4 | Push the disabled-package list to Kotlin (`SharedPreferences` file read by `AdNotificationListener.kt`) so ignored apps are dropped at the source and save battery | channel method `setDisabledPackages`; listener skips them | 2h
- [ ] 4.9.5 | Create `lib/screens/settings_screen.dart`: list of installed apps (labels from `NativeBridge.auditApps`) with a switch each, a search box, and the trusted domains list with delete | screen reachable from the dashboard app bar | 4h
- [ ] 4.9.6 | Add action "Trust this domain" to the link alert dialog and a confirmation text | alert action calls `settingsStore.addTrusted` | 2h
- [ ] 4.9.7 | Widget test for the settings screen (toggle saves, domain removed) | `test/settings_screen_test.dart` passes | 2h
- [ ] 4.9.v | Verify gate: disabled app's links are ignored | device test: disable WhatsApp, send a phishing link, no popup and no server call in the backend log; enable again, popup appears (recording) | 1h

## 4.10 [FE] Offline queue: links captured offline scanned when online
Goal: links captured without internet are saved safely and scanned when the connection returns.
Depends on: 4.7
- [ ] 4.10.1 | Add `sqflite` and `path` to `mobile/pubspec.yaml`; design the table `pending_links (id, url, source_pkg, captured_at, attempts)`; only the URL and package are stored (privacy rule) | schema in a comment in the new store class | 1h
- [ ] 4.10.2 | Create `lib/core/pending_link_store.dart` with an interface plus a SQLite implementation: `add`, `takeBatch(n)`, `remove(id)`, `incrementAttempts`, `purge(olderThan)` | class compiles | 3h
- [ ] 4.10.3 | Add an in-memory fake of the interface and unit tests: order kept, cap 200 rows (oldest dropped), entries older than 24 h purged, max 5 attempts | `test/pending_link_store_test.dart` passes | 2h
- [ ] 4.10.4 | In `handleLink`, on network failure (`ApiException` status 0 or timeout) add the URL to the store instead of dropping it; do not store on 4xx answers | edited `protection_engine.dart` plus test | 2h
- [ ] 4.10.5 | Add a retry trigger: at every fast-loop run and when connectivity returns (`connectivity_plus` plugin, or simply the fast loop if the plugin is not wanted) process up to 10 queued links | `flushPending()` method | 3h
- [ ] 4.10.6 | Make the popup text for a late verdict say "Scanned later: this link was received at <time>" | alert body includes the capture time | 1h
- [ ] 4.10.7 | Airplane-mode test on the device: turn on airplane mode, receive a link, turn it off | recording with the popup after reconnect | 2h
- [ ] 4.10.v | Verify gate: airplane-mode test passes | recording from 4.10.7 plus `flutter test` output of the store and engine tests | 1h
