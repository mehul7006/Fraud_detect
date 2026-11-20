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

# minSdk 26 (USSD API) - works for both groovy and kotlin gradle files
for f in android/app/build.gradle android/app/build.gradle.kts; do
  [ -f "$f" ] && sed -i 's/minSdk = flutter.minSdkVersion/minSdk = 26/; s/minSdkVersion flutter.minSdkVersion/minSdkVersion 26/' "$f"
done

# merge manifest entries once
python3 - <<'PY'
p = 'android/app/src/main/AndroidManifest.xml'
m = open(p, encoding='utf-8').read()
if 'AdNotificationListener' in m:
    print('manifest already merged'); raise SystemExit
perms = ''.join(f'    <uses-permission android:name="android.permission.{x}"/>\n' for x in
    ['INTERNET', 'POST_NOTIFICATIONS', 'USE_FULL_SCREEN_INTENT', 'CALL_PHONE', 'READ_PHONE_STATE',
     'QUERY_ALL_PACKAGES', 'MANAGE_EXTERNAL_STORAGE'])
svc = ('        <service android:name=".AdNotificationListener" android:exported="true" '
       'android:label="FraudShield link and ad monitor" '
       'android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">\n'
       '            <intent-filter><action android:name="android.service.notification.NotificationListenerService"/></intent-filter>\n'
       '        </service>\n')
share = ('            <intent-filter><action android:name="android.intent.action.SEND"/>'
         '<category android:name="android.intent.category.DEFAULT"/><data android:mimeType="text/plain"/></intent-filter>\n')
m = m.replace('<application', perms + '    <application', 1)
m = m.replace('</application>', svc + '    </application>', 1)
m = m.replace('</activity>', share + '        </activity>', 1)
open(p, 'w', encoding='utf-8').write(m)
print('manifest merged')
PY

flutter pub get
echo "Android setup complete."
