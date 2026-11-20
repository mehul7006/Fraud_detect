# Phase 7 – Clone & account takeover
## 7.1 [BE] Multi-device clone rules (same account, 2 active devices, same fingerprint/IMEI-hash)
Goal: Prove the duplicate-fingerprint rule with a real two-device test (the rule itself is already done).
Depends on: none
- [ ] 7.1.1 | Read the rule in CloneRiskService.register and write down the exact expected scores for two devices with the same fingerprint (rule adds +50) | test plan as comment in ApiFlowIT.java | 1h
- [ ] 7.1.2 | Add integration test: one user registers device A and device B with the same fingerprint; B gets +50 and the reason text | backend/src/test/java/com/fraudshield/ApiFlowIT.java test duplicateFingerprintAddsFifty | 2h
- [ ] 7.1.3 | Add test: two devices with different fingerprints add nothing | ApiFlowIT.java test differentFingerprintNoExtraScore | 1h
- [ ] 7.1.4 | Add test: other device last seen more than 1 day ago is ignored | ScanServicesTest.java or ApiFlowIT.java | 2h
- [ ] 7.1.5 | Add test: repeated heartbeats from both devices still create only one open threat | ApiFlowIT.java | 2h
- [ ] 7.1.v | Verify gate: duplicate fingerprint adds +50 across two registered devices | mvn verify output in Docker showing the new tests green | 1h

## 7.2 [BE] Takeover event correlation (OTP + new device within 10 min → CRITICAL)
Goal: Two related takeover signals within 10 minutes raise one CRITICAL threat.
Depends on: none
- [ ] 7.2.1 | Define the correlation rule in plain words (which threat types and signals count, window of 10 minutes, per user) and add it to the API spec | docs/02-ARCHITECTURE.md or docs/04-API-SPEC.md short section | 1h
- [ ] 7.2.2 | Add repository query: threats of given types for a user created after a timestamp | backend/src/main/java/com/fraudshield/repo/Repositories.java | 1h
- [ ] 7.2.3 | Create TakeoverService with evaluate(userId, newEvent) implementing the rule | backend/src/main/java/com/fraudshield/service/TakeoverService.java | 3h
- [ ] 7.2.4 | Call TakeoverService from ThreatController report endpoint after saving a takeover event | ThreatController.java | 1h
- [ ] 7.2.5 | Make sure a CRITICAL correlation threat is created only once per window (no duplicates) | TakeoverService.java | 2h
- [ ] 7.2.6 | Unit tests: signals 5 min apart give CRITICAL, 15 min apart do not, a single signal does not, other user not affected | backend/src/test/java/com/fraudshield/service/TakeoverServiceTest.java | 3h
- [ ] 7.2.7 | Integration test through POST /threats with two reports | ApiFlowIT.java test twoTakeoverSignalsRaiseCritical | 2h
- [ ] 7.2.v | Verify gate: two events raise severity | mvn verify green plus curl of two POST /threats then GET /threats on port 18080 | 1h

## 7.3 [BE] Push alerts (FCM) when threat created from another source
Goal: A user's other devices receive a push when a threat is created.
Depends on: 7.2
- [ ] 7.3.1 | Verify current Firebase Cloud Messaging HTTP v1 requirements and free usage terms; decide the Java client (firebase-admin or REST) | decision note as comment in the future PushService.java | 1h
- [ ] 7.3.2 | Add push_token column to devices and a migration | backend/src/main/resources/db/migration/V4__push_token.sql (use the next free version number) | 1h
- [ ] 7.3.3 | Add field pushToken to DeviceRequest and store it in CloneRiskService.register | Dtos.java, CloneRiskService.java, Device.java | 1h
- [ ] 7.3.4 | Create interface PushSender and the FCM implementation reading credentials from env (FCM_CREDENTIALS_FILE); off by default | backend/src/main/java/com/fraudshield/service/PushSender.java, FcmPushSender.java | 3h
- [ ] 7.3.5 | Add PushService that sends to all devices of the user except the source device, on HIGH or CRITICAL threats only; body has no personal data | backend/src/main/java/com/fraudshield/service/PushService.java | 2h
- [ ] 7.3.6 | Hook PushService into ThreatService.record (async, failure never breaks the request) | ThreatService.java | 2h
- [ ] 7.3.7 | Unit test with a fake PushSender: sent to other devices only, not sent for LOW, failure ignored | PushServiceTest.java | 2h
- [ ] 7.3.v | Verify gate: test push received on a real phone | screenshot of the notification plus backend log line without personal data | 2h

