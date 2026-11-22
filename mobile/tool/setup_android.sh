#!/usr/bin/env bash
# Generates the Android shell for the Flutter app and merges our native Kotlin code + manifest entries.
# Idempotent: safe to run again. Used locally, in Docker verification and in CI.
# Usage (from the mobile/ folder):  bash tool/setup_android.sh
set -euo pipefail
cd "$(dirname "$0")/.."

PKG_DIR=android/app/src/main/kotlin/com/fraudshield/app

flutter create . --platforms=android --org com.fraudshield --project-name fraudshield
rm -f test/widget_test.dart   # template test refers to a MyApp we don't have

# applicationId / namespace -> com.fraudshield.app (matches the Kotlin package)
grep -rl "com.fraudshield.fraudshield" android 2>/dev/null | xargs -r sed -i 's/com\.fraudshield\.fraudshield/com.fraudshield.app/g'
rm -rf android/app/src/main/kotlin/com/fraudshield/fraudshield
mkdir -p "$PKG_DIR"
cp android-native/kotlin/*.kt "$PKG_DIR/"

# unit tests for pure-Kotlin code (4.6): copy tests and add JUnit
mkdir -p android/app/src/test/kotlin/com/fraudshield/app
cp android-native/test/*.kt android/app/src/test/kotlin/com/fraudshield/app/ 2>/dev/null || true
for f in android/app/build.gradle.kts android/app/build.gradle; do
  if [ -f "$f" ] && ! grep -q "junit:junit" "$f"; then
    if [[ "$f" == *.kts ]]; then printf '
dependencies {
    testImplementation("junit:junit:4.13.2")
}
' >> "$f"
    else printf '
dependencies {
    testImplementation "junit:junit:4.13.2"
}
' >> "$f"; fi
  fi
done

# Windows with the project and the pub cache on different drives breaks Kotlin incremental caches
grep -q 'kotlin.incremental' android/gradle.properties || echo 'kotlin.incremental=false' >> android/gradle.properties

# minSdk 26 (USSD API) - works for both groovy and kotlin gradle files
for f in android/app/build.gradle android/app/build.gradle.kts; do
  [ -f "$f" ] && sed -i 's/minSdk = flutter.minSdkVersion/minSdk = 26/; s/minSdkVersion flutter.minSdkVersion/minSdkVersion 26/' "$f"
done

# merge manifest entries (idempotent: each permission / component is added only if missing, so re-running
# the script on an existing android/ folder also picks up permissions added in later phases)
PYBIN=$(command -v python3 || command -v python)
"$PYBIN" - <<'PY'
p = 'android/app/src/main/AndroidManifest.xml'
m = open(p, encoding='utf-8').read()
perms = ['INTERNET', 'POST_NOTIFICATIONS', 'USE_FULL_SCREEN_INTENT', 'CALL_PHONE', 'READ_PHONE_STATE',
         'QUERY_ALL_PACKAGES', 'MANAGE_EXTERNAL_STORAGE', 'PACKAGE_USAGE_STATS', 'REQUEST_DELETE_PACKAGES']
add = ''.join(f'    <uses-permission android:name="android.permission.{x}"/>\n' for x in perms
              if f'android.permission.{x}"' not in m)
if add:
    m = m.replace('<application', add + '    <application', 1)
if 'AdNotificationListener' not in m:
    svc = ('        <service android:name=".AdNotificationListener" android:exported="true" '
           'android:label="FraudShield link and ad monitor" '
           'android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">\n'
           '            <intent-filter><action android:name="android.service.notification.NotificationListenerService"/></intent-filter>\n'
           '        </service>\n')
    m = m.replace('</application>', svc + '    </application>', 1)
if 'android.intent.action.SEND' not in m:
    share = ('            <intent-filter><action android:name="android.intent.action.SEND"/>'
             '<category android:name="android.intent.category.DEFAULT"/><data android:mimeType="text/plain"/></intent-filter>\n')
    m = m.replace('</activity>', share + '        </activity>', 1)
open(p, 'w', encoding='utf-8').write(m)
print('manifest merged')
PY

# Debug builds only: allow plain http so the emulator can reach a local backend at http://10.0.2.2:<port>.
# Release builds are NOT touched and stay https-only (Android default for apps targeting API 28+).
"$PYBIN" - <<'PY'
p = 'android/app/src/debug/AndroidManifest.xml'
m = open(p, encoding='utf-8').read()
if 'usesCleartextTraffic' in m:
    print('debug manifest already allows cleartext'); raise SystemExit
m = m.replace('</manifest>', '    <application android:usesCleartextTraffic="true"/>\n</manifest>', 1)
open(p, 'w', encoding='utf-8').write(m)
print('debug manifest: cleartext allowed (debug only)')
PY

flutter pub get
echo "Android setup complete."
