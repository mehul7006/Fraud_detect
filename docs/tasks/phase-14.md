# Phase 14 – Fake message detection
## 14.1 [BE] Labelled scam-message corpus
Goal: 2000+ labelled messages (English, Hindi, Hinglish), 9+ scam categories, no real personal data.
Depends on: none
- [ ] 14.1.1 | Write the label scheme and category list (bank/KYC, lottery, courier, electricity, fake job, loan, digital arrest, OTP request, UPI refund, genuine) and the CSV columns | `ml/messages/README-schema.md` | 1h
- [ ] 14.1.2 | Check licences of any public SMS-spam datasets before use; record each source and licence, drop any with unclear terms | `ml/messages/DATA-LICENSES.md` | 2h
- [ ] 14.1.3 | Write about 600 scam messages by hand from the category templates (placeholders for names, amounts, links) | `ml/messages/scam_en.csv` | 4h
- [ ] 14.1.4 | Write about 400 Hindi (Devanagari) and 400 Hinglish scam messages | `ml/messages/scam_hi.csv`, `scam_hinglish.csv` | 4h
- [ ] 14.1.5 | Write about 700 genuine bank/OTP/delivery/telecom messages with fake numbers only | `ml/messages/genuine.csv` | 4h
- [ ] 14.1.6 | Write a script that checks counts, categories, duplicates and scans for phone numbers, emails and card patterns | `ml/messages/validate.py` | 2h
- [ ] 14.1.7 | Do the privacy review: tick the checklist, record reviewer and date | `ml/messages/PRIVACY-REVIEW.md` | 1h
- [ ] 14.1.v | Verify gate: >=2000 rows, >=9 scam categories, privacy review done. Run `docker run --rm -v "E:/frud detection/ml":/w -w /w python:3.12 python messages/validate.py` | Validator output "OK" with counts | 1h

## 14.2 [BE] Train small multilingual classifier
Goal: model file under 10 MB with F1 of at least 0.90 on held-out messages.
Depends on: 14.1
- [ ] 14.2.1 | Write data loader and stratified train/validation/test split with a fixed seed | `ml/train_msg.py` function `load_split` | 2h
- [ ] 14.2.2 | Train a TF-IDF (char n-grams for Hinglish) plus logistic regression baseline and print F1 | `ml/train_msg.py` | 3h
- [ ] 14.2.3 | Add a per-category confusion report and list the worst errors | `ml/reports/msg-v1.txt` | 2h
- [ ] 14.2.4 | Decide the on-device format: check which TFLite conversion works for a linear model (or a tiny dense net on TF-IDF features) and record the choice | Note in `ml/README.md` | 2h
- [ ] 14.2.5 | Export to TFLite and save vocabulary/feature file next to it | `models/msg-v1.tflite`, `models/msg-v1-vocab.json` | 4h
- [ ] 14.2.6 | Check TFLite output equals Python output on 50 messages | `ml/check_tflite.py` | 2h
- [ ] 14.2.v | Verify gate: held-out F1 >=0.90 and file <=10 MB. Run `ls -l models/msg-v1.tflite` and `python train_msg.py` in Docker | Printed F1 and file size | 1h

## 14.3 [FE] On-device inference and model update
Goal: classify in under 200 ms on a phone, and update the model without an app release.
Depends on: 14.2
- [ ] 14.3.1 | Add TensorFlow Lite dependency (verify current artifact name and version) | `mobile/android/app/build.gradle` | 1h
- [ ] 14.3.2 | Create `MessageClassifier.kt`: load model from app files dir, else from assets | `mobile/android-native/kotlin/MessageClassifier.kt` | 3h
- [ ] 14.3.3 | Implement the same text-to-features step in Kotlin as in Python (same vocab, same normalisation) | `MessageClassifier.kt` | 4h
- [ ] 14.3.4 | MethodChannel `classifyMessage(text)` returning category + score; Dart wrapper | `MainActivity.kt`, `native_bridge.dart` | 2h
- [ ] 14.3.5 | Test vectors: 20 messages whose Python scores are stored; Kotlin must match within 0.01 | `MessageClassifierTest.kt`, `ml/vectors.json` | 3h
- [ ] 14.3.6 | Backend endpoint to serve the model version and file, and the app updater with hash check | `GET /config/model/msg` in `ConfigController.java`, `model_updater.dart` | 4h
- [ ] 14.3.7 | Time 100 classifications on a mid-range device | Log with average ms | 1h
- [ ] 14.3.v | Verify gate: <200 ms per message; a newer model file served by the backend is used after restart without reinstall | Timing log plus screen recording | 2h

