# Phase 9 – Release
## 9.1 [BE] Production deploy (Docker + managed Postgres), backups, monitoring (health, alerts)
Goal: run the backend in production from Docker with a managed database, nightly backups and a health alert.
Depends on: 0.4, 8.3
- [ ] 9.1.1 | Verify free hosting options today (one small VM plus free managed Postgres tier); compare limits, region, backup features, terms; pick one | docs/deploy/hosting-choice.md with chosen provider and the links checked | 2h
- [ ] 9.1.2 | Create multi-stage Dockerfile (maven:3.9-eclipse-temurin-21 build stage, eclipse-temurin:21-jre run stage, non-root user) | backend/Dockerfile; `docker build -t fraudshield-backend backend` succeeds | 2h
- [ ] 9.1.3 | Add spring-boot-starter-actuator, expose only `/actuator/health` (liveness and readiness), permit it in SecurityConfig, keep everything else closed in application-prod.yml | pom.xml, SecurityConfig.java, application-prod.yml; test `HealthEndpointIT` returns 200 without a token | 2h
- [ ] 9.1.4 | Write docker-compose.prod.yml (app + reverse proxy with automatic HTTPS, DB URL pointing to the managed Postgres) and .env.example listing DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET (no real values) | backend/docker-compose.prod.yml, backend/.env.example | 3h
- [ ] 9.1.5 | Write backup script (pg_dump to a dated file, keep 7 days, copy off the server) and run one restore into a scratch Postgres container | backend/scripts/backup.sh, backend/scripts/restore-test.md with the restore output | 3h
- [ ] 9.1.6 | Set up a free uptime monitor on the health URL with an e-mail alert (verify current free plan limits first); stop the app once to prove the alert arrives | Screenshot of the alert e-mail in docs/deploy/monitoring.md | 2h
- [ ] 9.1.7 | Write smoke script (register, login, POST /scan/url, DELETE /me) and deploy to a staging server using the prod profile | backend/scripts/smoke.sh; staging run log with all steps OK | 3h
- [ ] 9.1.8 | Check prod safety: start with default JWT secret and confirm refusal (ProdConfigGuard), confirm swagger is off, confirm HTTPS redirect | docs/deploy/prod-checklist.md all ticked | 2h
- [ ] 9.1.v | Verify gate: staging promoted to prod; `bash backend/scripts/smoke.sh https://<prod-host>` passes, health alert tested, one backup restored | smoke output plus restore log attached to the Jira ticket | 2h

## 9.2 [BE] Data-deletion + privacy endpoints verified (audit part only; the endpoint itself is done)
Goal: prove that `DELETE /me` leaves no row or log trace of the user anywhere.
Depends on: 1.7
- [ ] 9.2.1 | List every table and every place (logs, caches, backups) that can hold user data, including tables added in Phases 10-16; check each has `ON DELETE CASCADE` or an explicit delete in ThreatService.deleteAccount | docs/privacy/data-inventory.md (table, column, how it is deleted) | 2h
- [ ] 9.2.2 | Write integration test that creates a user with a device, threats and feedback rows, calls DELETE /me, then counts rows per table | backend/src/test/java/com/fraudshield/DeleteMeAuditIT.java passes | 3h
- [ ] 9.2.3 | Add a generic guard to that test: query information_schema for every table with a `user_id` column and assert it has 0 rows for the deleted user, so new tables are checked automatically | Test `noUserRowsRemainInAnyTable` in DeleteMeAuditIT | 2h
- [ ] 9.2.4 | Test that the old JWT after DELETE /me gets 401 (not 500) on GET /threats and on a second DELETE /me; fix JwtService or the filter if it fails | Test `oldTokenRejectedAfterDelete`; fix in security/JwtService.java if needed | 2h
- [ ] 9.2.5 | Log audit: run the IT suite with log capture and grep for the test email and test URL query strings | docs/privacy/log-audit.md with the grep command and an empty result | 2h
- [ ] 9.2.6 | Verify what India's DPDP Act 2023 and Play data-safety rules require for access, correction and erasure; decide if a `GET /me/export` is needed and how long backups keep deleted data | docs/privacy/legal-notes.md with sources and the decision | 2h
- [ ] 9.2.7 | If export is needed, add `GET /me/export` returning the user's rows as JSON, with a test; otherwise record "not needed" with the reason | ThreatController or new MeController, test in ApiFlowIT | 3h
- [ ] 9.2.v | Verify gate: `DELETE /me` removes all rows (DeleteMeAuditIT green) and logs are clean | `docker run --rm -v "E:/frud detection/backend":/app -v m2cache:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn verify` shows DeleteMeAuditIT passing | 1h

