# Phase 0 – Setup
## 0.3 [BE] CI pipeline (GitHub Actions: `mvn test`, `flutter analyze`, `flutter test`)
Goal: every push and pull request builds and tests backend and app automatically on GitHub.
Depends on: 0.2
- [ ] 0.3.1 | Initialise git (the folder is not a repo yet), check `.gitignore` covers `target/`, `mobile/android/`, `mobile/build/`, then commit | `git log --oneline` shows first commit; `git status` clean | 1h
- [ ] 0.3.2 | Create the GitHub repository and push branch `main` | repo URL; `.github/workflows/ci.yml` visible on GitHub | 1h
- [ ] 0.3.3 | Run the backend job commands locally in Docker with the docker socket mounted (Testcontainers needs it): `docker run --rm -v "E:/frud detection":/repo -v m2cache:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -w /repo/backend maven:3.9-eclipse-temurin-21 mvn -B -ntp clean verify` | console line with the total `Tests run` count and BUILD SUCCESS pasted in a note | 2h
- [ ] 0.3.4 | Review `.github/workflows/ci.yml` job `mobile`: pin the Flutter version to the one inside `ghcr.io/cirruslabs/flutter:stable` (run `flutter --version` in the image), keep `bash tool/setup_android.sh` step | edited `ci.yml` with `flutter-version:` line | 1h
- [ ] 0.3.5 | Lint the workflow file with a Docker actionlint image (verify the current image name first) and fix warnings | actionlint output with 0 errors | 1h
- [ ] 0.3.6 | Push a test branch and open a pull request; read the Actions log of both jobs and fix the first red step (typical: Gradle memory, missing SDK licences) | link to the run; both jobs green | 3h
- [ ] 0.3.7 | Make the test reports visible: confirm artifact `backend-test-reports` and `fraudshield-debug-apk` download, add a CI status badge to `README.md` | badge renders on the repo page | 1h
- [ ] 0.3.v | Verify gate: push to `main` triggers a green build for backend and mobile | Actions run URL showing 2 green jobs plus a screenshot | 1h

## 0.4 [BE] Config profiles `dev`/`prod`, secrets from env (`JWT_SECRET`, DB URL)
Goal: prod profile reads secrets from environment only and refuses to start with a missing or default secret.
Depends on: 0.2
- [x] 0.4.1 | Run the existing `ProdConfigGuardTest` in Docker (`mvn -B -ntp test -Dtest=ProdConfigGuardTest`) and read `ProdConfigGuard.java` against `application-prod.yml` for gaps | green test output; list of gaps in a note | 1h
- [x] 0.4.2 | Add missing unit cases to `ProdConfigGuardTest`: secret exactly 31 chars, secret starting `change-me`, DB password `fraud`, blank DB_URL | 4 new test methods pass | 2h
- [x] 0.4.3 | Write a start-up test that boots `FraudShieldApplication` with profile `prod` and no env and expects `IllegalStateException` naming `JWT_SECRET` | `ProdProfileStartupTest.java` passes | 2h
- [x] 0.4.4 | Make `docker-compose.yml` take DB password from `${DB_PASSWORD}` (guard rejects `fraud`) and add `backend/.env.example` listing `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `PORT` | edited `docker-compose.yml`, new `.env.example`; real `.env` is in `.gitignore` | 1h
- [x] 0.4.5 | Manual run in Docker, port 18080: prod profile with no env, then with `JWT_SECRET=dev-only-...`, then with a 48-char random secret and strong DB password | 3 console logs: 2 refusals with clear message, 1 successful boot | 2h
- [x] 0.4.6 | Confirm default profile is still `dev` (`spring.profiles.default`) and `/v3/api-docs` returns 404 in prod; document env variables in `README.md` | curl status 404 in prod; README section "Configuration" | 1h
- [x] 0.4.v | Verify gate: the app refuses to start in prod with the default JWT secret | log line `Cannot start in prod` or `JWT_SECRET is a development/default value` from step 0.4.5 | 1h

## 0.5 [FE] Flutter project generated + Kotlin/manifest merged
Goal: `flutter build apk --debug` succeeds inside Docker with our Kotlin code and manifest entries merged.
Depends on: none
- [ ] 0.5.1 | Retry the image download: `docker pull ghcr.io/cirruslabs/flutter:stable`; if it fails again, check network/proxy and try again later or on another network (verify that ghcr.io is reachable with `docker pull hello-world` first) | `docker images` lists the flutter image | 1h
- [ ] 0.5.2 | Create a reusable run command for Flutter in Docker with named volumes for pub and gradle caches: `docker run --rm -v "E:/frud detection/mobile":/app -v pubcache:/root/.pub-cache -v gradlecache:/root/.gradle -w /app ghcr.io/cirruslabs/flutter:stable <cmd>` and run `flutter --version` | version output saved in a note | 1h
- [ ] 0.5.3 | Run `bash tool/setup_android.sh` in the container; check that `mobile/android/app/src/main/kotlin/com/fraudshield/app/` holds all 7 `.kt` files and `AndroidManifest.xml` contains `AdNotificationListener` and the SEND intent-filter | `ls` output and grep result in a note | 2h
- [ ] 0.5.4 | Run `flutter pub get` and `flutter analyze`; fix Dart errors in `mobile/lib/` | analyzer output `No issues found` | 2h
- [ ] 0.5.5 | Run `flutter build apk --debug`; accept Android SDK licences if asked (`flutter doctor --android-licenses` with `yes`) | build log reaches Gradle task `assembleDebug` | 2h
- [ ] 0.5.6 | Fix Kotlin compile errors in `mobile/android-native/kotlin/*.kt` (check imports, missing `androidx.core` dependency, null-safety on `applicationInfo`) and rerun step 0.5.5 until it passes; edit sources in `android-native/`, not in generated `android/` | list of fixed files; build passes | 4h
- [ ] 0.5.v | Verify gate: `flutter build apk --debug` succeeds | log line `Built build/app/outputs/flutter-apk/app-debug.apk` and file size via `ls -l` | 1h

## 0.6 [FE] App ID/namespace `com.fraudshield.app`, minSdk 26, signing config (debug)
Goal: the built APK has id `com.fraudshield.app`, minSdk 26, debug signing, and installs on a device.
Depends on: 0.5
- [ ] 0.6.1 | Check generated `android/app/build.gradle` (or `.kts`) after the setup script: `namespace`, `applicationId` are `com.fraudshield.app`, `minSdk` is 26; fix the `sed` lines in `tool/setup_android.sh` if the template text differs | grep output of the three values | 1h
- [ ] 0.6.2 | Check the `signingConfig` of `release` still points to `debug` (template default) and add a comment line "replaced in task 9.5" in `tool/setup_android.sh` post-step or in a note | grep output | 1h
- [ ] 0.6.3 | Inspect the built APK with `aapt dump badging app-debug.apk` (inside the Flutter image build-tools) for package name and `sdkVersion:'26'` | aapt output saved | 1h
- [ ] 0.6.4 | Choose the install route, since Docker cannot run an emulator: real phone with wireless debugging (`adb connect` from a container with platform-tools, verify the exact command) or copying the APK to the phone | note with chosen route and steps | 2h
- [ ] 0.6.5 | Install the APK and launch it; open Login screen and Dashboard once | screenshot of login screen on the device | 2h
- [ ] 0.6.v | Verify gate: APK installs on emulator or device | `adb shell dumpsys package com.fraudshield.app` showing `versionName=0.1.0` and `minSdk=26`, plus screenshot | 1h
