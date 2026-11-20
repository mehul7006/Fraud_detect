# Feasibility: what Android lets an app do

Android sandboxing and Google Play policy limit this. Plan around these facts, not against them.

| # | Your requirement | Possible? | How we do it |
|---|---|---|---|
| 1 | Detect phone **cloned** | Partly | A clone cannot be proven from inside the phone. We detect **signals**: same account active on 2+ device fingerprints, SIM swap (subscriberId/ICC change), unknown device-admin apps, remote-access apps installed (AnyDesk, TeamViewer, AirDroid), screen-sharing/accessibility abuse, root/emulator flags, Play Protect off. Backend scores risk (`CloneRiskService`). |
| 2 | Detect **call forwarding** | Yes | Query the carrier with USSD `*#21#` (all forwarding) / `*#67#` (busy) / `*#61#` (no answer) / `*#62#` (unreachable) via `TelephonyManager.sendUssdRequest` (API 26+, needs `CALL_PHONE`). Parse the response. Disable with `##002#`. Falls back to opening the dialer with the code. |
| 3 | Detect **SMS forwarding** | Limited | No Android API. (a) Detect apps that hold `RECEIVE_SMS`/`READ_SMS`/notification-listener (PackageManager audit) = likely SMS stealers. (b) Detect SIM-level SMS forwarding only through the carrier – app guides user to carrier code / customer care. |
| 4 | Detect apps that spam **ads** | Yes | `NotificationListenerService` counts notifications per package/hour; `UsageStatsManager` + `SYSTEM_ALERT_WINDOW` audit finds apps that draw over other apps / launch full-screen intents. Score on backend (`AdAbuseService`). Offer "Uninstall" (opens system uninstall dialog – user must confirm). |
| 5 | Detect **WhatsApp hacked** | Indirect | WhatsApp exposes no API. Signals: "Linked devices" can't be read automatically. We (a) alert on WhatsApp login-code SMS/notifications the user did not request, (b) 2-step-verification reminders, (c) guided one-tap deep link into WhatsApp > Linked devices > Log out all. |
| 6 | Detect **Telegram hacked** | Indirect | Same. Guided steps: Settings > Devices > Terminate all other sessions. Optional: user logs in with Telegram TDLib/Bot API to list sessions (v2, opt-in). |
| 7 | **Log out** WhatsApp/Telegram from all devices automatically | **No** | Not allowed by those apps. We provide guided deep-links + step wizard. Be honest in UI; do not promise auto logout. |
| 8 | **Stop** call forwarding | Yes | `##002#` USSD via same API. |
| 9 | Scan **links** in WhatsApp/Telegram/other apps | Yes (with consent) | `NotificationListenerService` reads incoming notification text, extracts URLs, sends to backend `/scan/url`. Also an Android share-target ("Share link to FraudShield") and tap-intercept as browser-handler (v2). Cannot read inside chat window without Accessibility (Play-restricted). |
| 10 | Block malware/suspicious link | Partial | Show full-screen warning + local VPN-based DNS blocking (v2 `VpnService`). Cannot kill another app's link opening without VPN. |
| 11 | Detect **malicious APK / PDF** received | Yes | `FileObserver`/`MediaStore` watches Downloads, WhatsApp Media, Telegram dirs (scoped-storage permissions). SHA-256 sent to backend; heuristics (double extension, APK, PDF with /JavaScript /Launch, macro Office). Delete via `MediaStore.createDeleteRequest` (user confirms). |
| 12 | Constant popup when threat | Yes | High-priority notification + full-screen intent + foreground service. |

## Google Play policy warnings
- `READ_SMS`, `CALL_LOG`, `AccessibilityService`, `QUERY_ALL_PACKAGES`: need Permission Declaration Form; security apps are accepted only if core purpose is clearly declared.
- Do not read chat content. Notification listener: explain on a consent screen and let user choose which apps are monitored.
- Distribute first as sideload/internal test; Play listing later.

## Privacy rules (non-negotiable)
- Send to backend only: URL, file hash, package names, counters. Never message text, contacts, call logs.
- All requests over TLS, JWT auth, delete-my-data endpoint.

## Added requirements (fraud-focus phases 10–15)
| # | Requirement | Possible? | How / honest limit |
|---|---|---|---|
| 13 | Detect fake look-alike websites (fleepkart vs flipkart) | Yes | Server: brand registry + look-alike matcher + domain age + page comparison + ML. Brand list must be maintained; a brand-new brand is unknown until added. |
| 14 | Block fake websites in any browser | Yes, with user consent | Local VPN (`VpnService`) with DNS check. Sees domains only, not HTTPS paths or content. DoH apps can bypass. |
| 15 | Detect apps that over-collect data / send it to third parties | Yes | Permission-vs-purpose rules, tracker-SDK signatures, per-app domain log from the VPN. Shows what an app *can* and *does* contact, not the content it sends. |
| 16 | "Block" a leaking app | Partial | Block the app's internet via VPN, repeat warnings, one-tap uninstall dialog. Android never allows forced uninstall/disable by another app. |
| 17 | Detect fake apps copying a real brand | Yes | Compare package + signing-cert hash with an official-app registry. |
| 18 | Detect fake messages | Yes | On-device classifier + rules; optional opt-in cloud check (text not stored). Accuracy is never 100%: UI says "looks like", not "is". |
| 19 | LLM explanations | Yes | LLM only explains reason codes; it never decides the verdict. |
