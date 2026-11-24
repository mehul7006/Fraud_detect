# FraudShield Privacy Policy

_Version 1 – 22 November 2026. Draft for review before publication (roadmap 9.4). Replace the bracketed items
before going live: **[Company name]**, **[contact email]**, **[address]**, **[web deletion page]**._

FraudShield protects your phone against scams: dangerous links, harmful files, call forwarding, risky apps and account
takeover. This policy explains exactly what the app reads on your phone, what it sends to our server, and why.

## 1. What never leaves your phone
- **The text of your messages and notifications.** FraudShield reads notifications only to find web links, login-code
  notices and ad spam. The message text itself is never uploaded or stored.
- **Your contacts, photos, files' contents, call logs, SMS contents and location.**
- **Carrier replies to the call-forwarding check** (they can contain phone numbers). Only the result (on / off) is used.
- **The list of all your apps.** Only apps that already show a risky sign are checked with our server (see 2.4).

## 2. What is sent to the FraudShield server, and why
| Data | Why | When |
|---|---|---|
| **2.1 Account:** email address and password (stored only as a one-way hash, never readable) | to log you in | when you register / log in |
| **2.2 Device check:** a scrambled (SHA-256) device ID, scrambled SIM ID and scrambled hardware fingerprint, phone model, Android version, whether the phone is rooted or an emulator, whether Google Play Protect is on, and which known remote-access apps (e.g. AnyDesk, TeamViewer) are installed | to warn you about phone cloning, SIM swap and remote control (scammers use these to take over accounts) | about once an hour while protection runs |
| **2.3 Links:** the web address of a link found in a notification or shared with FraudShield, and the name of the app it came from | to check the link against known scam sites | when a link arrives |
| **2.4 Apps with a risky sign:** for those apps only: package name, signing-certificate fingerprint, the store that installed it, how many notifications it posted in the last hour, which sensitive permissions it holds (overlay, SMS, accessibility, notification access, full-screen alerts) and how often it opened by itself | to warn you about ad-spam, fake and SMS-stealing apps | about once an hour |
| **2.5 Files:** the SHA-256 fingerprint, file name and size of a newly downloaded APK / PDF / document (never the file itself) | to check it against known malware | when a new file appears in Downloads, WhatsApp or Telegram folders |
| **2.6 Threats found:** type, severity, short title (e.g. "Call forwarding is ON (all calls)") | your threat history; account-takeover warnings | when a threat is found |
| **2.7 Optional:** if you switch on "Help improve risky-app detection", the signals in 2.4 plus your choice (Uninstall / Ignore) for that app | to make detection more accurate | only when you choose it (off by default) |
| **2.8 Optional:** links you report as wrongly flagged / missed | to correct our block list | only when you report one |

## 3. Who else sees data
- **Google Safe Browsing** receives the web address of links we check (2.3).
- **VirusTotal** (Google) receives only the file fingerprint (2.5) – never the file.
- **Firebase Cloud Messaging** (Google) delivers our warnings to your phone. It receives a push token for your app
  install and the short warning title (e.g. "Account takeover in progress") – no links, numbers or account details.
- **Firebase Crashlytics** (Google), release builds only: when the app crashes, a crash report (technical stack
  trace, phone model, Android version, app version) so we can fix the bug. No messages, contacts or account data.
- **No advertising, no analytics SDKs, no selling or sharing of data** with anyone else.
- Our hosting provider stores the data on our behalf in [region], encrypted in transit (HTTPS) and at rest.

## 4. Permissions and what each is used for
| Permission | Used for |
|---|---|
| Notification access | find dangerous links, login codes and new-device notices in notifications; count ad spam |
| All files access / storage (Android 8–10) | check newly downloaded files for malware |
| Phone (calls / phone state) | ask your carrier whether call forwarding is on (`*#21#` and similar) |
| Usage access (optional) | notice apps that open by themselves |
| Notifications | show you warnings |
| Query all packages | find risky and fake apps |
| VPN (optional, "Fake-website protection") | block fake websites in every app - see 4a |
FraudShield works without any single permission; that protection is then switched off and the app says so.

### 4a. Fake-website protection (on-device VPN)
When you turn it on, FraudShield runs a VPN **on your phone** that handles only website-name lookups (DNS).
- **Seen:** the website names apps look up (e.g. `flipkart.com`) and which app looked them up. Kept on your phone
  for 30 days ("Which apps contact which sites"); never uploaded. Data-use numbers come from Android's own counters.
- **Never seen:** page content, full links, messages, passwords, payments. Your internet traffic does not pass
  through FraudShield.
- **Sent to our server:** only a website name not yet known, to check whether it is fake. It is not stored and not
  linked to your account. In "Local-only mode" nothing is sent. The list of known fake sites is downloaded as
  hashed, signed entries.
- You can turn it off in the app or in Android settings at any time.

### 4b. Call guard (PROPOSED text for Phase 16 - needs owner / legal review before release)
When you make FraudShield your "caller ID & spam app", Android asks it about each incoming call from a number that is
not in your contacts. The decision is made **on your phone**.
- **Stays on your phone:** your call log, your contacts, who called you and when, the list of blocked calls
  (kept 30 days on the phone), your always-allow list.
- **Downloaded:** a signed list of scam-number fingerprints (SHA-256 hashes, never numbers) and free number-pattern
  rules (for example the official 1600 and 140 series and foreign country codes).
- **Lookup of an unknown number:** the phone may send only the **first 5 characters of the number's fingerprint**.
  That prefix is shared by thousands of numbers, so the server cannot tell which number called you. The server
  answers with every reported fingerprint in that group and your phone finds the match itself.
- **When you tap "Report":** the number and the category you choose (scam / spam / telemarketing) are sent to the
  server. The server turns the number into its fingerprint at once and keeps only the fingerprint, the category and
  your one vote (so the same person cannot report a number twice). The number itself is never stored or logged. Your
  vote is deleted when you delete your account.
- **Never collected:** call recordings, call audio, the caller's name, your contacts, your call history.
- **Limits, honestly:** a fingerprint of a phone number can in principle be matched back to the number by trying
  every possible number, so a fingerprint is treated as personal data and protected like it.
- Calls from the official **1600** (banks, insurers, government) and **140** (registered promotional) series are
  never marked as spam or blocked by FraudShield, as TRAI requires. To stop promotional calls, use DND (call or SMS 1909).

## 5. How long we keep data
Until you delete your account. Threats you mark as resolved stay in your history until then. Login sessions
expire after 30 days of not opening the app.

## 6. Your rights: see, correct, delete
- **Delete your account and all its data** at any time: FraudShield > Settings > **Delete my account**, or on the
  web at **[web deletion page]**. This removes every record linked to you from our database immediately (tested
  automatically: no row of a deleted user remains).
- To get a copy of your data or ask a question: **[contact email]**.
- Grievance officer (India, IT Rules 2021 / DPDP Act 2023): **[name, contact email, address]**.

## 7. Children
FraudShield is not directed at children under 13.

## 8. Changes
If this policy changes in a way that affects your data, the app asks you to read and accept the new version.

Contact: **[Company name]**, **[address]**, **[contact email]**.
