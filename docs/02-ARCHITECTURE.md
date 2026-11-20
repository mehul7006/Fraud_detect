# Architecture & Full Flow

```
┌──────────────────────── Android phone ────────────────────────┐
│ Flutter UI (dashboard, alerts, wizards)                        │
│    │ MethodChannel / EventChannel                              │
│ Kotlin native layer                                            │
│  ├ ProtectionService (foreground)                              │
│  ├ AdNotificationListener  → counts notifs/pkg, extracts URLs  │
│  ├ FileWatcher             → new APK/PDF/doc → SHA-256         │
│  ├ CallForwardChecker      → USSD *#21# etc.                   │
│  ├ AppAuditor              → risky perms, overlay, RAT apps    │
│  └ DeviceFingerprint       → SIM, model, root, play-protect    │
└───────────────┬────────────────────────────────────────────────┘
                │ HTTPS + JWT
┌───────────────▼─────────────── Spring Boot backend ───────────┐
│ AuthController      /auth/register /auth/login                 │
│ DeviceController    /devices  (register + heartbeat+clone risk)│
│ ScanController      /scan/url  /scan/file  /scan/apps          │
│ ThreatController    /threats (list, resolve)                   │
│ Services: UrlScan, FileScan, AdAbuse, CloneRisk, Threat        │
│ PostgreSQL: users, devices, threat_events, blocklist, app_rep  │
└────────────────────────────────────────────────────────────────┘
```

## Flows
**Link flow**: WhatsApp notification arrives → listener extracts URL → `POST /scan/url` → verdict `SAFE|SUSPICIOUS|MALICIOUS` → if not SAFE: full-screen warning "Don't open", record ThreatEvent, offer "Report/Block".

**File flow**: new file in watched folder → hash + metadata → `POST /scan/file` → MALICIOUS → popup → user taps Delete → `MediaStore.createDeleteRequest`.

**Ad abuse flow**: listener counts per package; every 15 min the app posts `/scan/apps` with `{pkg, notifPerHour, hasOverlay, ...}` → backend scores → ≥70 → popup "App X shows ads constantly" → Uninstall button.

**Call forwarding flow**: on app start + every 6 h: USSD `*#21#` → parse → if forwarded: popup → "Disable now" runs `##002#`.

**Account takeover (WhatsApp/Telegram)**: unexpected OTP notification or "new device login" notification → CRITICAL ThreatEvent → persistent popup → wizard: step-by-step with deep links to log out other devices.

**Clone risk**: each heartbeat sends fingerprint; backend flags if same user has another active device with different fingerprint, SIM changed, RAT app present.

## Native files to copy into Flutter android project
After `flutter create`, copy `mobile/android-native/kotlin/*` to `android/app/src/main/kotlin/com/fraudshield/app/` and merge `mobile/android-native/AndroidManifest.snippet.xml` into `AndroidManifest.xml`.

## Tech choices
Java 21, Spring Boot 3.3, Spring Security + JWT (jjwt), Spring Data JPA, Flyway, PostgreSQL 16, Flutter 3.x, Riverpod-free minimal state (setState/ValueNotifier) to start, `http` package, `flutter_local_notifications`.
