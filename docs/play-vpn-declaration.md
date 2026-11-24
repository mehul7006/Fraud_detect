# 12.11 Google Play: VpnService declaration and in-app disclosure

Policy basis (Google Play "VpnService" policy, Developer Program Policies, checked 2026-11): apps may use `VpnService`
only when the VPN is the app's core functionality, must disclose what data is handled, must not collect personal
or sensitive data for advertising, must not redirect / manipulate traffic or ads, and must declare the use in the
Play Console ("App content" > VPN service). Security apps (device protection, filtering of harmful sites) are an
accepted use case. **Re-read the live policy page before submitting** - it changes.

## Play Console declaration (paste)
**Core functionality using VpnService:** Fraud and phishing protection. FraudShield's VPN runs entirely on the
device and only handles DNS lookups (website names). Each name is checked against a signed list of known fake
banking / shopping / government sites; matches are blocked so the fake page cannot load, and the user is shown a
warning naming the real site. All other traffic goes directly to the internet - it is not routed through the VPN,
not inspected, not proxied and not modified.

**Data handled:** website names looked up by apps on the device, and which app looked them up. Kept only on the
device (30 days, deletable). For a name not yet known, only the name itself is sent to our server to check it; it is
not stored there and not linked to the user. "Local-only mode" turns these checks off. No page content, full URLs,
messages, credentials or payment data are seen. Nothing is used for advertising, sold or shared.

**User control:** the VPN starts only after an in-app explanation and Android's system VPN consent; it can be turned
off in the app or in Android settings at any time.

## In-app prominent disclosure (shown before the system prompt) - `vpn_consent_screen.dart`
Title "Block fake websites in every app", then three labelled points: **What FraudShield sees** (only website names
and which app asked), **What it never sees** (pages, messages, passwords, payments, full links; nothing recorded on
the server), **What leaves your phone** (only an unknown website name, never linked to you; nothing in local-only
mode), and the note that Android will ask to allow a VPN and shows a key icon. Buttons: **Turn on protection** /
**Not now**. English and Hindi.

## Checklist before submission
- [ ] Play Console > App content > VPN service: declaration filled with the text above
- [ ] Privacy policy (docs/PRIVACY-POLICY.md) section on the VPN matches this text
- [ ] Foreground service type `systemExempted` declared for the VPN service (Play Console > App content > Foreground
      service permissions: "VPN app")
- [ ] Screenshot/video of the disclosure + Android prompt for the review team