## 9.3 [FE] Play Console declaration forms (QUERY_ALL_PACKAGES, MANAGE_EXTERNAL_STORAGE, notification listener, CALL_PHONE) with demo video
Goal: submit every sensitive-permission declaration with an honest justification and a demo video.
Depends on: 9.4
- [ ] 9.3.1 | Verify current Google Play policy pages for each permission (QUERY_ALL_PACKAGES, MANAGE_EXTERNAL_STORAGE, notification access, CALL_PHONE and READ_PHONE_STATE, USE_FULL_SCREEN_INTENT, VpnService) and note what each form asks | docs/play/policy-notes.md with link and date checked per permission | 3h
- [ ] 9.3.2 | Review mobile/android-native/AndroidManifest.snippet.xml and remove or narrow any permission whose feature is not shipped in the release | Updated manifest snippet plus list of removed permissions in docs/play/policy-notes.md | 2h
- [ ] 9.3.3 | Write the declaration text per permission (what feature, why no lighter API works, what data stays on phone) | docs/play/permission-declarations.md | 3h
- [ ] 9.3.4 | Write a storyboard for one demo video per permission (open app, show consent, trigger feature, show result) | docs/play/video-storyboard.md | 1h
- [ ] 9.3.5 | Record the demo videos on a real phone with screen recorder and upload them as unlisted links (verify the form accepts the link type) | Video links listed in docs/play/permission-declarations.md | 3h
- [ ] 9.3.6 | Register the Play developer account if not done (one-time fee, identity checks); verify current account requirements | Account ready, screenshot of Play Console home | 2h
- [ ] 9.3.7 | Fill Data safety form and each declaration form in Play Console from the drafted text | Screenshots of submitted forms in docs/play/ | 3h
- [ ] 9.3.v | Verify gate: all forms show status Submitted in Play Console | Screenshot of the App content page with every form submitted | 1h

## 9.4 [FE] Privacy policy + in-app consent screens
Goal: a public privacy policy URL and a first-run consent screen that matches what the app really does.
Depends on: 9.2
- [ ] 9.4.1 | Write the privacy policy in plain English and Hindi: what leaves the phone (URL or domain, file hash, package names, counters), what never does, message text only on opt-in, retention, how to delete | docs/privacy/privacy-policy.md and privacy-policy-hi.md | 3h
- [ ] 9.4.2 | Host the policy on a free static page (verify provider terms) and record the public URL | Live URL opened in a browser, noted in README.md | 2h
- [ ] 9.4.3 | Create consent screen with plain-language bullets and a link to the policy; block the app until accepted; store the choice and policy version locally | mobile/lib/screens/consent_screen.dart, gate added in mobile/lib/main.dart | 3h
- [ ] 9.4.4 | Add policy link to the login screen and a Settings entry; add "Delete my account and data" button calling DELETE /me through api_client.dart | Changes in login_screen.dart, api_client.dart (`deleteMe()`), new settings entry | 3h
- [ ] 9.4.5 | Widget tests: consent shown on first run, app blocked until accepted, delete button asks for confirmation | mobile/test/consent_screen_test.dart passes | 2h
- [ ] 9.4.6 | Check policy text against the app: list every network call in api_client.dart and confirm each is covered | docs/privacy/policy-vs-app-check.md | 2h
- [ ] 9.4.v | Verify gate: URL live and linked in app | `docker run --rm -v "E:/frud detection/mobile":/app -w /app ghcr.io/cirruslabs/flutter:stable sh -c "flutter analyze && flutter test"` is green, plus screenshots of consent screen and policy page | 1h

