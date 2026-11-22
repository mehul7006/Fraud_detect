# Google Play Console declarations – draft (roadmap 9.3)

Drafted 22 November 2026 from the permissions the app really declares (`mobile/tool/setup_android.sh`) and what the
code does with them. You submit these in Play Console > App content; each sensitive one needs a short **demo video**
(screen recording: open the app, show the in-app disclosure, grant the permission, show the feature working).
Policy wording changes often – re-check each Play policy page before submitting.

## 1. Permission declarations

| Permission | Play policy | Our answer (core feature) | Video should show |
|---|---|---|---|
| `QUERY_ALL_PACKAGES` | "Package visibility" – allowed for **antivirus / device security** apps | FraudShield scans installed apps for ad-spam, fake copies of known apps (signing certificate) and SMS-stealers; it must see every app to find them. | Risky apps screen listing a flagged app with its reasons |
| `MANAGE_EXTERNAL_STORAGE` (All files access) | "All files access" – allowed for **antivirus** apps | Newly downloaded APK / PDF / document files are checked for malware by fingerprint (SHA-256); only the fingerprint and file name leave the phone. | Grant "All files access" from the in-app card, download a test file, see the alert |
| Notification listener (`BIND_NOTIFICATION_LISTENER_SERVICE`) | no form, but needs **prominent disclosure** (we have the consent screen + card) | Finds web links, login-code and new-device notices in notifications to stop phishing and account takeover; counts ad-spam notifications. Message text never leaves the phone. | Consent screen, "Enable link & ad monitoring" card, a scam link in an SMS -> "Dangerous link" alert |
| `CALL_PHONE`, `READ_PHONE_STATE` | not restricted; explain in the disclosure | Asks the carrier whether call forwarding is on (`*#21#`, `*#67#`, `*#61#`, `*#62#`) and reads Android's call-forwarding indicator; one-tap turn-off (`##002#`). | Call forwarding tile, ON alert, Turn OFF |
| `PACKAGE_USAGE_STATS` (optional) | special access, user grants in Settings | Notices apps that open by themselves (ad fraud). Only a count per app. | Optional card |
| `REQUEST_DELETE_PACKAGES` | not restricted | "Uninstall" button opens Android's own uninstall dialog for a flagged app. | Uninstall from the risky-apps screen |
| `USE_FULL_SCREEN_INTENT` | **Android 14+: full-screen intents are granted by default only to calling and alarm apps** | Used for the critical lock-screen alert (7.10). **Risk:** Play may refuse the declaration; the app must then fall back to a high-priority notification (it already shows one) and ask the user to allow full-screen alerts in Settings. Decide before submitting (see 7.10). | – |

## 2. Data safety form (from `docs/PRIVACY-POLICY.md`)
- **Data collected:** Email address (account management, required); App interactions? **No.** Device or other IDs:
  **Yes** – scrambled device / SIM / hardware IDs (fraud prevention, security). Web browsing: **Yes** – URLs of links
  that are checked (fraud prevention, security). Files and docs: **Yes** – file fingerprint + name only (security).
  Installed apps: **Yes** – only apps with a risky sign (security). Messages / contacts / location / photos: **No.**
- **Shared with third parties:** URLs -> Google Safe Browsing; file fingerprints -> VirusTotal (both for security).
- **Crash logs + diagnostics:** Yes – Firebase Crashlytics, release builds (app functionality / analytics of crashes).
- **Device or other IDs:** also the Firebase push token (app functionality: security alerts).
- **Encrypted in transit:** Yes (https only; release build refuses http, 8.9).
- **Users can request deletion:** Yes – in-app "Delete my account" + web page (9.4).
- **Optional collection:** "Help improve risky-app detection" (off by default).

## 3. Other forms
- Target audience: 18+ (not for children).
- Ads: **No ads.**
- Account deletion URL (required since 2024): the web deletion page from 9.4 – **needs hosting (9.1)**.
- Government / financial features: none (FraudShield does not process payments).

## Still needed from you
Play Console developer account, company details for the store listing, the privacy-policy + deletion URLs
(after hosting), and the demo videos (I can script the emulator recordings once the release build exists).
