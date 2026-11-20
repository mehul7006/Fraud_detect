# Phase 8 – Hardening
## 8.1 [BE] Rate limiting per user/IP (Bucket4j)
Goal: Requests above 100 per minute get HTTP 429 in the standard error format.
Depends on: none
- [ ] 8.1.1 | Verify the current Bucket4j artifact and version that works with Spring Boot 3 and add it to pom.xml | backend/pom.xml | 1h
- [ ] 8.1.2 | Add config keys fraudshield.ratelimit.per-minute (default 100) and stricter limit for /auth/** (for example 10) | application.yml | 1h
- [ ] 8.1.3 | Create RateLimitFilter: key is user id when authenticated, else client IP; in-memory buckets with expiry | backend/src/main/java/com/fraudshield/security/RateLimitFilter.java | 3h
- [ ] 8.1.4 | Return 429 with the common ErrorBody and a Retry-After header | RateLimitFilter.java, error/ErrorBody.java | 1h
- [ ] 8.1.5 | Decide how to read the real client IP behind a proxy (X-Forwarded-For only from trusted proxy) and document it | comment in RateLimitFilter.java, docs/02-ARCHITECTURE.md | 2h
- [ ] 8.1.6 | Register the filter in SecurityConfig after the request-id filter | SecurityConfig.java | 1h
- [ ] 8.1.7 | Integration test with a low limit in test profile: request N+1 gives 429, a different user is not affected | ApiFlowIT.java test rateLimitReturns429 | 2h
- [ ] 8.1.v | Verify gate: 100 req/min limit returns 429 | loop of 105 curl calls to /scan/url on port 18080 showing the first 429 | 1h

## 8.2 [BE] Refresh tokens + logout/revoke
Goal: Short access tokens with refresh and a logout that makes old tokens unusable.
Depends on: none
- [ ] 8.2.1 | Design: access token 15-60 min, refresh token random, stored hashed, rotation on each refresh; write the endpoints into the API spec | docs/04-API-SPEC.md | 2h
- [ ] 8.2.2 | Add table refresh_tokens (user_id, token_hash, expires_at, revoked_at, device_id) and migration | backend/src/main/resources/db/migration/V6__refresh_tokens.sql (next free version) | 2h
- [ ] 8.2.3 | Add entity, repository and RefreshTokenService (issue, rotate, revoke, revoke all) | backend/src/main/java/com/fraudshield/service/RefreshTokenService.java | 3h
- [ ] 8.2.4 | Add token version (or jti blacklist) so access tokens of a logged-out user are rejected | JwtService.java, SecurityConfig.java, User.java | 3h
- [ ] 8.2.5 | Add endpoints POST /auth/refresh and POST /auth/logout; login now returns both tokens | AuthController.java, Dtos.java | 2h
- [ ] 8.2.6 | Integration tests: refresh gives new pair, old refresh token rejected after rotation, old access token rejected after logout, expired refresh gives 401 | ApiFlowIT.java | 3h
- [ ] 8.2.v | Verify gate: old token rejected after logout | curl: login, logout, call /threats with old token returns 401 | 1h

## 8.3 [BE] HTTPS/TLS, CORS, security headers
Goal: Traffic is served over TLS with safe headers and an explicit CORS policy.
Depends on: none
- [ ] 8.3.1 | Add security headers in SecurityConfig (HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy, CSP for the API) | SecurityConfig.java | 2h
- [ ] 8.3.2 | Add CORS config with an allow-list from env (none by default; the app is not a browser) | backend/src/main/java/com/fraudshield/config/CorsConfig.java | 1h
- [ ] 8.3.3 | Test headers and CORS: expected headers present, unknown origin refused | ApiFlowIT.java test securityHeadersPresent | 2h
- [ ] 8.3.4 | Decide TLS termination (reverse proxy such as Caddy or nginx with free Let's Encrypt, or Spring SSL) and verify the current Let's Encrypt rules | decision note in docs/02-ARCHITECTURE.md | 2h
- [ ] 8.3.5 | Write a docker-compose TLS proxy example with a test domain | backend/docker-compose.tls.yml and proxy config file | 3h
- [ ] 8.3.6 | Force HTTPS in prod profile (redirect or reject, trust forwarded headers) | application-prod.yml | 1h
- [ ] 8.3.v | Verify gate: SSL Labs grade A on the public staging host | screenshot or JSON of the SSL Labs result (needs a public domain; local check with testssl.sh in Docker meanwhile) | 2h

## 8.4 [BE] No-PII logging review; log scrubbing
Goal: Logs hold no email and no URL query strings.
Depends on: none
- [ ] 8.4.1 | Grep all logger calls and exception messages in src/main for email, URL, token and file names; list findings | list as a table in docs/ notes or PR text | 2h
- [ ] 8.4.2 | Fix each finding (remove or mask) | files touched in backend/src/main/java/com/fraudshield/ | 3h
- [ ] 8.4.3 | Add a scrubbing helper (mask email, strip query string) and use it where values must be logged | backend/src/main/java/com/fraudshield/util/LogScrub.java | 2h
- [ ] 8.4.4 | Add a log pattern converter or filter as a safety net that masks emails and query strings | application.yml, logback config | 3h
- [ ] 8.4.5 | Write unit tests for LogScrub and an integration test that captures logs during login and scan and asserts no email or query string | backend/src/test/java/com/fraudshield/LogPrivacyIT.java | 3h
- [ ] 8.4.v | Verify gate: grep of logs finds no email or URL query string | command: docker logs on a run that did register, login, scan url with ?token=abc, then grep for the email and abc returns nothing | 1h

## 8.5 [BE] Unit + integration coverage ≥80% (JaCoCo)
Goal: JaCoCo report shows line coverage of 80% or more.
Depends on: none
- [ ] 8.5.1 | Add the JaCoCo plugin (unit and integration data merged) to pom.xml | backend/pom.xml | 2h
- [ ] 8.5.2 | Run mvn verify in Docker and read the baseline report; list classes below 80% | report in backend/target/site/jacoco/ and a short list | 2h
- [ ] 8.5.3 | Add unit tests for JwtService, ThreatService and error handler gaps | backend/src/test/java/com/fraudshield/ | 3h
- [ ] 8.5.4 | Add unit tests for CloneRiskService and FileScanService branches not yet covered | ScanServicesTest.java and new test files | 3h
- [ ] 8.5.5 | Add the coverage check rule (fail below 80%) to the verify phase | pom.xml jacoco:check | 1h
- [ ] 8.5.v | Verify gate: report 80% or more | mvn verify output with the jacoco check passing and the HTML report | 1h

## 8.6 [BE] Dependency/vulnerability scan (OWASP dep-check)
Goal: The scan finds 0 high CVEs in dependencies.
Depends on: none
- [ ] 8.6.1 | Verify the current dependency-check Maven plugin version and whether a free NVD API key is needed; request one if so | note and env var NVD_API_KEY | 1h
- [ ] 8.6.2 | Add the plugin to pom.xml in a separate profile so normal builds stay fast | backend/pom.xml profile security-scan | 1h
- [ ] 8.6.3 | Run it in Docker with a mounted data folder to cache the NVD data | command in README or docs, report in backend/target/dependency-check-report.html | 2h
- [ ] 8.6.4 | Fix each high finding (upgrade a dependency or suppress with a written reason) | pom.xml, backend/dependency-check-suppressions.xml | 3h
- [ ] 8.6.5 | Set failBuildOnCVSS to 7 in the profile | pom.xml | 1h
- [ ] 8.6.v | Verify gate: 0 high CVEs | report summary showing zero findings with CVSS 7 or higher | 1h

## 8.7 [BE] Load test (k6): 200 req/s on `/scan/url`
Goal: p95 latency below 300 ms at 200 requests per second.
Depends on: 8.1
- [ ] 8.7.1 | Write the k6 script: login once, then constant 200 req/s to /scan/url with 20 mixed URLs, threshold p95 < 300 ms | backend/loadtest/scan-url.js | 2h
- [ ] 8.7.2 | Run the backend and Postgres with docker compose on port 18080 with realistic settings (rate limit raised for the test user) | command in backend/loadtest/README or docs | 1h
- [ ] 8.7.3 | Run k6 using the grafana/k6 Docker image and save the summary | backend/loadtest/result-1.txt | 2h
- [ ] 8.7.4 | Find the slowest part if the target is missed (DB query, BCrypt, logging, JVM size) and fix one cause | code or config change noted in the result file | 3h
- [ ] 8.7.5 | Re-run and compare before and after | backend/loadtest/result-2.txt | 2h
- [ ] 8.7.v | Verify gate: p95 under 300 ms at 200 req/s | k6 summary showing the threshold passed | 1h

## 8.8 [FE] Battery profiling (<3%/day)
Goal: The app uses less than 3% battery per day with protection running.
Depends on: 7.11
- [ ] 8.8.1 | Add counters for loop runs, scans and wake-ups in the service (local debug log only) | mobile/android-native/kotlin/ProtectionService.kt | 2h
- [ ] 8.8.2 | Remove obvious waste: reduce the 20 s loop when idle, batch SharedPreferences writes in AdNotificationListener | ProtectionService.kt, AdNotificationListener.kt | 3h
- [ ] 8.8.3 | Prepare the device: reset battery stats, adb shell dumpsys batterystats --reset, and note the test steps | test steps under docs/ | 1h
- [ ] 8.8.4 | Run a 24 h test on a real phone with normal use and capture a bugreport | bugreport zip file | 2h
- [ ] 8.8.5 | Read the report in Battery Historian (Docker image) or dumpsys batterystats and extract the app's share | screenshot or text of the app's mAh and percent | 3h
- [ ] 8.8.6 | Fix the biggest consumer if above 3% and re-test | change note plus second report | 4h
- [ ] 8.8.v | Verify gate: Battery Historian report shows under 3% per day | report file | 1h

## 8.9 [FE] Certificate pinning + obfuscation (R8)
Goal: A release build is obfuscated and only trusts the backend's certificate.
Depends on: 8.3
- [ ] 8.9.1 | Decide the pinning method (network security config pin-set for native calls, and a pinned HttpClient for Dart) and the pin source (public key hash of the server certificate, plus a backup pin) | decision note in docs/02-ARCHITECTURE.md | 2h
- [ ] 8.9.2 | Add network_security_config.xml with pin-set and expiry and reference it in the manifest | mobile/android/app/src/main/res/xml/network_security_config.xml | 2h
- [ ] 8.9.3 | Apply the same pin in the Dart ApiClient (SecurityContext or pinning package; verify its current version) | mobile/lib/core/api_client.dart | 3h
- [ ] 8.9.4 | Enable R8 (minifyEnabled, shrinkResources) in the release build type | mobile/android/app/build.gradle | 1h
- [ ] 8.9.5 | Add proguard rules to keep the MethodChannel classes, services and receivers | mobile/android/app/proguard-rules.pro | 2h
- [ ] 8.9.6 | Test pinning with a proxy (mitmproxy in Docker): the app must refuse the proxy certificate | proxy log showing the failed connection | 2h
- [ ] 8.9.7 | Build a release APK and run the main flows (login, link scan, file scan) | flutter build apk --release output plus short test notes | 2h
- [ ] 8.9.v | Verify gate: release build with R8 works and the proxy test is refused | build log plus proxy log | 1h

## 8.10 [FE] Error/empty/offline states on every screen
Goal: Each screen shows a clear message for error, empty and offline cases.
Depends on: none
- [ ] 8.10.1 | List all screens and the three states each needs (login, dashboard, threats, takeover wizard, risky apps, settings) in a checklist | checklist file under docs/ | 1h
- [ ] 8.10.2 | Create shared widgets ErrorView, EmptyView and OfflineBanner | mobile/lib/widgets/state_views.dart | 2h
- [ ] 8.10.3 | Apply them to the threats screen | mobile/lib/screens/threats_screen.dart | 2h
- [ ] 8.10.4 | Apply them to the dashboard (tiles show a retry when the backend is unreachable) | mobile/lib/screens/dashboard_screen.dart | 3h
- [ ] 8.10.5 | Apply them to login and the risky apps screen | login_screen.dart, risky_apps_screen.dart | 2h
- [ ] 8.10.6 | Write widget tests for each state with a fake ApiClient | mobile/test/states_test.dart | 3h
- [ ] 8.10.v | Verify gate: manual checklist done in airplane mode and with the backend stopped | ticked checklist plus screenshots | 2h

## 8.11 [FE] Localisation: English + Hindi
Goal: The user can switch between English and Hindi and every visible text changes.
Depends on: 8.10
- [ ] 8.11.1 | Add flutter_localizations and intl setup (verify current versions) and l10n.yaml | mobile/pubspec.yaml, mobile/l10n.yaml | 1h
- [ ] 8.11.2 | Create app_en.arb by moving all hard-coded strings out of the screens | mobile/lib/l10n/app_en.arb | 4h
- [ ] 8.11.3 | Replace the strings in the screens with generated lookups | all files in mobile/lib/screens/ | 4h
- [ ] 8.11.4 | Create app_hi.arb with Hindi text (get a native speaker to review) | mobile/lib/l10n/app_hi.arb | 4h
- [ ] 8.11.5 | Translate the native alert texts created in Kotlin and in protection_engine.dart | AlertNotifier.kt, protection_engine.dart | 3h
- [ ] 8.11.6 | Add a language switch in settings and remember the choice | settings screen, mobile/lib/core/locale_store.dart | 2h
- [ ] 8.11.7 | Widget test: switch to Hindi and check key strings | mobile/test/l10n_test.dart | 2h
- [ ] 8.11.v | Verify gate: language switch works on every screen | screenshots of each screen in both languages | 1h

## 8.12 [FE] Accessibility (TalkBack, large text)
Goal: The app passes a TalkBack and large-text checklist.
Depends on: 8.10
- [ ] 8.12.1 | Write the checklist (labels on buttons and icons, focus order, contrast 4.5:1, touch target 48 dp, text at 200% scale, no colour-only status) | checklist file under docs/ | 1h
- [ ] 8.12.2 | Add Semantics labels to icons, tiles and permission cards | mobile/lib/screens/dashboard_screen.dart and other screens | 3h
- [ ] 8.12.3 | Fix layouts that overflow at 200% text scale | all screens | 3h
- [ ] 8.12.4 | Fix colour-only status (add text or icon) and low contrast | dashboard_screen.dart, theme in main.dart | 2h
- [ ] 8.12.5 | Make the blocking alert dialog readable by TalkBack (announce title, focus first action) | dashboard_screen.dart _drain | 2h
- [ ] 8.12.6 | Add a widget test using the Flutter accessibility guideline matchers (labeled tap target, tap target size, contrast) | mobile/test/a11y_test.dart | 2h
- [ ] 8.12.7 | Walk through all main flows with TalkBack on a real phone and tick the checklist | ticked checklist | 3h
- [ ] 8.12.v | Verify gate: checklist passes | ticked checklist plus flutter test output | 1h

## 8.13 [FE] Real-device test matrix (Samsung, Xiaomi, Realme, Pixel × Android 10–14)
Goal: A filled test matrix with all found bugs closed.
Depends on: 7.11, 8.9
- [ ] 8.13.1 | Write the test script: install, login, grant permissions, link scan, file scan, app closed scan, alert on lock screen, call forwarding check | docs/device-test-script.md | 2h
- [ ] 8.13.2 | Create the matrix table (device, Android version, each script step, result) | docs/device-test-matrix.md | 1h
- [ ] 8.13.3 | Choose the real devices or cloud labs that are free to use; verify availability | chosen list at top of the matrix | 2h
- [ ] 8.13.4 | Run the script on Pixel (stock Android) 10 to 14 | filled matrix rows | 4h
- [ ] 8.13.5 | Run the script on Samsung and note One UI battery limits | filled matrix rows | 3h
- [ ] 8.13.6 | Run the script on Xiaomi and Realme and note autostart and background limits | filled matrix rows | 3h
- [ ] 8.13.7 | Log every bug as a ticket and fix the blockers | ticket list and fix notes | 4h
- [ ] 8.13.v | Verify gate: matrix filled and bugs closed | final matrix with all rows passing and zero open blockers | 2h
