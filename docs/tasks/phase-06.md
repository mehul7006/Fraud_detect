# Phase 6 – Ad-abuse detection
## 6.1 [BE] Tune scoring weights from beta data; config via `application.yml`
Goal: Move AdAbuseService weights to configuration and reach precision of 90% or more on 30 labelled apps.
Depends on: 6.2
- [ ] 6.1.1 | Create AdAbuseProperties (@ConfigurationProperties) holding every weight and threshold now hard-coded in AdAbuseService | backend/src/main/java/com/fraudshield/config/AdAbuseProperties.java | 2h
- [ ] 6.1.2 | Add the default values to application.yml under fraudshield.adabuse and inject them into AdAbuseService | application.yml, AdAbuseService.java | 2h
- [ ] 6.1.3 | Build a labelled set of 30 apps (benign and abusive) with the same fields as AppInfo | backend/src/test/resources/adabuse-labelled.csv | 3h
- [ ] 6.1.4 | Write AdAbuseAccuracyTest that loads the CSV, scores each app and prints precision, recall and every false positive | backend/src/test/java/com/fraudshield/service/AdAbuseAccuracyTest.java | 3h
- [ ] 6.1.5 | Adjust weights in application.yml until precision is 90% or more; keep a before/after note | application.yml plus short comment in the test | 2h
- [ ] 6.1.6 | Keep the old test that the spam sample scores 70 or more passing | ScanServicesTest.java | 1h
- [ ] 6.1.v | Verify gate: precision 90% or more on 30 labelled apps | mvn test output in Docker showing the metrics line | 1h

## 6.2 [BE] App reputation table (package → known good/bad), seeded with top 500 apps
Goal: Known-good apps are never flagged and known-bad packages are always flagged.
Depends on: none
- [ ] 6.2.1 | Design table app_reputation (package_name unique, label, verdict GOOD/BAD, source, updated_at) | backend/src/main/resources/db/migration/V2__app_reputation.sql (check the highest existing version number first) | 1h
- [ ] 6.2.2 | Add entity AppReputation and AppReputationRepository | backend/src/main/java/com/fraudshield/domain/AppReputation.java, repo/Repositories.java | 2h
- [ ] 6.2.3 | Collect the seed list of the top 500 popular apps (package names from a free, public source; verify its licence) and write a seed file | backend/src/main/resources/seed/known-good-apps.csv | 4h
- [ ] 6.2.4 | Seed the table from the CSV in the migration or a startup loader | V2__app_reputation.sql or AppReputationSeeder.java | 2h
- [ ] 6.2.5 | In AdAbuseService, return SAFE for GOOD packages and MALICIOUS for BAD ones before scoring | AdAbuseService.java | 2h
- [ ] 6.2.6 | Tests: WhatsApp package with many notifications stays SAFE, a BAD package is MALICIOUS, unknown package is scored normally | ScanServicesTest.java, ApiFlowIT.java | 2h
- [ ] 6.2.v | Verify gate: known-good apps never flagged | test output plus POST /scan/apps curl with a known-good package that has 30 notifications/hour returns SAFE | 1h

## 6.3 [BE] Per-device app history (detect new risky app installed)
Goal: The apps response tells which risky apps are new since the last scan.
Depends on: 6.2
- [ ] 6.3.1 | Design table device_apps (device_id, package_name, first_seen, last_seen, last_score) | backend/src/main/resources/db/migration/V3__device_apps.sql | 2h
- [ ] 6.3.2 | Add entity DeviceApp and repository methods | domain/DeviceApp.java, repo/Repositories.java | 2h
- [ ] 6.3.3 | Add optional deviceId to AppsScanRequest and add field newSinceLastScan to AppResult (keep old clients working) | Dtos.java | 1h
- [ ] 6.3.4 | Update ScanController /scan/apps to upsert device_apps and mark packages not seen before; first scan of a device marks nothing as new | ScanController.java, new service DeviceAppService.java | 3h
- [ ] 6.3.5 | Record a threat only for a new app that is MALICIOUS (no repeat each hour) | ScanController.java | 1h
- [ ] 6.3.6 | Integration test: first scan no new flags, second scan with one extra risky app flags it, third scan does not | ApiFlowIT.java | 2h
- [ ] 6.3.v | Verify gate: "New app since last scan" appears in the response | curl output of two consecutive POST /scan/apps on port 18080 | 1h

## 6.4 [FE] Notification counting per package per hour
Goal: Confirm a spam test app reaches 20 or more notifications/hour in the audit.
Depends on: none
- [ ] 6.4.1 | Review AdNotificationListener counting: the prefs string is rewritten on every notification; check cost and the race with takePending | mobile/android-native/kotlin/AdNotificationListener.kt | 1h
- [ ] 6.4.2 | Extract the counting into a small class (NotifCounter) taking time as a parameter | mobile/android-native/kotlin/NotifCounter.kt | 2h
- [ ] 6.4.3 | Write unit tests: 25 events in 1 hour give 25, events older than 1 hour drop out, other packages are independent | mobile/android-native/test/NotifCounterTest.kt | 2h
- [ ] 6.4.4 | Cap stored timestamps per package (for example 100) and clean keys of uninstalled packages | NotifCounter.kt | 2h
- [ ] 6.4.5 | Build or find a tiny test app that posts notifications on a timer (adb shell cmd notification post is an alternative) and run it | test notes under docs/ | 2h
- [ ] 6.4.v | Verify gate: spam test app reaches 20 or more per hour in the audit output | AppAuditor result for that package printed in logcat | 1h

