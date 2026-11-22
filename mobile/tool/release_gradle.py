"""8.9 + 9.5: release build settings for android/app/build.gradle.kts (idempotent, called by setup_android.sh).
- release signing from android/key.properties (git-ignored); bundleRelease/assembleRelease FAIL without it
  (never ship a debug-signed app)
- R8 minify + resource shrinking + our ProGuard rules (android-native/proguard-rules.pro)
Version name / code come from pubspec.yaml `version: x.y.z+N` (Flutter passes them in)."""
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
G = ROOT / 'android' / 'app' / 'build.gradle.kts'
MARK = '// 9.5 release signing'
s = G.read_text(encoding='utf-8')
shutil.copy(ROOT / 'android-native' / 'proguard-rules.pro', ROOT / 'android' / 'app' / 'proguard-rules.pro')
if MARK in s:
    print('release config already present')
    raise SystemExit

s = 'import java.util.Properties\n\n' + s
s = s.replace('android {', MARK + """: values live in android/key.properties (never committed), see docs/RELEASE.md
val releaseKeys = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {""", 1)
old = """    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }"""
new = """    signingConfigs {
        create("release") {
            if (!releaseKeys.isEmpty) {
                storeFile = file(releaseKeys.getProperty("storeFile"))
                storePassword = releaseKeys.getProperty("storePassword")
                keyAlias = releaseKeys.getProperty("keyAlias")
                keyPassword = releaseKeys.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 9.5: the upload key when present; local `flutter run --release` without it falls back to debug keys,
            // but building an AAB/APK for distribution without it is stopped below.
            signingConfig = if (releaseKeys.isEmpty) signingConfigs.getByName("debug") else signingConfigs.getByName("release")
            // 8.9: R8 shrink + obfuscate, drop unused resources
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }"""
assert old in s, 'unexpected template - check build.gradle.kts by hand'
s = s.replace(old, new, 1)
s += """
// 9.5: never produce a distributable release that is signed with the debug key
gradle.taskGraph.whenReady {
    val distributing = allTasks.any { it.name == "bundleRelease" || it.name == "assembleRelease" }
    if (distributing && releaseKeys.isEmpty) {
        throw GradleException("Release build needs android/key.properties with the upload key (see docs/RELEASE.md, roadmap 9.5)")
    }
}
"""
G.write_text(s, encoding='utf-8')
print('release config added')
