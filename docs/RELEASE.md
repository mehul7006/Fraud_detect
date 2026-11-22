# Release build (roadmap 8.9, 9.5)

## Versioning
`mobile/pubspec.yaml` -> `version: 1.2.0+5` = version name **1.2.0**, version code **5**. Raise the number after `+`
for every upload to Google Play (it must always go up).

## Signing
- Upload key: `_devtools/keys/fraudshield-upload.jks` (git-ignored), passwords in `.env`
  (`RELEASE_*`), created once by `python tools/release/make_upload_key.py`, which also writes
  `mobile/android/key.properties` (git-ignored). **Back up the .jks and the passwords.**
- Use **Play App Signing**: Google keeps the real app-signing key; our key only uploads. A lost upload key can be reset.
- A distributable build without `key.properties` stops with an error, so a debug-signed app can never be shipped.

## Build
```
cd mobile
bash tool/setup_android.sh            # once / after a Flutter upgrade (idempotent)
flutter build appbundle --release --obfuscate --split-debug-info=build/symbols \
    --dart-define API_BASE=https://<your API host>/api/v1
```
- R8 shrinks + obfuscates the Kotlin/Java code (`android-native/proguard-rules.pro`); `--obfuscate` does the same for
  Dart. Keep `build/symbols` and `build/app/outputs/mapping/release/mapping.txt` per version: they turn crash
  reports back into readable stack traces.
- A release build refuses an `http://` API address (`checkBaseUrl`), so data never goes over plain http.

## Before uploading (checklist)
- [ ] All tests green (backend `mvn verify`, `flutter test`, Kotlin tests)
- [ ] `tools/security/scan.sh` -> 0 open vulnerabilities
- [ ] Release AAB installs and starts on an emulator; login, link check, background link check work (R8 did not
      remove anything needed)
- [ ] Privacy policy URL live and linked (9.4); Play Console declarations filled (9.3)