## 7.4 [BE] Safe-contact/emergency contact SMS alert (opt-in)
Goal: With the user's consent, a chosen contact is alerted when a CRITICAL threat happens.
Depends on: 7.2
- [ ] 7.4.1 | Decide a free delivery channel (verify options: user's own phone sends SMS, or push to the contact's FraudShield app, or email) and record the decision; a paid SMS gateway is not allowed | decision comment in NotifyService.java | 2h
- [ ] 7.4.2 | Design table emergency_contacts (user_id, contact_name, contact_channel_value stored encrypted or hashed where possible, consent_at, active) | backend/src/main/resources/db/migration/V5__emergency_contacts.sql | 2h
- [ ] 7.4.3 | Add entity, repository, DTOs and endpoints POST/GET/DELETE /api/v1/me/emergency-contact (consent flag required, 400 without it) | backend/src/main/java/com/fraudshield/controller/ContactController.java, domain/, dto/ | 3h
- [ ] 7.4.4 | Create NotifyService.alertContact(userId, threat) that sends only "possible scam on X's phone, please call them" with no threat details | backend/src/main/java/com/fraudshield/service/NotifyService.java | 3h
- [ ] 7.4.5 | Trigger it for CRITICAL threats once per threat and ensure DELETE /me removes contacts | ThreatService.java | 2h
- [ ] 7.4.6 | Tests: no consent gives 400, CRITICAL triggers one alert, HIGH does not, delete account removes the row | backend/src/test/java/com/fraudshield/service/NotifyServiceTest.java, ApiFlowIT.java | 3h
- [ ] 7.4.v | Verify gate: contact receives the alert | screenshot of received message on the contact's device | 1h

## 7.5 [FE] Device fingerprint, SIM hash, root, emulator, Play Protect, RAT apps
Goal: DeviceInfo.kt output matches the backend DeviceRequest and works on Android 8-14.
Depends on: none
- [ ] 7.5.1 | Compare the map built in DeviceInfo.collect with DeviceRequest field by field (names and types) and list gaps | note as comment in mobile/android-native/kotlin/DeviceInfo.kt | 1h
- [ ] 7.5.2 | Review SIM hash: hash only, no raw IMSI or number, and handle missing READ_PHONE_STATE and no SIM | DeviceInfo.kt | 2h
- [ ] 7.5.3 | Review root, emulator and Play Protect checks for false positives on real phones; fix the weakest one | DeviceInfo.kt | 3h
- [ ] 7.5.4 | Confirm the RAT package list in DeviceInfo matches CloneRiskService.REMOTE_APPS and package visibility is declared (queries or QUERY_ALL_PACKAGES) | DeviceInfo.kt, AndroidManifest.snippet.xml | 1h
- [ ] 7.5.5 | Run on a real phone, post the JSON to POST /devices on port 18080 and compare with the backend response | captured JSON plus curl output | 2h
- [ ] 7.5.6 | Run on an emulator and check emulator=true | captured JSON | 1h
- [ ] 7.5.v | Verify gate: app output matches the backend DTO and backend accepts it | 200 response with cloneRiskScore from the real device | 1h

## 7.6 [FE] Hourly heartbeat
Goal: The backend last_seen updates every hour from the app.
Depends on: 7.11
- [ ] 7.6.1 | Review checkDevice and runSlow in protection_engine.dart: the timer only runs while the app is open; list what must move to the background service | note as comment in mobile/lib/core/protection_engine.dart | 1h
- [ ] 7.6.2 | Handle errors: when offline keep the old tile state instead of showing ERROR, and retry on next tick | protection_engine.dart | 2h
- [ ] 7.6.3 | Add a unit test for the heartbeat call with a fake ApiClient (success and failure) | mobile/test/heartbeat_test.dart | 2h
- [ ] 7.6.4 | Add a way to read last_seen (GET /devices or database) for the check | backend GET endpoint or psql query in docs notes | 1h
- [ ] 7.6.5 | Run the app for 3 hours (or shorten the interval to 5 minutes in a debug build) and look at last_seen | psql output with 3 timestamps | 2h
- [ ] 7.6.v | Verify gate: backend last_seen updates | timestamps one interval apart | 1h

