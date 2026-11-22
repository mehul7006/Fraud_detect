"""7.3 + 9.6: Firebase Gradle plugins (google-services, Crashlytics) for the generated android/ project.
Idempotent; called by setup_android.sh. The plugins are only applied when android-native/google-services.json exists
(it is project-specific and git-ignored), so a checkout without it still builds - just without push / crash reports."""
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
cfg = ROOT / 'android-native' / 'google-services.json'
if not cfg.exists():
    print('no android-native/google-services.json: Firebase (push, crash reports) not configured')
    raise SystemExit
shutil.copy(cfg, ROOT / 'android' / 'app' / 'google-services.json')

S = ROOT / 'android' / 'settings.gradle.kts'
s = S.read_text(encoding='utf-8')
if 'com.google.gms.google-services' not in s:
    s = s.replace('    id("org.jetbrains.kotlin.android")',
                  '    id("com.google.gms.google-services") version "4.5.0" apply false\n'
                  '    id("com.google.firebase.crashlytics") version "3.0.7" apply false\n'
                  '    id("org.jetbrains.kotlin.android")', 1)
    S.write_text(s, encoding='utf-8')

A = ROOT / 'android' / 'app' / 'build.gradle.kts'
a = A.read_text(encoding='utf-8')
if 'com.google.gms.google-services' not in a:
    a = a.replace('    id("dev.flutter.flutter-gradle-plugin")',
                  '    id("dev.flutter.flutter-gradle-plugin")\n'
                  '    // 7.3 push + 9.6 crash reports\n'
                  '    id("com.google.gms.google-services")\n'
                  '    id("com.google.firebase.crashlytics")', 1)
    A.write_text(a, encoding='utf-8')
print('Firebase Gradle plugins configured')
