# Phase 12 – Local VPN core
## 12.1 [FE] `VpnService` skeleton, foreground notification, consent screen
Goal: a VPN that starts and stops, shows the key icon, and leaves the internet working.
Depends on: none
- [ ] 12.1.1 | Decide the tunnel mode: DNS-only tunnel (route only a virtual DNS address; other traffic bypasses the VPN) or full tunnel (all packets, needs a user-space TCP/UDP forwarder); note what 12.8 and 13.9 need from it (for example per-app byte counts via NetworkStatsManager, per-app blocking needs full tunnel) | docs/vpn-design.md with the decision and reasons | 2h
- [ ] 12.1.2 | Add the service entry (BIND_VPN_SERVICE permission, intent action android.net.VpnService, foreground service type) to the manifest snippet | mobile/android-native/AndroidManifest.snippet.xml | 1h
- [ ] 12.1.3 | Create `FraudVpnService.kt`: build the TUN interface (address, DNS server, routes per 12.1.1), start in the foreground with a notification channel and a Stop action, close cleanly on stop | mobile/android-native/kotlin/FraudVpnService.kt | 4h
- [ ] 12.1.4 | Add MethodChannel calls `startVpn`, `stopVpn`, `vpnState` in MainActivity.kt, including the system `VpnService.prepare()` consent dialog and the activity result | MainActivity.kt, mobile/lib/core/native_bridge.dart | 3h
- [ ] 12.1.5 | Build the consent screen in plain language: what is seen (domain names and byte counts), what is not seen (page content, passwords, full web addresses of secure sites), what leaves the phone (domain only), buttons Agree and Not now | mobile/lib/screens/vpn_consent_screen.dart | 3h
- [ ] 12.1.6 | Wire the dashboard to open the consent screen, start the VPN after Agree, and show state on a tile | dashboard_screen.dart, protection_engine.dart status key `vpn` | 2h
- [ ] 12.1.7 | Write the widget test for the consent screen (Agree calls start, Not now does not) and run analyze | mobile/test/vpn_consent_screen_test.dart | 2h
- [ ] 12.1.v | Verify gate: key icon appears and internet keeps working | `flutter analyze` and `flutter test` in the Flutter Docker image green; on a real phone: screenshot of the key icon, a page loading in Chrome while the VPN is on | 2h

## 12.2 [FE] Packet parser and app (UID) mapping
Goal: read DNS queries and answers from the tunnel and know which app asked.
Depends on: 12.1
- [ ] 12.2.1 | Check mobile/tool/setup_android.sh to see how Kotlin files reach the generated android project, and make a place for Kotlin unit tests that the script also copies (JUnit, runnable in a Docker Gradle or Flutter image) | Script change, empty test class that runs | 2h
- [ ] 12.2.2 | Create fixtures of real packets: IPv4 UDP DNS query for `flipkart.com`, IPv6 UDP DNS query, DNS answer with A record, answer with CNAME and name compression; make them with scapy in a python Docker container and save as hex | mobile/android-native/test/fixtures/packets.hex, generator script in mobile/tool/make_packets.py | 3h
- [ ] 12.2.3 | Create `PacketParser.kt`: parse IPv4 and IPv6 headers, detect UDP to port 53, return source port, destination, and payload; ignore everything else safely (bounds checks everywhere) | mobile/android-native/kotlin/PacketParser.kt | 4h
- [ ] 12.2.4 | Add DNS message parsing: question name (labels and compression pointers), type, answers with TTL and addresses | PacketParser.kt (DnsMessage class) | 3h
- [ ] 12.2.5 | Create `UidResolver.kt`: on API 29+ use `ConnectivityManager.getConnectionOwnerUid`; for API 26-28 fall back to reading /proc/net/udp and udp6 (verify they are readable on those versions); map UID to package name and label | mobile/android-native/kotlin/UidResolver.kt | 3h
- [ ] 12.2.6 | Write unit tests over the fixtures including malformed and truncated packets (must not crash) | mobile/android-native/test/PacketParserTest.kt | 3h
- [ ] 12.2.7 | Log "domain + app name" for each query in FraudVpnService (debug builds only) | Logcat line format `DNS flipkart.com <- com.android.chrome` | 1h
- [ ] 12.2.v | Verify gate: log shows domain and app while browsing; parser unit test passes | Unit test output plus logcat copy of 5 lines from a real phone | 2h

