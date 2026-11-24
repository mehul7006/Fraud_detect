/// 18.9: which build this is, fixed at build time with `--dart-define=FS_FLAVOUR=play|web`
/// (Android reads the same define into BuildConfig.FS_FLAVOUR - tool/flavour_gradle.py).
///  * play (default): the Google Play build - blocks trackers + malware; NO ad blocking anywhere in the app,
///    because Play policy forbids blocking or interfering with other apps' ads (docs/ad-blocking-policy.md).
///  * web: the build offered on our website - also blocks ads.
const String kFlavour = String.fromEnvironment('FS_FLAVOUR', defaultValue: 'play') == 'web' ? 'web' : 'play';

/// Whether this build may offer ad blocking at all (the native side checks again on its own).
const bool kAdBlockingInBuild = kFlavour == 'web';
