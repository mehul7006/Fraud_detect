# Ad and tracker blocking - Google Play policy and distribution decision (18.1)

**Date:** 2026-11-24 · **Status:** proposal, **awaiting owner review** (roadmap 18.1 is done only after the owner signs off)
**Read from:** the live Play Console Help pages below (fetched 2026-11-24; the quotes are verbatim).

## What the policies say

### 1. Device and Network Abuse - <https://support.google.com/googleplay/android-developer/answer/9888379>

> "We don't allow apps that interfere with, disrupt, damage, or access in an unauthorized manner the user's device,
> other devices or computers, servers, networks, application programming interfaces (APIs), or services, including
> but not limited to other apps on the device, any Google service, or an authorized carrier's network."

Listed under *Examples of common Device and Network Abuse violations*:

> "Apps that block or interfere with another app displaying ads."

and in the summary box: *"Don't block or interfere with another app displaying ads."*

**Meaning for us:** a Play build must not block ads shown inside other apps. (Browsers blocking ads on web pages
inside themselves is a different case; FraudShield is not a browser, and our VPN filter covers all apps, so the
distinction does not help us.)

### 2. VpnService policy - <https://support.google.com/googleplay/android-developer/answer/12564964>

> "Only apps that use the VpnService and have VPN as their core functionality can create a secure device-level
> tunnel to a remote server. Exceptions include apps that require a remote server for core functionality such as:
> … Device security apps (for example, anti-virus, mobile device management, firewall) …"

> "The VpnService cannot be used to: Collect personal and sensitive user data without prominent disclosure and
> consent. Redirect or manipulate user traffic from other apps on a device for monetization purposes (for example,
> redirecting ads traffic through a country different than that of the user)."

Declaration question 5(a)/(b): *"Any manipulation of ads that can impact monetization will result in rejection."*

Apps must document the VpnService use in the store listing, show a separate prominent disclosure + consent
(done in 12.1 / `docs/play-vpn-declaration.md`), and submit the VpnService declaration.

**Meaning for us:** our on-device DNS filter is a *device security / firewall* use (it never tunnels traffic to a
remote server - only DNS is routed into the local VPN). Blocking *ad servers* would be "manipulation of ads that can
impact monetization" -> rejection. Blocking malware / phishing / scam servers is the security purpose itself.

### 3. Trackers (analytics / attribution) - our reading

The policies forbid interfering with *ads being displayed*. Blocking analytics / attribution endpoints (for example
`app-measurement.com`, `appsflyer.com`, `adjust.com`) does not stop any ad from being shown, and privacy firewalls
on Play do this today. Risk remains: a reviewer could see attribution as part of ad monetization. Mitigations built in:

* An entry is "trackers" only if it comes from a tracking-only source or our own curated list, **and** no ad
  source lists it **and** its name has no ad-like label (`ads`, `adserver`, `doubleclick`, `pagead`, …) -
  otherwise it is "ads" and the Play build never blocks it (`ml/blocklists/build_blocklists.py`).
* Per-app "Allow" and "This app broke" escape hatches (18.4).
* The tracker switch can be shipped OFF by default in the Play build if the review objects (one-line change in
  `BlockPolicy.defaults`).

## Decision (proposed)

| Build | How it is made | Blocks | Where it is offered |
|---|---|---|---|
| **Play** (default) | `flutter build appbundle --release --dart-define=FS_FLAVOUR=play` (or no define) | trackers + malware / phishing / scam | Google Play |
| **Website** | `flutter build apk --release --dart-define=FS_FLAVOUR=web` | + ads | our own website (signed APK) |

* Same code; the flavour is fixed at build time (`lib/core/flavour.dart`, `BuildConfig.FS_FLAVOUR` via
  `tool/flavour_gradle.py`). The Play build has **no ads switch anywhere**: the UI hides it, the native side
  refuses the ads category, and the Play variant of the list (`/config/blocklists?variant=play`) contains no ad
  entries at all - the server refuses to publish a play file with ads in it.
* In-app text in the Play build states that ads are not blocked and why (`blockAdsNotInPlay`).
* The website build is outside Play policy but must still meet the law and Play Protect expectations: same
  privacy policy, same disclosure, signed with our release key, its own update check.
* Both builds share one package name, so a phone has one or the other (switching = uninstall + install).

## Open points for the owner

1. Approve the decision above (or choose "Play: malware only").
2. Approve publishing the website build (support load, update channel, Play Protect warnings for sideloads).
3. Licences of the list sources: see `ml/blocklists/DATA-LICENSES.md`. 1Hosts is MPL-2.0: we publish the derived
   list (`domains.tsv.gz`) to meet it - confirm that is acceptable.
4. **Found while checking licences:** abuse.ch's current terms of use (<https://abuse.ch/terms-of-use/>, section 4)
   say commercial use of URLhaus "may require a paid subscription". The 13.6 domain-category seed uses 68 URLhaus
   malware hosts as "CC0" - this should be reviewed (not used in the 18.2 list).
