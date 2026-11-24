"""18.9: build-time flavour flag for the Android app (idempotent; run by tool/setup_android.sh).

One code base, two builds, chosen with a dart-define (no Gradle productFlavors, so the plain
`flutter build apk` / `flutter run` commands keep working unchanged):

    flutter build apk --release --dart-define=FS_FLAVOUR=play   # Google Play: trackers + malware (default)
    flutter build apk --release --dart-define=FS_FLAVOUR=web    # website download: + ads

Flutter passes all dart-defines to Gradle as the `dart-defines` property (comma-separated Base64). This patch reads
FS_FLAVOUR from it into BuildConfig.FS_FLAVOUR for the Kotlin side; Dart reads the same define (lib/core/flavour.dart).
Anything other than "web" means "play" - the safe default for a Play upload.
"""
import os

p = os.path.join(os.path.dirname(__file__), "..", "android", "app", "build.gradle.kts")
s = open(p, encoding="utf-8").read()
MARK = "// 18.9 flavour"
if MARK in s:
    print("flavour flag already in build.gradle.kts")
    raise SystemExit
top = '''
%s: FS_FLAVOUR from the dart-defines ("play" unless exactly "web")
val fsFlavour: String = ((project.findProperty("dart-defines") as String?) ?: "")
    .split(",").filter { it.isNotBlank() }
    .map { try { String(Base64.getDecoder().decode(it)) } catch (e: IllegalArgumentException) { "" } }
    .firstOrNull { it.startsWith("FS_FLAVOUR=") }?.substringAfter("=")
    .let { if (it == "web") "web" else "play" }
''' % MARK
if "import java.util.Base64" not in s:  # in .kts, "java." means the project's java extension - import instead
    s = "import java.util.Base64\n" + s
anchor = "android {\n"
assert anchor in s, "android { block not found"
s = s.replace(anchor, top + "\n" + anchor, 1)
anchor2 = "    defaultConfig {\n"
assert anchor2 in s, "defaultConfig not found"
s = s.replace(anchor2, '''    buildFeatures { buildConfig = true } %s
    defaultConfig {
        buildConfigField("String", "FS_FLAVOUR", "\\"$fsFlavour\\"")
''' % MARK, 1)
open(p, "w", encoding="utf-8", newline="\n").write(s)
print("flavour flag added to build.gradle.kts")