## 7.7 [FE] Unexpected WhatsApp/Telegram login-code detector (never stores the code)
Goal: A "Telegram code" notification raises an alert and the code is never stored.
Depends on: none
- [ ] 7.7.1 | Review the matching in AdNotificationListener (mentionsApp and "code"/"otp"): list false positives such as chat messages with the word code | mobile/android-native/kotlin/AdNotificationListener.kt | 1h
- [ ] 7.7.2 | Extract the text rules into a pure class NotificationRules (input: package and text, output: event kinds) | mobile/android-native/kotlin/NotificationRules.kt | 2h
- [ ] 7.7.3 | Write unit tests with at least 12 sample texts (real-style WhatsApp and Telegram login codes, normal chats, other apps) | mobile/android-native/test/NotificationRulesTest.kt | 3h
- [ ] 7.7.4 | Check that no event or log holds the code digits (test that the stored JSON has no digits from the text) | NotificationRulesTest.kt | 1h
- [ ] 7.7.5 | Post a test notification with adb (cmd notification post) and confirm the alert in the app | logcat plus screenshot | 1h
- [ ] 7.7.v | Verify gate: test notification "Telegram code" raises an alert | screenshot of the alert | 1h

## 7.8 [FE] New-device/linked-device notice detector
Goal: A new-device or linked-device notice raises a CRITICAL alert.
Depends on: 7.7
- [ ] 7.8.1 | Collect real wording of WhatsApp and Telegram new-login and linked-device notices (check current app text, English and Hindi) | list in the test file as comments | 2h
- [ ] 7.8.2 | Update NotificationRules for NEW_DEVICE with those phrases | NotificationRules.kt | 2h
- [ ] 7.8.3 | Add unit tests for the phrases and for normal messages that must not match | NotificationRulesTest.kt | 2h
- [ ] 7.8.4 | Check _handleNewDevice in the engine reports CRITICAL once, not on every notification | mobile/lib/core/protection_engine.dart | 2h
- [ ] 7.8.5 | Post a test notice with adb and check alert plus backend threat | logcat plus GET /threats output | 1h
- [ ] 7.8.v | Verify gate: test notice raises CRITICAL | screenshot of the alert and the CRITICAL row in /threats | 1h

## 7.9 [FE] WhatsApp & Telegram guided wizards (open app + steps)
Goal: Wizards open the app and show clear steps that match the current apps.
Depends on: none
- [ ] 7.9.1 | Verify each step in takeover_wizard_screen.dart against the current WhatsApp and Telegram menus and fix wording | mobile/lib/screens/takeover_wizard_screen.dart | 2h
- [ ] 7.9.2 | Handle the app not installed (openApp returns false) with a message | takeover_wizard_screen.dart | 1h
- [ ] 7.9.3 | Add the Android 11+ package visibility entry for com.whatsapp, com.whatsapp.w4b and org.telegram.messenger | AndroidManifest.snippet.xml | 1h
- [ ] 7.9.4 | Add a widget test: steps shown for both apps, not-installed message shown | mobile/test/takeover_wizard_test.dart | 2h
- [ ] 7.9.5 | Ask one non-technical person to follow the steps and note where they got stuck | notes with edits made | 2h
- [ ] 7.9.v | Verify gate: wizard opens the app and the steps are readable | screen recording | 1h