## 12.3 [FE] DNS forwarding, local cache, upstream choice, DoH handling
Goal: sites load normally through our DNS proxy, and known DoH bypass is handled.
Depends on: 12.2
- [ ] 12.3.1 | Create `DnsProxy.kt`: send the query payload to the upstream through a socket protected with `VpnService.protect`, return the response in a new UDP packet to the TUN (swap addresses and ports, fix checksums) | mobile/android-native/kotlin/DnsProxy.kt | 4h
- [ ] 12.3.2 | Add a local cache with TTL (honor answer TTL, cap at 1 hour, max 2,000 entries) and a negative cache for NXDOMAIN of 60 s | DnsProxy.kt cache class plus unit test | 3h
- [ ] 12.3.3 | Add upstream choice: system DNS (default) plus a short list of public resolvers; verify each provider's privacy statement before listing it; store the choice | settings entry, DnsProxy upstream config, docs/vpn-design.md list with links | 3h
- [ ] 12.3.4 | Verify and collect the known DoH hostnames and IPs from public lists, with the source and date; decide per host: block (force fallback to normal DNS) or warn | mobile/android-native/assets/doh-hosts.json, notes in docs/vpn-design.md | 2h
- [ ] 12.3.5 | Implement DoH handling: resolve of a DoH host returns NXDOMAIN or triggers a one-time notice depending on the decision; make it switchable in settings | DnsProxy.kt, DohPolicy.kt | 3h
- [ ] 12.3.6 | Measure latency: log time from query to response for 200 queries; compute added latency against system DNS without VPN (use a small timing screen or logcat) | docs/vpn-latency.md with median and p95 | 2h
- [ ] 12.3.7 | Check big sites, an IPv6-only case, and a captive Wi-Fi login; fix what breaks | Checklist in docs/vpn-design.md all ticked | 2h
- [ ] 12.3.v | Verify gate: sites load, added latency under 20 ms, DoH test host handled | docs/vpn-latency.md numbers plus screenshot of a DoH test page (for example a DoH hostname opened in the browser) showing the handled result | 1h

## 12.4 [FE] Domain-check pipeline and "Blocked fake site" alert
Goal: a known fake domain opened in Chrome is blocked in under 1 second, with an alert.
Depends on: 12.3, 12.5, 12.6
- [ ] 12.4.1 | Decide the timing rule: how long a DNS answer may be held while waiting for the server (for example 300 ms), and what happens after: unknown domain is let through and a later answer blocks the next lookup | Decision in docs/vpn-design.md | 1h
- [ ] 12.4.2 | Create the local fast filter: store hashed blocklist (SHA-256 of registrable domain) in a Set loaded from local storage; check each queried domain | mobile/android-native/kotlin/DomainGuard.kt (local part) with unit test | 3h
- [ ] 12.4.3 | Implement blocklist sync client against `GET /config/blocklist?since=` including signature verification with the public key embedded in the app; refuse and keep old list on a bad signature | BlocklistSync.kt, unit test with a tampered payload | 4h
- [ ] 12.4.4 | Make the auth token readable from Kotlin (the VPN runs without the Flutter screen): store it in a place both sides can read securely and update it on login and logout | api_client.dart, native storage helper, MainActivity.kt | 3h
- [ ] 12.4.5 | Implement the async server check: for domains not in the local filter and not in a small "recently safe" cache, call `POST /scan/domain` with a short timeout and cache the verdict | DomainGuard.kt server part | 3h
- [ ] 12.4.6 | On MALICIOUS: answer the DNS query with NXDOMAIN and raise the full-screen "Blocked fake site" alert through AlertNotifier (domain, brand if known, official URL button) | DomainGuard.kt, AlertNotifier.kt, strings | 4h
- [ ] 12.4.7 | Report the event to the Flutter side so it appears in threat history without sending the domain with the user id to the server (store locally; POST /threats gets a generic title only) | protection_engine.dart, MainActivity.kt event channel | 2h
- [ ] 12.4.v | Verify gate: opening a known test fake domain (a domain added to the blocklist on staging, such as `example-phish.test` style entry) in Chrome is blocked and the alert shows within 1 s | Screen recording with timestamps plus unit test results | 2h