## 9.5 [FE] Signed release AAB + versioning
Goal: produce a signed, versioned release bundle that Play accepts on the internal testing track.
Depends on: 9.4
- [ ] 9.5.1 | Decide the versioning rule (versionName 1.0.0, versionCode increases every upload) and set `version:` in pubspec.yaml | mobile/pubspec.yaml version line, rule written in docs/release/versioning.md | 1h
- [ ] 9.5.2 | Generate the upload keystore with keytool in a Docker JDK image; keep it outside the repo and back it up in two places | Keystore file path noted (not committed), .gitignore entry for *.jks and key.properties | 2h
- [ ] 9.5.3 | Add signing config reading key.properties; update mobile/tool/setup_android.sh so a regenerated android folder keeps it | Changes in tool/setup_android.sh and android/app/build.gradle | 3h
- [ ] 9.5.4 | Enable R8 shrink with keep rules for MethodChannel classes and notification listener (shared with 8.9) | proguard-rules.pro; release build starts without missing-class errors | 3h
- [ ] 9.5.5 | Build the bundle with the Flutter Docker image, mounting the keystore read-only | `flutter build appbundle --release` output app-release.aab | 2h
- [ ] 9.5.6 | Check the signature with jarsigner or bundletool and test the release build on a real phone | Command output showing signer; screenshot of app running | 2h
- [ ] 9.5.7 | Enable Play App Signing and upload the AAB to the internal testing track | Screenshot of the internal release in Play Console | 2h
- [ ] 9.5.v | Verify gate: AAB uploads to the internal track and installs from the test link | Play Console screenshot plus installed-app screenshot | 1h

## 9.6 [FE] Crash reporting (Firebase Crashlytics)
Goal: crashes reach a dashboard, but only when the user agrees.
Depends on: 9.4
- [ ] 9.6.1 | Verify Crashlytics is free on the current Firebase plan and what data it collects (device model, install id); decide default is OFF until the user opts in, to keep the privacy promise | docs/privacy/crash-reporting-decision.md | 2h
- [ ] 9.6.2 | Create the Firebase project and register com.fraudshield.app; keep google-services.json out of git | Firebase console screenshot; .gitignore entry | 1h
- [ ] 9.6.3 | Add firebase_core and firebase_crashlytics (verify current versions) and the Gradle plugins in tool/setup_android.sh | mobile/pubspec.yaml, tool/setup_android.sh | 3h
- [ ] 9.6.4 | Initialise in main.dart: FlutterError.onError, PlatformDispatcher.onError, collection disabled until opt-in | main.dart changes | 2h
- [ ] 9.6.5 | Add opt-in toggle in Settings plus a line in the consent screen and the privacy policy | Settings entry, updated policy files | 2h
- [ ] 9.6.6 | Add a debug-only "Test crash" button for Dart and one for a Kotlin crash | Button visible only in debug builds | 1h
- [ ] 9.6.v | Verify gate: test crash appears in Crashlytics with opt-in on, and no report with opt-in off | Console screenshots of both cases | 1h

## 9.7 [FE] Closed beta (20 users), feedback triage
Goal: run a 2-week closed beta with 20 real users and fix what they find.
Depends on: 9.3, 9.5, 9.6
- [ ] 9.7.1 | Verify the current Play rule for closed testing before production (tester count and days) and plan the beta around it | docs/beta/plan.md with the rule and link | 1h
- [ ] 9.7.2 | Recruit 20 testers (friends, family, colleagues; mix of phone brands), collect their Google accounts | docs/beta/testers.csv (no personal data in git: keep names as initials) | 3h
- [ ] 9.7.3 | Create the closed testing track, add testers, send the join link and a one-page "what to test" guide | Play Console screenshot; docs/beta/tester-guide.md | 2h
- [ ] 9.7.4 | Make a feedback form (free) and add an in-app link; define triage labels (crash, wrong verdict, annoying, idea) | Form URL in Settings; docs/beta/triage.md | 2h
- [ ] 9.7.5 | Triage weekly: copy each issue into Jira with label and priority; fix all crashes and wrong-verdict bugs | Jira export attached; list of fixed bugs | 4h
- [ ] 9.7.6 | Ship fix builds (new versionCode) to the track and ask testers to update | Release notes in docs/beta/releases.md | 2h
- [ ] 9.7.v | Verify gate: 14 days finished with crash-free users at 99% or more | Crashlytics crash-free screenshot plus docs/beta/report.md | 2h