## 7.10 [FE] Persistent critical popup (full-screen intent) + Android 14 permission check
Goal: A CRITICAL alert shows on the lock screen, including on Android 14.
Depends on: none
- [ ] 7.10.1 | Verify the Android 14 rule for USE_FULL_SCREEN_INTENT (it can be revoked; check with NotificationManager.canUseFullScreenIntent) | note as comment in mobile/android-native/kotlin/AlertNotifier.kt | 1h
- [ ] 7.10.2 | Add the bridge methods canUseFullScreenIntent and openFullScreenIntentSettings (ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) | MainActivity.kt, mobile/lib/core/native_bridge.dart | 2h
- [ ] 7.10.3 | Add a permission card on the dashboard that shows when Android 14 denies it | mobile/lib/screens/dashboard_screen.dart | 2h
- [ ] 7.10.4 | Make the activity show over the lock screen (setShowWhenLocked and setTurnScreenOn) when opened from the alert | MainActivity.kt | 2h
- [ ] 7.10.5 | Handle Android 13 POST_NOTIFICATIONS runtime request before the first alert | MainActivity.kt, dashboard_screen.dart | 2h
- [ ] 7.10.6 | Test on Android 10, 13 and 14: locked screen, unlocked screen, permission denied | results table under docs/ | 3h
- [ ] 7.10.v | Verify gate: critical alert shows on the lock screen | screen recording of a locked phone | 1h

## 7.11 [FE] **Foreground service / WorkManager** so checks run when app closed
Goal: Links, files and events are checked while the app is swiped away.
Depends on: none
- [ ] 7.11.1 | Decide the design in writing: what runs in the service (events, files, heartbeat), what stays in Dart, and how the service calls the backend (Kotlin HTTP, token read from where) | design note as comment block in mobile/android-native/kotlin/ProtectionService.kt | 2h
- [ ] 7.11.2 | Decide token storage: move the JWT to a place Kotlin can read (EncryptedSharedPreferences or pass it through MethodChannel once on login) | change in mobile/lib/core/api_client.dart plus mobile/android-native/kotlin/TokenStore.kt | 3h
- [ ] 7.11.3 | Write a small Kotlin ApiClient (HttpURLConnection) with scanUrl, scanFile and report using the saved token and base URL | mobile/android-native/kotlin/NativeApi.kt | 3h
- [ ] 7.11.4 | Add manifest entries: FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE (or the type chosen after checking current Android 14 rules), RECEIVE_BOOT_COMPLETED, service declaration | mobile/android-native/AndroidManifest.snippet.xml | 1h
- [ ] 7.11.5 | Create ProtectionService.kt as a foreground service with a low-importance notification channel "Protection active" and START_STICKY | mobile/android-native/kotlin/ProtectionService.kt | 3h
- [ ] 7.11.6 | Add start and stop bridge methods startProtection and stopProtection and call start from the dashboard after login | MainActivity.kt, native_bridge.dart, dashboard_screen.dart | 2h
- [ ] 7.11.7 | Inside the service, add a 20 s loop that takes pending events (AdNotificationListener.takePending) and scans LINK events through NativeApi; alerts go through AlertNotifier | ProtectionService.kt | 4h
- [ ] 7.11.8 | Add file scanning to the loop (FileScanner.scanNew then POST /scan/file) when all-files access is granted | ProtectionService.kt | 2h
- [ ] 7.11.9 | Avoid double handling: when the app is open, only one of Dart or the service takes events (use a flag or let the service own events and push results to Dart) | ProtectionService.kt, protection_engine.dart | 3h
- [ ] 7.11.10 | Add hourly heartbeat to the service using WorkManager PeriodicWorkRequest (15 min minimum rule, 1 h chosen) so it also runs if the service is killed | mobile/android-native/kotlin/HeartbeatWorker.kt | 3h
- [ ] 7.11.11 | Add BootReceiver that restarts the service after reboot; handle Android 12+ limits on starting foreground services from the background | mobile/android-native/kotlin/BootReceiver.kt | 2h
- [ ] 7.11.12 | Add the Gradle dependency for WorkManager and check the build with the Flutter Docker image | mobile/android/app/build.gradle, flutter build apk --debug output | 2h
- [ ] 7.11.13 | Write unit tests for the pure parts (event to action mapping, token store) | mobile/android-native/test/ProtectionServiceLogicTest.kt | 2h
- [ ] 7.11.14 | Test with app swiped away: send a WhatsApp or notification containing a phishing test URL; time to alert | logcat plus screen recording | 2h
- [ ] 7.11.15 | Test on Xiaomi, Samsung and Realme: note battery-saver kills and write the "allow background" guidance screen text | results table under docs/ | 3h
- [ ] 7.11.v | Verify gate: a link is scanned while the app is swiped away | recording showing swipe-away, test notification with URL, alert, and backend log line for POST /scan/url | 1h