## 12.5 [BE] Blocklist sync `GET /config/blocklist?since=`
Goal: the app can download only new hashed blocklist entries and check they are genuine.
Depends on: none
- [ ] 12.5.1 | Write the design: entry = SHA-256 of lowercase registrable domain, version = increasing integer, response `{version, added[], removed[], signature}`, and the exact text that is signed | docs/12-blocklist-sync.md | 2h
- [ ] 12.5.2 | Add a Flyway migration to the `blocklist` table: `domain_sha256`, `added_version`, `removed_version` (nullable) and a sequence for versions; back-fill hashes for existing DOMAIN rows (next free migration number) | db/migration/V?__blocklist_sync.sql, Blocklist.java updated | 3h
- [ ] 12.5.3 | Update the code that adds blocklist entries so new rows get the hash and the next version (BlocklistRepository and any admin import from 4.3) | Repositories.java, service/BlocklistService.java | 3h
- [ ] 12.5.4 | Implement signing with Ed25519 from the JDK: private key from env `BLOCKLIST_SIGNING_KEY` (no default in prod, dev generates one at startup), public key printed by a tool; write the key generation command | service/SignatureService.java, backend/scripts/gen-signing-key.md | 3h
- [ ] 12.5.5 | Implement `ConfigController` `GET /api/v1/config/blocklist?since=`: delta only, 200 with empty lists if nothing new, 400 for a bad `since` | controller/ConfigController.java | 3h
- [ ] 12.5.6 | Write the tests: delta returns only entries newer than `since`; removed entries appear in `removed`; signature verifies with the public key; a tampered `added` entry makes verification fail | ConfigControllerIT, SignatureServiceTest | 3h
- [ ] 12.5.7 | Add the endpoint to docs/04-API-SPEC.md and to the ApiContractIT expectations | docs/04-API-SPEC.md, ApiContractIT.java | 1h
- [ ] 12.5.v | Verify gate: delta returns only new entries and the tampered signature is rejected | `mvn verify` green; curl `localhost:18080/api/v1/config/blocklist?since=0` then `?since=<latest>` outputs saved | 1h

## 12.6 [BE] Light endpoint `POST /scan/domain`
Goal: scan a bare domain for the VPN path without logging it next to a user.
Depends on: 10.9
- [ ] 12.6.1 | Add request record `DomainScanRequest(@Pattern domain)` accepting only a hostname (no scheme, path, query, spaces; max 253 chars) and response `DomainScanResponse(verdict, score, reasonCodes, impersonatedBrand, officialUrl)` | dto/Dtos.java | 1h
- [ ] 12.6.2 | Add `UrlScanService.scanDomain(domain)` that runs only the fast layers (blocklist, look-alike, cached RDAP if present); no page fetch; target under 100 ms | UrlScanService.java | 3h
- [ ] 12.6.3 | Add `POST /api/v1/scan/domain` to ScanController; do not call `threats.record` and do not put the domain in any log line (decide and write the rule: the app keeps its own history) | ScanController.java | 2h
- [ ] 12.6.4 | Write the contract test: valid domain 200 with shape, `https://x.com/a?b=1` gives 400, empty gives 400, no token gives 401 | ApiFlowIT new tests | 2h
- [ ] 12.6.5 | Write the log test: attach a Logback ListAppender, scan `fleepkart.com` for a logged-in user, assert no log event contains the domain or the user id together | NoDomainLoggingTest | 3h
- [ ] 12.6.6 | Add the endpoint to docs/04-API-SPEC.md and run the log grep by hand on a real container run (docker logs) | docs/04-API-SPEC.md, grep command and empty result in docs/privacy/log-audit.md | 2h
- [ ] 12.6.v | Verify gate: contract test passes and log grep finds no domain linked to a user | `mvn verify` green; `docker logs <container> 2>&1 findstr fleepkart` is empty | 1h