## 14.4 [FE] Input paths
Goal: check a message by share, paste box, or (opt-in) notification text.
Depends on: 14.3, 14.5
- [ ] 14.4.1 | Create paste-and-check screen | `mobile/lib/screens/check_message_screen.dart` | 3h
- [ ] 14.4.2 | Handle share-text intent (reuse the 4.8 handler in `MainActivity.kt`) and route to the screen | `MainActivity.kt`, `main.dart` | 3h
- [ ] 14.4.3 | Add notification message check in `AdNotificationListener.kt`; text stays in memory, goes straight to the classifier, never saved | `AdNotificationListener.kt` | 3h
- [ ] 14.4.4 | Add per-app toggle list for message checks in settings | `settings_screen.dart`, SharedPreferences | 3h
- [ ] 14.4.5 | Widget test for paste box and test for disabled app being ignored | `mobile/test/check_message_test.dart` | 2h
- [ ] 14.4.v | Verify gate: shared scam text shows verdict; notification from a disabled app is ignored | Screen recording and log | 1h

## 14.5 [FE] On-device rule layer
Goal: simple explainable rules that run before the ML model.
Depends on: none
- [ ] 14.5.1 | Write the rule list (urgency, account blocked, asks OTP/PIN/CVV, claim prize, sender-ID patterns) with a reason text each | `mobile/lib/core/message_rules.dart` | 3h
- [ ] 14.5.2 | Make rules work for English, Hindi and Hinglish keywords | `message_rules.dart` | 2h
- [ ] 14.5.3 | Return list of matched rules with plain explanation | `message_rules.dart` `MessageRuleResult` | 1h
- [ ] 14.5.4 | Write 40+ sample messages (scam and genuine) | `mobile/test/message_rules_test.dart` | 3h
- [ ] 14.5.5 | Run tests in Docker, tune rules for false positives | `flutter test` output | 2h
- [ ] 14.5.v | Verify gate: 40+ samples pass and each hit has a reason. Run `docker run --rm -v "E:/frud detection/mobile":/app -w /app ghcr.io/cirruslabs/flutter:stable flutter test test/message_rules_test.dart` | Green test output | 1h

## 14.6 [BE] Opt-in cloud check
Goal: `POST /scan/message` that analyses text in memory and stores and logs nothing.
Depends on: 11.6
- [ ] 14.6.1 | Define request/response: text, consentFlag, language; response category, score, explanation | `dto/Dtos.java` | 1h
- [ ] 14.6.2 | Create controller; refuse with 400 if consentFlag is not true; limit text size | `controller/MessageScanController.java` | 2h
- [ ] 14.6.3 | Service: call the LLM explainer from 11.6 (text only in memory, template fallback if key missing) | `service/MessageScanService.java` | 4h
- [ ] 14.6.4 | Make sure request logging and the error handler never print the body; add a log filter test | `RequestIdFilter.java`, `GlobalExceptionHandler.java` check | 2h
- [ ] 14.6.5 | Integration test: send a marker text, then grep captured logs and DB tables for it | `MessageScanIT.java` | 3h
- [ ] 14.6.6 | Add an LLM prompt-injection test (message says "ignore instructions") | `MessageScanServiceTest.java` | 2h
- [ ] 14.6.v | Verify gate: log and DB grep find no message text; no consent flag gives 400. Run `mvn verify` in Docker, then curl on port 18080 without the flag | Test output and 400 response | 1h

