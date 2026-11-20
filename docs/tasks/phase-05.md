# Phase 5 – File protection
## 5.1 [BE] VirusTotal hash lookup (hash only, never file)
Goal: Ask VirusTotal about a file hash and add its result to the file score without sending the file.
Depends on: none
- [ ] 5.1.1 | Verify current VirusTotal API terms, free quota and the v3 file-hash endpoint, then record the facts | comment block at top of backend/src/main/java/com/fraudshield/service/FileScanService.java | 1h
- [ ] 5.1.2 | Add interface FileReputationClient with lookup(sha256) returning an optional result (malicious count, total engines) | backend/src/main/java/com/fraudshield/service/FileReputationClient.java | 1h
- [ ] 5.1.3 | Implement VirusTotalClient with RestClient, 2 s timeout, API key from env VT_API_KEY, returns empty when key is missing or the call fails | backend/src/main/java/com/fraudshield/service/VirusTotalClient.java | 3h
- [ ] 5.1.4 | Add a rate limiter that respects the free-tier limit and a 24 h cache by hash so repeated hashes cost no quota | VirusTotalClient.java | 2h
- [ ] 5.1.5 | Call the client from FileScanService.scan after the local blocklist check; add score and reason "Flagged by N antivirus engines" | FileScanService.java | 2h
- [ ] 5.1.6 | Write unit tests with a stub client: malicious result raises score, empty result keeps score, timeout does not fail the scan | backend/src/test/java/com/fraudshield/service/FileReputationTest.java | 2h
- [ ] 5.1.7 | Add config keys virustotal.enabled (default false) and virustotal.api-key | backend/src/main/resources/application.yml, application-dev.yml | 1h
- [ ] 5.1.v | Verify gate: EICAR hash 275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f is reported by VT through POST /api/v1/scan/file on port 18080, and only the hash is sent | curl output plus mvn test green in Docker | 1h

## 5.2 [BE] Extra heuristics: PDF with `/JavaScript` `/Launch`, Office macro flag (needs larger `headerHex`/flags from app)
Goal: Score PDFs and Office files using content flags sent by the app.
Depends on: 5.6
- [ ] 5.2.1 | Define the flag names the app will send (PDF_JS, PDF_LAUNCH, PDF_OPENACTION, OFFICE_MACRO) and write them into the API spec | docs/04-API-SPEC.md section POST /scan/file | 1h
- [ ] 5.2.2 | Add optional field flags (list of string, max 10 items) to FileScanRequest; old requests stay valid | backend/src/main/java/com/fraudshield/dto/Dtos.java | 1h
- [ ] 5.2.3 | Add scoring for PDF flags (JS +35, Launch +50, OpenAction +15) | FileScanService.java | 2h
- [ ] 5.2.4 | Add scoring for OFFICE_MACRO, including macro flag on docx/xlsx names | FileScanService.java | 2h
- [ ] 5.2.5 | Add unit tests: clean PDF, PDF with JS, PDF with Launch, docx with macro flag, request without flags | backend/src/test/java/com/fraudshield/service/ScanServicesTest.java | 2h
- [ ] 5.2.6 | Add an integration test that posts flags in JSON | ApiFlowIT.java test fileWithPdfLaunchFlagIsMalicious | 2h
- [ ] 5.2.v | Verify gate: test PDFs score correctly (clean below 30, Launch 70 or more) | mvn test and mvn verify output in Docker | 1h

## 5.3 [BE] File threat stats endpoint (counts by type)
Goal: Return counts of the user's threats grouped by type so the dashboard can show numbers.
Depends on: none
- [ ] 5.3.1 | Define response DTO ThreatSummary (counts by type, by severity, open count, total) and add it to the API spec | Dtos.java, docs/04-API-SPEC.md | 1h
- [ ] 5.3.2 | Add grouped count queries to ThreatEventRepository (by type, status OPEN and all) | backend/src/main/java/com/fraudshield/repo/Repositories.java | 2h
- [ ] 5.3.3 | Add ThreatService.summary(userId) building the DTO, with zero for types that have no rows | ThreatService.java | 1h
- [ ] 5.3.4 | Add GET /api/v1/threats/summary in ThreatController (JWT required) | ThreatController.java | 1h
- [ ] 5.3.5 | Integration test: 2 file threats and 1 link threat give 2 and 1; another user sees zeros; no token gives 401 | ApiFlowIT.java test threatSummaryCounts | 2h
- [ ] 5.3.v | Verify gate: dashboard numbers match the threat list | curl GET /threats/summary compared with GET /threats on port 18080, counts equal | 1h

## 5.4 [FE] Folder scanner (Downloads, WhatsApp, WhatsApp Business, Telegram) + SHA-256
Goal: Confirm a new file in the watched folders is reported to the backend within 20 s.
Depends on: 5.8
- [ ] 5.4.1 | Verify the WhatsApp, WhatsApp Business and Telegram folder paths used in FileScanner.roots() on Android 11-14 | list of confirmed paths as a comment in mobile/android-native/kotlin/FileScanner.kt | 2h
- [ ] 5.4.2 | Fix roots for Android 11+ paths (Android/media/...) and add Telegram subfolders if needed | FileScanner.kt | 2h
- [ ] 5.4.3 | Skip files still being written (size changing) and hash off the main thread | FileScanner.kt scanNew | 3h
- [ ] 5.4.4 | Log timing in the fast loop (file seen to request sent) | mobile/lib/core/protection_engine.dart runFast | 1h
- [ ] 5.4.5 | Write a Kotlin unit test for sha256 and header using a temp file with the EICAR string | mobile/android-native/test/FileScannerTest.kt | 2h
- [ ] 5.4.6 | Manual test: download in Chrome, receive a WhatsApp file, receive a Telegram file; record time to alert | test notes under docs/ with logcat lines | 2h
- [ ] 5.4.v | Verify gate: new file in Downloads is reported within 20 s | adb logcat plus backend request log with timestamps | 1h