## 12.7 [FE] Privacy controls and local-only mode
Goal: users can prove only the domain leaves the phone, and can turn server checks off.
Depends on: 12.4
- [ ] 12.7.1 | Create `settings_screen.dart` (if 9.4 has not created it, reuse that one) with a "Local-only mode" switch and a plain explanation of both modes | mobile/lib/screens/settings_screen.dart | 2h
- [ ] 12.7.2 | Store the flag locally and make it readable from Kotlin DomainGuard (shared preferences file) and Dart ProtectionEngine | mobile/lib/core/settings_store.dart, DomainGuard.kt | 3h
- [ ] 12.7.3 | In local-only mode skip every server scan call (domain and link scans) and use only the hashed blocklist; show a small label "Local only" on the dashboard | DomainGuard.kt, protection_engine.dart `_handleLink`, dashboard tile | 3h
- [ ] 12.7.4 | Add a debug counter of server scan calls shown in Settings (debug builds) | Settings entry "Server checks sent: N" | 1h
- [ ] 12.7.5 | Write unit tests: local-only on makes zero calls (fake HTTP client), local-only off makes one call per unknown domain | mobile/test/local_only_test.dart and a Kotlin test for DomainGuard | 3h
- [ ] 12.7.6 | Capture what leaves the phone: run the phone through a PC proxy or use a free packet-capture app (verify which one you can use) and note every request body of the scan endpoints | docs/privacy/network-capture.md with the captured bodies (only `{"domain":"..."}`) | 3h
- [ ] 12.7.v | Verify gate: capture shows only the domain, local-only mode shows zero scan calls | Capture notes plus the debug counter at 0 after browsing 10 sites with local-only on; backend log of 0 scan requests | 1h

## 12.8 [FE] Per-app traffic accounting
Goal: a "Top apps" screen with real byte counts and the domains each app contacted.
Depends on: 12.2
- [ ] 12.8.1 | Verify how to get bytes per app: compare counting packets in the tunnel (needs full tunnel) with `NetworkStatsManager` (needs Usage Access) or `TrafficStats` per UID; try each on one phone and note accuracy and permission cost | docs/vpn-design.md section "Byte counts" with the choice | 3h
- [ ] 12.8.2 | Design the local SQLite schema: per day, package, bytes_up, bytes_down, plus a table of package, domain, query_count, last_seen; indexes for the 30-day queries | mobile/android-native/kotlin/TrafficStore.kt (schema part) | 2h
- [ ] 12.8.3 | Record domain counts from DNS queries (12.2) into TrafficStore (batched writes every few seconds, not per packet) | TrafficStore.kt, FraudVpnService.kt | 3h
- [ ] 12.8.4 | Record bytes with the method chosen in 12.8.1 (poll hourly and on screen open) and handle app uninstall and UID reuse | TrafficCollector.kt | 4h
- [ ] 12.8.5 | Add the 30-day cleanup (daily, on service start) and unit tests for insert, aggregate and purge | TrafficStore.kt, TrafficStoreTest.kt | 3h
- [ ] 12.8.6 | Expose `topApps(days)` and `domainsFor(package, days)` through MethodChannel and native_bridge.dart | MainActivity.kt, native_bridge.dart | 2h
- [ ] 12.8.7 | Build the "Top apps" screen: app name, icon, up and down, number of domains, tap to see domains | mobile/lib/screens/top_apps_screen.dart | 4h
- [ ] 12.8.v | Verify gate: numbers within 10 percent of Android data usage | Screenshots of our screen and Settings > Network > App data usage for 3 apps after 1 day of use, differences calculated in docs/vpn-design.md | 2h