## 6.5 [FE] Overlay / full-screen-intent / accessibility / listener permission audit
Goal: AppAuditor flags apps with overlay, full-screen, accessibility and listener abilities correctly.
Depends on: none
- [ ] 6.5.1 | Review AppAuditor: SYSTEM_ALERT_WINDOW in requestedPermissions means requested, not granted; add a granted check (AppOpsManager) | mobile/android-native/kotlin/AppAuditor.kt | 3h
- [ ] 6.5.2 | Check accessibility by enabled services list (Settings.Secure enabled_accessibility_services), not only declared services | AppAuditor.kt | 2h
- [ ] 6.5.3 | Check notification listener by enabled_notification_listeners, not only declared | AppAuditor.kt | 2h
- [ ] 6.5.4 | Check that QUERY_ALL_PACKAGES is in the manifest and test getInstalledPackages on Android 11+ | AndroidManifest.snippet.xml | 1h
- [ ] 6.5.5 | Install a test app that declares SYSTEM_ALERT_WINDOW and an accessibility service; compare audit output with adb shell dumpsys package | test notes with both outputs | 2h
- [ ] 6.5.v | Verify gate: audit flags the test app with SYSTEM_ALERT_WINDOW | logcat or Dart log of the audit result | 1h

## 6.6 [FE] UsageStats: apps launched without user action / background starts (needs Usage Access)
Goal: Detect an app that opens itself, using Usage Access, without storing anything but counters.
Depends on: none
- [ ] 6.6.1 | Verify which UsageEvents types show a background-started activity on Android 10-14 and write the chosen rule | comment block at top of UsageAuditor.kt | 2h
- [ ] 6.6.2 | Create UsageAuditor.kt with hasUsageAccess() and openUsageAccessSettings() | mobile/android-native/kotlin/UsageAuditor.kt | 2h
- [ ] 6.6.3 | Add the PACKAGE_USAGE_STATS permission and the bridge methods hasUsageAccess, openUsageAccess, usageAudit | AndroidManifest.snippet.xml, MainActivity.kt, mobile/lib/core/native_bridge.dart | 2h
- [ ] 6.6.4 | Implement usageAudit(): count per package the activity starts in the last 24 h that are not preceded by user interaction (rule from 6.6.1) | UsageAuditor.kt | 4h
- [ ] 6.6.5 | Add field selfLaunchCount to AppInfo on the backend and a weight in AdAbuseService | Dtos.java, AdAbuseService.java, application.yml | 2h
- [ ] 6.6.6 | Add the permission card (plain wording, opt-in) to the dashboard | mobile/lib/screens/dashboard_screen.dart | 2h
- [ ] 6.6.v | Verify gate: a test app that opens itself in the background is detected | audit output showing selfLaunchCount above 0 for the test app | 1h

## 6.7 [FE] Uninstall flow + "Ignore this app"
Goal: Uninstall opens the system dialog and Ignore hides an app until its risk changes.
Depends on: 6.8
- [ ] 6.7.1 | Verify ACTION_DELETE works on Android 8-14; add REQUEST_DELETE_PACKAGES to the manifest if needed | AndroidManifest.snippet.xml, MainActivity.kt uninstallApp | 2h
- [ ] 6.7.2 | Re-check the app list after returning from the uninstall dialog and remove the alert if the app is gone | mobile/lib/core/protection_engine.dart | 2h
- [ ] 6.7.3 | Add an ignored-packages store (local only, package names) | mobile/lib/core/ignored_apps.dart | 2h
- [ ] 6.7.4 | Skip ignored packages in checkApps and add an Ignore action to the risky-app alert | protection_engine.dart | 2h
- [ ] 6.7.5 | Unit test: ignored package is skipped, un-ignore works | mobile/test/ignored_apps_test.dart | 1h
- [ ] 6.7.6 | Manual test on a device: uninstall a test app, cancel the dialog, ignore another app | test notes | 1h
- [ ] 6.7.v | Verify gate: system uninstall dialog appears | screen recording | 1h

## 6.8 [FE] Risky apps screen (list with reasons)
Goal: One screen lists all flagged apps with their reasons.
Depends on: none
- [ ] 6.8.1 | Keep the last checkApps result in the engine (list of package, label, score, verdict, reasons) and expose it as a ValueNotifier | mobile/lib/core/protection_engine.dart | 2h
- [ ] 6.8.2 | Add native_bridge/API field mapping so reasons are carried through | mobile/lib/core/api_client.dart | 1h
- [ ] 6.8.3 | Create risky_apps_screen.dart with a list sorted by score, reason chips, pull-to-refresh | mobile/lib/screens/risky_apps_screen.dart | 3h
- [ ] 6.8.4 | Add empty, loading and error states | risky_apps_screen.dart | 2h
- [ ] 6.8.5 | Add Uninstall and Ignore buttons per row (uses 6.7) and link the screen from the Apps tile on the dashboard | risky_apps_screen.dart, dashboard_screen.dart | 2h
- [ ] 6.8.6 | Widget test with fake data: list order, reasons shown, empty state | mobile/test/risky_apps_screen_test.dart | 2h
- [ ] 6.8.v | Verify gate: all flagged apps visible with reasons | screenshot plus flutter analyze and flutter test output in Docker | 1h