## 14.7 [FE] Consent flow for cloud check
Goal: cloud call happens only after a clear tap.
Depends on: 14.6
- [ ] 14.7.1 | Write consent text (what is sent, not stored, not logged) in English and Hindi | `l10n/` strings | 1h
- [ ] 14.7.2 | Create first-time explanation dialog and remember acceptance | `mobile/lib/screens/cloud_consent_dialog.dart` | 3h
- [ ] 14.7.3 | Per-message confirm with a visible "This will be sent to our server" label | `cloud_consent_dialog.dart` | 2h
- [ ] 14.7.4 | Add API method in `api_client.dart` that can only be called with a confirmed token object from the dialog | `api_client.dart` `scanMessageCloud` | 2h
- [ ] 14.7.5 | Widget test: cancel makes zero HTTP calls (fake client) | `mobile/test/cloud_consent_test.dart` | 2h
- [ ] 14.7.v | Verify gate: cloud call never fires without the tap | Test output plus proxy log showing zero calls on cancel | 1h

## 14.8 [FE] Verdict screen
Goal: show message verdict, what to do, and merged link verdicts.
Depends on: 14.3, 14.5, 10.9
- [ ] 14.8.1 | Create screen with category title, reasons and "what to do / never click" box | `mobile/lib/screens/message_verdict_screen.dart` | 3h
- [ ] 14.8.2 | Extract links from the message with the same regex as 4.6 | `protection_engine.dart` helper `extractUrls` | 2h
- [ ] 14.8.3 | Send each link through `_handleLink` pipeline and collect results | `protection_engine.dart` | 3h
- [ ] 14.8.4 | Merge message score and link verdicts into one overall verdict (worst wins) | `message_verdict_screen.dart` | 2h
- [ ] 14.8.5 | Widget test with a fake-Flipkart link inside a scam message | `mobile/test/message_verdict_test.dart` | 2h
- [ ] 14.8.v | Verify gate: message with a fake-Flipkart link shows both message and link verdicts | Screenshot | 1h

## 14.9 [FE] Hindi and Hinglish support
Goal: UI and detection work for Hindi and Hinglish.
Depends on: 14.3, 14.5
- [ ] 14.9.1 | Set up Flutter localisation files (check if 8.11 already did) | `mobile/lib/l10n/app_en.arb`, `app_hi.arb` | 2h
- [ ] 14.9.2 | Translate all Phase 14 screen strings (get a native speaker to review) | `app_hi.arb` | 3h
- [ ] 14.9.3 | Collect 30 new Hindi/Hinglish scam samples not used in training | `mobile/test/resources/hi_samples.json` | 3h
- [ ] 14.9.4 | Test that rules plus model catch at least the target share of them | `mobile/test/hindi_detection_test.dart` | 3h
- [ ] 14.9.v | Verify gate: 30 Hindi/Hinglish scam samples detected; language switch changes the screens | Test output and screenshot | 1h

## 14.10 [FE] False-positive guard
Goal: genuine messages stay SAFE (under 2% false positives).
Depends on: 14.3, 14.5
- [ ] 14.10.1 | Collect 500 genuine bank/OTP/delivery messages with fake numbers (no real data); keep them separate from training data | `mobile/test/resources/genuine_500.csv` | 4h
- [ ] 14.10.2 | Write test that runs rules plus classifier over all rows | `mobile/test/message_fp_test.dart` | 3h
- [ ] 14.10.3 | Print the false positives with the rule/score that caused them | test report | 1h
- [ ] 14.10.4 | Tune rule weights or threshold, re-run, note the changes | `message_rules.dart` | 3h
- [ ] 14.10.v | Verify gate: false-positive rate <2%. Run `flutter test test/message_fp_test.dart` in the Flutter Docker image | Printed rate and green test | 1h