## 12.9 [FE] Battery and stability: always-on, reboot, network switch, 24 h soak
Goal: the VPN stays up through reboots and network changes without draining the battery.
Depends on: 12.4
- [ ] 12.9.1 | Support Always-on VPN: make sure the service is declared so the system can start it, handle `onStartCommand` with a null intent, and load saved settings | FraudVpnService.kt, manifest | 3h
- [ ] 12.9.2 | Add a boot receiver (`BOOT_COMPLETED`) that restarts the VPN only when the user had it on and consent was already given (VpnService.prepare returns null) | BootReceiver.kt, manifest snippet | 2h
- [ ] 12.9.3 | Register a default network callback and call `setUnderlyingNetworks` so Wi-Fi to mobile data switches keep connectivity; re-create upstream sockets on change | FraudVpnService.kt, DnsProxy.kt | 4h
- [ ] 12.9.4 | Handle `onRevoke`, service kill and low memory: stop cleanly, clear notification, report state to Flutter | FraudVpnService.kt | 2h
- [ ] 12.9.5 | Prepare a soak test plan: scripted checks every hour (browse 5 sites, record crash count); decide how to read battery use (Android Settings > Battery usage, or adb batterystats if adb is available from a Docker image or a local install) | docs/vpn-soak-test.md | 2h
- [ ] 12.9.6 | Run the manual switch tests: Wi-Fi to mobile, airplane mode on and off, reboot, 5 minutes in a lift or weak signal | Table of results in docs/vpn-soak-test.md | 3h
- [ ] 12.9.7 | Run the 24 h soak with the VPN on and normal use; record crashes and battery percentage | Filled soak table with battery screenshot | 2h
- [ ] 12.9.v | Verify gate: 24 h without crash, battery under 3 percent per day | Battery screenshot and crash count in docs/vpn-soak-test.md | 1h

## 12.10 [FE] Conflict handling: another VPN, Private DNS, VPN revoked
Goal: each VPN problem shows a clear screen telling the user what to do.
Depends on: 12.1
- [ ] 12.10.1 | List the states and the message for each: OFF, ON, ANOTHER_VPN_ACTIVE, PRIVATE_DNS_ON, REVOKED_BY_USER, PERMISSION_DENIED, STARTING_FAILED; write the Hindi text too | docs/vpn-states.md | 2h
- [ ] 12.10.2 | Detect another active VPN (network capability TRANSPORT_VPN not owned by us) and detect Private DNS (verify how to read the `private_dns_mode` setting and whether it is readable on Android 9-14) | mobile/android-native/kotlin/VpnStateChecker.kt | 3h
- [ ] 12.10.3 | Send state changes from Kotlin to Flutter through an EventChannel (include revoked) | VpnStateChecker.kt, native_bridge.dart stream | 3h
- [ ] 12.10.4 | Build `vpn_state_screen.dart` showing the message and a button for each state (open VPN settings, open Private DNS settings, start again) | mobile/lib/screens/vpn_state_screen.dart | 4h
- [ ] 12.10.5 | Show the state on the dashboard tile and open the state screen from it | dashboard_screen.dart | 2h
- [ ] 12.10.6 | Write widget tests for each state (message and button) | mobile/test/vpn_state_screen_test.dart | 3h
- [ ] 12.10.v | Verify gate: each state tested by hand shows the right message | Manual checklist in docs/vpn-states.md with a screenshot per state (use a second VPN app and Private DNS setting to cause them) | 2h

## 12.11 [FE] Play policy: VpnService declaration and prominent in-app disclosure
Goal: policy text that matches the app and is checked against the current Google Play rules.
Depends on: 12.1
- [ ] 12.11.1 | Verify the current Google Play VpnService policy and declaration form (what is allowed, what must be disclosed, what data use is forbidden); copy the key rules with links and the date | docs/play-vpn-declaration.md section "Policy checked" | 2h
- [ ] 12.11.2 | Write the declaration answers: core functionality is local protection, what data is handled (domain names, byte counts), that nothing is sold or shared, encryption in transit | docs/play-vpn-declaration.md section "Declaration text" | 2h
- [ ] 12.11.3 | Make the in-app disclosure text from the consent screen (12.1.5) match the declaration word for word where the policy demands it, shown before the system VPN dialog | vpn_consent_screen.dart, text file shared with docs | 2h
- [ ] 12.11.4 | Add a short VPN section to the privacy policy (links to 9.4) and the Data safety answers | docs/privacy/privacy-policy.md, policy-hi.md | 2h
- [ ] 12.11.5 | Prepare a 30-60 s demo video storyboard showing the disclosure, the system VPN dialog and a blocked fake site | docs/play-vpn-declaration.md section "Video" | 1h
- [ ] 12.11.6 | Review the text against the checked policy line by line with a second person (a non-technical reader for the disclosure) | Review table with reviewer and date | 2h
- [ ] 12.11.v | Verify gate: text reviewed against current policy | docs/play-vpn-declaration.md with the review table complete and the policy check date within the last 30 days | 1h