## 5.5 [FE] First-run baseline (don't flag existing library)
Goal: Existing files are ignored and only files added later are flagged.
Depends on: 5.4
- [ ] 5.5.1 | Review scanNew: firstRun uses prefs.contains("seen"); confirm the key is saved even when no file exists | FileScanner.kt | 1h
- [ ] 5.5.2 | Fix the 5000-entry cap so a big library does not push baseline entries out and make old files reappear | FileScanner.kt (hash ids, raise cap or store in a file) | 2h
- [ ] 5.5.3 | Extract the seen-set logic into a testable class | mobile/android-native/kotlin/BaselineStore.kt | 2h
- [ ] 5.5.4 | Write unit tests: first run returns zero, new file returns one, modified file returns one | mobile/android-native/test/BaselineStoreTest.kt | 2h
- [ ] 5.5.5 | Manual test on a device with 50+ existing files: fresh install, then add one file | notes with logcat lines | 1h
- [ ] 5.5.v | Verify gate: existing files ignored, new file flagged | unit test output plus device logcat | 1h

## 5.6 [FE] PDF/Office flag extraction (scan first 64 KB for `/JS`, `/Launch`, `vbaProject`)
Goal: Read the first 64 KB of PDF and Office files and send only flag names, never content.
Depends on: none
- [ ] 5.6.1 | Create ContentFlagger with flags(file) that reads at most 64 KB | mobile/android-native/kotlin/ContentFlagger.kt | 2h
- [ ] 5.6.2 | Implement PDF checks (/JavaScript, /JS, /Launch, /OpenAction) on raw bytes | ContentFlagger.kt | 2h
- [ ] 5.6.3 | Implement Office checks: docx/xlsx are ZIP files so look for vbaProject.bin in entry names; document the 64 KB limit; old .doc/.xls macro marker | ContentFlagger.kt | 3h
- [ ] 5.6.4 | Add flags to the map from FileScanner.scanNew and send them in the Dart scanFile call | FileScanner.kt, mobile/lib/core/api_client.dart | 1h
- [ ] 5.6.5 | Create fixture files (clean PDF, PDF with JS, PDF with Launch, macro docm) and unit tests | mobile/android-native/test/ContentFlaggerTest.kt | 3h
- [ ] 5.6.v | Verify gate: test files produce the expected flags and the request body holds flag names only | unit test output plus a captured request JSON | 1h

## 5.7 [FE] Delete flow with confirmation; restricted to scan roots
Goal: Delete a bad file only after confirmation and never outside the scan roots.
Depends on: 5.4
- [ ] 5.7.1 | Fix FileScanner.delete: startsWith on canonical path also accepts sibling folders like Download-evil; compare with a trailing separator | FileScanner.kt | 1h
- [ ] 5.7.2 | Refuse symlinks and paths with .. | FileScanner.kt | 1h
- [ ] 5.7.3 | Write unit tests: file in root deleted, /system/build.prop refused, sibling folder refused, ../ escape refused | mobile/android-native/test/FileScannerDeleteTest.kt | 2h
- [ ] 5.7.4 | Add a confirm step ("Delete this file?") to the Delete action and show deleted or refused | mobile/lib/core/protection_engine.dart, mobile/lib/screens/dashboard_screen.dart | 2h
- [ ] 5.7.5 | Widget test for the confirm dialog (cancel keeps the file, confirm calls NativeBridge.deleteFile) | mobile/test/file_delete_test.dart | 2h
- [ ] 5.7.v | Verify gate: file gone after confirm; deleting a /system path is refused | unit test output plus screen recording | 1h

## 5.8 [FE] All-files-access permission UX + Android 11–14 testing
Goal: Both granted and denied flows work on Android 11 to 14.
Depends on: none
- [ ] 5.8.1 | Review hasAllFilesAccess and openAllFilesAccessSettings for Android 8-10 (legacy storage permission needed) and decide the fallback | note in mobile/android-native/kotlin/MainActivity.kt | 2h
- [ ] 5.8.2 | Add legacy storage permissions with maxSdkVersion 29 and a runtime request for Android 10 and below | mobile/android-native/AndroidManifest.snippet.xml, MainActivity.kt | 2h
- [ ] 5.8.3 | Refresh the permission card when the user returns from system settings (lifecycle resume) | mobile/lib/screens/dashboard_screen.dart | 2h
- [ ] 5.8.4 | Add a plain explanation before opening settings and a clear "file protection is off" tile when denied | dashboard_screen.dart | 1h
- [ ] 5.8.5 | Test grant and deny flows on Android 11, 12, 13, 14; fill a results table | results table under docs/ (device, version, result) | 3h
- [ ] 5.8.v | Verify gate: granted and denied flows both OK on Android 11-14 | filled results table | 1h
