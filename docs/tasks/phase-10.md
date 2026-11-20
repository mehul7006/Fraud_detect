# Phase 10 – Look-alike website engine
## 10.1 [BE] Brand registry: table `brands` seeded with at least 200 Indian + global brands
Goal: a database of brands with official domains so a domain can be looked up and compared.
Depends on: none
- [ ] 10.1.1 | Write the Indian brand list (at least 110: Flipkart, Amazon India, Myntra, Meesho, SBI, HDFC, ICICI, Axis, PNB, PhonePe, Paytm, Jio, Airtel, Vi, India Post, IRCTC, UIDAI, Income Tax, EPFO, DigiLocker, Swiggy, Zomato and more) with category and official domains; open each official domain in a browser to confirm it | backend/src/main/resources/brands-seed.csv (columns name, category, domains separated by semicolons, keywords separated by semicolons) | 4h
- [ ] 10.1.2 | Add the global brands (at least 90: Google, Microsoft, Apple, WhatsApp, Telegram, Instagram, Facebook, X, Netflix, PayPal, DHL, FedEx, Binance and more) to the same CSV | brands-seed.csv with at least 200 data rows | 3h
- [ ] 10.1.3 | Design the schema: `brands(id, name, category, keywords, favicon_hash)` and `brand_domains(brand_id, domain UNIQUE)` with index on domain | backend/src/main/resources/db/migration/V2__brands.sql (tables only) | 2h
- [ ] 10.1.4 | Generate the INSERT statements from the CSV with a small Python script run in a python:3.12 Docker container and append them to the migration (or a V2_1 file); check the Flyway version number is free first | backend/scripts/gen_brands_sql.py plus generated SQL | 2h
- [ ] 10.1.5 | Add `Brand` entity and `BrandRepository` (follow the style in repo/Repositories.java) with `findByOfficialDomain(String registrableDomain)` and `findAll()` | domain/Brand.java, BrandRepository in repo/Repositories.java | 2h
- [ ] 10.1.6 | Write Testcontainers test: at least 200 brands, every brand has at least 1 domain, lookup of `flipkart.com` returns Flipkart, lookup of `fleepkart.com` returns nothing | backend/src/test/java/com/fraudshield/BrandRepositoryIT.java passes | 2h
- [ ] 10.1.v | Verify gate: migration applies, 200+ brands, lookup works | `docker run --rm -v "E:/frud detection/backend":/app -v m2cache:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -w /app maven:3.9-eclipse-temurin-21 mvn verify` shows BrandRepositoryIT green | 1h

## 10.2 [BE] Domain normaliser: registrable domain (public-suffix list), punycode/IDN decode, strip `www`, detect subdomain tricks
Goal: turn any URL host into a clean registrable domain and flag subdomain tricks.
Depends on: none
- [ ] 10.2.1 | Verify the public suffix source: check whether Guava `InternetDomainName` is acceptable (adds a dependency) or bundle publicsuffix.org list (verify its licence); pick one | Decision line at top of DomainNormalizer.java, dependency added in pom.xml if Guava | 1h
- [ ] 10.2.2 | Create `DomainNormalizer` with `parse(String urlOrHost)` returning a record `NormalizedDomain(host, registrable, subdomain, tld, wasPunycode, unicodeHost)`; handle missing scheme, port, userinfo, trailing dot, uppercase | backend/src/main/java/com/fraudshield/service/DomainNormalizer.java | 3h
- [ ] 10.2.3 | Add public-suffix handling for multi-part suffixes (`co.in`, `gov.in`, `co.uk`, `com.au`) so `shop.flipkart.co.in` gives `flipkart.co.in` | Same class; tests for the 4 suffixes | 2h
- [ ] 10.2.4 | Add IDN decode (`xn--` to Unicode) and strip `www`; set `wasPunycode` | Same class | 2h
- [ ] 10.2.5 | Add subdomain-trick detector: another registrable-looking name inside the subdomain (`login.flipkart.com.evil.xyz`), brand in subdomain, very deep subdomains; return flags | `List<String> tricks()` in NormalizedDomain | 2h
- [ ] 10.2.6 | Write the 30-case table test (CSV rows input to registrable): include `login.flipkart.com.evil.xyz` to `evil.xyz`, `www.flipkart.com` to `flipkart.com`, `FLIPKART.COM.` to `flipkart.com`, `http://user@fleepkart.com:8080/x` to `fleepkart.com`, `xn--flipkart-9ya.com`, `shop.flipkart.co.in`, `1.2.3.4` | backend/src/test/java/com/fraudshield/service/DomainNormalizerTest.java (parameterized, 30 rows) | 3h
- [ ] 10.2.v | Verify gate: 30-case unit test passes | `docker run --rm -v "E:/frud detection/backend":/app -v m2cache:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q test -Dtest=DomainNormalizerTest` shows 30 passing | 1h

## 10.3 [BE] Look-alike matcher (typos, homoglyphs, brand+keyword, subdomain, wrong TLD)
Goal: tell which real brand a domain imitates, with a score, and keep official domains SAFE.
Depends on: 10.1, 10.2
- [ ] 10.3.1 | Create result types: `LookalikeResult(brand, officialDomain, officialUrl, matchType, score)` and enum `MatchType` (TYPO, HOMOGLYPH, KEYBOARD, REPEATED_OR_MISSING, BRAND_KEYWORD, BRAND_IN_SUBDOMAIN, BRAND_IN_PATH, WRONG_TLD) | service/LookalikeResult.java, MatchType | 1h
- [ ] 10.3.2 | Implement Damerau-Levenshtein distance (with transposition) and its unit test; early exit when length difference is above 2 | service/EditDistance.java, EditDistanceTest (`flipkart` vs `fleepkart` is 2, `flipkart` vs `flipkrat` is 1) | 2h
- [ ] 10.3.3 | Implement the confusable "skeleton": lowercase, map `rn` to `m`, `vv` to `w`, `0` to `o`, `1` and `I` to `l`, `5` to `s`, Cyrillic and Greek lookalikes (a, e, o, p, c, x) to Latin; compare skeletons with brand names | service/ConfusableMap.java, ConfusableMapTest (`f1ipkart` gives `flipkart`, `flipkaгt` with Cyrillic г is handled or listed as known gap) | 3h
- [ ] 10.3.4 | Implement keyboard-neighbour typo check (QWERTY adjacency) and repeated or missing letter check (`flipkarrt`, `flipkrt`, `fliipkart`) | LookalikeService methods plus tests | 3h
- [ ] 10.3.5 | Implement brand-plus-keyword check: label contains brand name plus words like sale, offer, login, kyc, support, refund, deals (`flipkart-sale.xyz`, `flipkartoffers.in`), and wrong-TLD check (`flipkart.xyz`, `flipkart.top`) | LookalikeService methods plus tests | 3h
- [ ] 10.3.6 | Implement brand-in-subdomain and brand-in-path checks using DomainNormalizer (`flipkart.com.verify-login.xyz`, `evil.xyz/flipkart/login`) and the official-domain short circuit (official registrable domain gives SAFE and returns no result) | LookalikeService.match(String url) complete | 3h
- [ ] 10.3.7 | Set scores per match type in a config class (typo 85, homoglyph 90, brand+keyword 85, subdomain 80, wrong TLD 75, path 60); every positive must reach 80 or more, path-only stays lower | service/LookalikeScores.java; table written in docs/10-lookalike-rules.md | 2h
- [ ] 10.3.8 | Write the parameterized scenario test with exact URLs. Expect impersonates Flipkart with score 80 or more: `fleepkart.com`, `flipkarrt.com`, `f1ipkart.com`, `flipkart-sale.xyz`, `https://flipkart.com.login-verify.xyz/offer`. Expect SAFE (no match): `flipkart.com`, `www.flipkart.com`, `flipkart.in`, `https://www.flipkart.com/cart` | backend/src/test/java/com/fraudshield/service/LookalikeServiceTest.java | 3h
- [ ] 10.3.v | Verify gate: all scenario URLs behave as listed | `docker run --rm -v "E:/frud detection/backend":/app -v m2cache:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q test -Dtest=LookalikeServiceTest` green; paste the output | 1h

## 10.4 [BE] Popular-domain allowlist (Tranco top 10k) to prevent false positives
Goal: well-known unrelated sites such as flickr.com are never called look-alikes.
Depends on: 10.3
- [ ] 10.4.1 | Verify the Tranco list terms and how to download it; record version or date used | docs/10-popular-domains-source.md | 1h
- [ ] 10.4.2 | Download the list in a scratch Docker container, keep the top 10,000, and also add the Indian popular sites you know are missing; save one domain per line | backend/src/main/resources/popular-domains.txt | 2h
- [ ] 10.4.3 | Remove from the file any domain that is itself a look-alike of a seeded brand (run the matcher over the file and review each hit by hand) | Review list in docs/10-popular-domains-source.md | 2h
- [ ] 10.4.4 | Create `PopularDomains` bean loading the file into a HashSet and add the check inside LookalikeService: popular domain returns no look-alike result, unless it is on a brand's own list | service/PopularDomains.java, change in LookalikeService | 2h
- [ ] 10.4.5 | Add tests: `flickr.com`, `flipboard.com`, `amazonaws.com`, `flipkart-sale.xyz` (still flagged, not popular), plus `fleepkart.com` must not be in the file | LookalikeServiceTest new cases and PopularDomainsTest | 2h
- [ ] 10.4.6 | Run a false-positive sweep: matcher over all 10,000 popular domains must flag 0; list and fix any hit | Sweep test `popularDomainsNeverFlagged` passes | 2h
- [ ] 10.4.v | Verify gate: the three named domains are not flagged | `mvn -q test -Dtest=LookalikeServiceTest,PopularDomainsTest` (same docker command as 10.3.v) green | 1h

## 10.5 [BE] Domain intelligence via RDAP: age, registrar, country; cached; timeout-safe
Goal: add a score for newly registered domains without ever breaking a scan.
Depends on: 10.2
- [ ] 10.5.1 | Verify how RDAP lookups work for .com, .in, .xyz (IANA bootstrap file at data.iana.org/rdap/dns.json, registry servers, rate limits) and which fields hold the registration date | docs/10-rdap-notes.md with real sample JSON for 2 domains | 2h
- [ ] 10.5.2 | Create `DomainIntel` record (registeredAt, ageDays, registrar, country, available) and `DomainIntelService` interface | service/DomainIntel.java, DomainIntelService.java | 1h
- [ ] 10.5.3 | Implement RDAP client with java.net.http.HttpClient: bootstrap lookup, 2 s connect and read timeout, parse the registration event, return `available=false` on any failure | service/RdapDomainIntelService.java | 3h
- [ ] 10.5.4 | Add scoring rule: age under 7 days +35, under 30 days +25, under 180 days +10, unknown adds 0; reason codes NEW_DOMAIN and VERY_NEW_DOMAIN | service/DomainAgeScoring.java plus unit test of boundaries | 2h
- [ ] 10.5.5 | Add a cache (in-memory map with expiry for now; replaced by Caffeine in 10.10) keyed by registrable domain, 24 h | Cache inside RdapDomainIntelService | 1h
- [ ] 10.5.6 | Write the stub-server test with the JDK `com.sun.net.httpserver.HttpServer`: young domain returns +25, old domain returns 0, stub that sleeps 5 s makes the result `available=false` within 2.5 s, stub 500 error degrades | RdapDomainIntelServiceTest (4 cases) | 3h
- [ ] 10.5.v | Verify gate: new domain adds score, timeout degrades | `mvn -q test -Dtest=RdapDomainIntelServiceTest,DomainAgeScoringTest` green | 1h

## 10.6 [BE] TLS + redirect facts with SSRF guard
Goal: read certificate and redirect facts safely, never touching private networks.
Depends on: 10.2
- [ ] 10.6.1 | Create `SsrfGuard`: resolve the host once, refuse loopback, private (10.x, 172.16-31.x, 192.168.x), link-local (169.254.x including cloud metadata), 0.0.0.0, multicast, IPv6 loopback and unique-local, and IPv4-mapped IPv6; return the vetted IP to connect to; add property `fraudshield.fetch.allow-private` (default false, true only in tests) | service/SsrfGuard.java, application.yml property | 3h
- [ ] 10.6.2 | Write SsrfGuard tests: `localhost`, `127.0.0.1`, `10.0.0.5`, `192.168.1.1`, `169.254.169.254`, `[::1]`, `0.0.0.0`, decimal IP `2130706433`, and `public IP literal` allowed | SsrfGuardTest | 2h
- [ ] 10.6.3 | Implement `TlsRedirectService.certificateFacts(host)`: open TLS socket to the vetted IP with a certificate-recording trust manager (no data sent), return issuer, notBefore age, SAN list, and hostname match flag | service/TlsRedirectService.java, `CertFacts` record | 3h
- [ ] 10.6.4 | Implement manual redirect follower: no automatic redirects, max 5 hops, each hop re-checked by SsrfGuard, record chain and whether final registrable domain differs from the first | `RedirectChain` record, method `followRedirects(url)` | 3h
- [ ] 10.6.5 | Create test fixtures: local HTTPS server with a self-signed certificate for the wrong name (keystore made with keytool in the Maven Docker image) and a plain local server doing a 4-hop redirect | src/test/resources/test-mismatch.p12, test helper class LocalTestServers | 3h
- [ ] 10.6.6 | Write the tests using allow-private=true: mismatch certificate detected, 4-hop chain detected, 6-hop chain stops at 5, and with allow-private=false the same local URLs are refused | TlsRedirectServiceTest (4 cases) | 3h
- [ ] 10.6.7 | Add scoring and reason codes: CERT_HOST_MISMATCH +30, CERT_VERY_NEW +15 (under 7 days), REDIRECT_TO_OTHER_DOMAIN +20, REDIRECT_CHAIN_LONG +10 | service/TlsRedirectScoring.java with test | 2h
- [ ] 10.6.v | Verify gate: mismatch cert and 4-hop redirect detected; localhost and private IPs refused | `mvn -q test -Dtest=SsrfGuardTest,TlsRedirectServiceTest` green | 1h

## 10.7 [BE] Safe page fetcher: strict timeout, 1 MB cap, no cookies, no JavaScript, SSRF-safe, feature extraction
Goal: fetch a suspicious page safely and turn it into a small set of features.
Depends on: 10.6
- [ ] 10.7.1 | Verify and add jsoup to pom.xml (check current stable version and licence) | pom.xml dependency, noted in docs/10-page-fetcher.md | 1h
- [ ] 10.7.2 | Define the `PageFeatures` record: title, faviconUrl, faviconHash, formCount, formActionDomains, hasPasswordField, hasOtpField, hasCardField, externalScriptDomains, metaRefreshUrl, visibleTextSample (first 300 chars, used only for rules, never logged) | service/PageFeatures.java | 1h
- [ ] 10.7.3 | Implement `PageFetcher.fetch(url)`: HttpClient, GET only, 3 s timeout, no cookie handler, fixed generic User-Agent, stream and stop at 1 MB, accept only text/html, uses SsrfGuard and the redirect follower from 10.6 | service/PageFetcher.java | 4h
- [ ] 10.7.4 | Implement `PageFeatureExtractor` with jsoup: title, favicon link, forms and action domains, input types (password, tel, number with card-like names, otp), external scripts, meta refresh | service/PageFeatureExtractor.java | 3h
- [ ] 10.7.5 | Create HTML fixtures: normal blog page, login page posting to the same domain, fake-bank page posting to another domain, page with meta refresh, page with 2 MB body | src/test/resources/fixtures/*.html | 2h
- [ ] 10.7.6 | Write extractor tests over the fixtures and fetcher tests with a local server (cap at 1 MB, timeout, non-HTML refused, cookies not stored) | PageFeatureExtractorTest, PageFetcherTest | 3h
- [ ] 10.7.7 | Write the SSRF tests for the fetcher: `http://169.254.169.254/`, `http://10.0.0.1/`, `http://localhost:8080/`, a redirect from a public stub to `http://127.0.0.1/`, and a DNS-rebinding stub (first lookup public, second lookup private) that must be refused because the IP is pinned | PageFetcherSsrfTest (5 cases) | 3h
- [ ] 10.7.v | Verify gate: fixtures parsed and all SSRF cases refused | `mvn -q test -Dtest=PageFeatureExtractorTest,PageFetcherTest,PageFetcherSsrfTest` green | 1h

## 10.8 [BE] Page-vs-brand comparison
Goal: detect a page that copies a known brand on a domain that is not the brand's.
Depends on: 10.7, 10.3
- [ ] 10.8.1 | Write a small command-line runner (test-scope main class) that fetches the favicon of each seeded brand's official site through PageFetcher and prints its hash; run it for the top 40 brands (verify the favicons load) | src/test/java/.../tools/FaviconHashTool.java; output CSV | 3h
- [ ] 10.8.2 | Add the hashes to `brands.favicon_hash` through a new Flyway migration (next free version number) | db/migration/V?__brand_favicons.sql | 1h
- [ ] 10.8.3 | Define wording rules (English and common Hindi/Hinglish): verify KYC, account blocked, claim prize, update PAN, refund, urgent action, enter OTP; keep them in a resource file | src/main/resources/scam-wording.txt | 2h
- [ ] 10.8.4 | Implement `PageBrandCheck.check(PageFeatures, domain)`: brand name in title on a non-official domain, favicon hash equals a brand favicon on a non-official domain, password or card field with form posting to another registrable domain, scam wording | service/PageBrandCheck.java returning reason codes (BRAND_NAME_ON_OTHER_DOMAIN, FAVICON_COPY, FORM_TO_OTHER_DOMAIN, SCAM_WORDING) | 4h
- [ ] 10.8.5 | Create the fixture `fake-flipkart.html` (Flipkart title, copied favicon reference, login form posting to another domain, "verify KYC") and the official-like fixture that must not trigger | src/test/resources/fixtures/fake-flipkart.html, real-like-flipkart.html | 2h
- [ ] 10.8.6 | Write tests: fake fixture on `fleepkart.com` gives MALICIOUS-level score with reason "copies Flipkart page"; same page content on `flipkart.com` gives 0 | PageBrandCheckTest | 2h
- [ ] 10.8.v | Verify gate: fixture fake Flipkart gives MALICIOUS with the reason | `mvn -q test -Dtest=PageBrandCheckTest` green, output shows reason text | 1h

## 10.9 [BE] Risk combiner v2 in `UrlScanService` with reason codes
Goal: one layered score with machine-readable reason codes and brand fields in the response.
Depends on: 10.3, 10.5, 10.6, 10.8
- [ ] 10.9.1 | Create enum `ReasonCode` (LOOKALIKE_BRAND, NEW_DOMAIN, VERY_NEW_DOMAIN, CERT_HOST_MISMATCH, REDIRECT_TO_OTHER_DOMAIN, FORM_TO_OTHER_DOMAIN, FAVICON_COPY, SCAM_WORDING, plus one code for each existing rule in UrlScanService such as IP_HOST, PUNYCODE, SHORTENER, RISKY_TLD, BLOCKLISTED, APK_LINK) | service/ReasonCode.java | 2h
- [ ] 10.9.2 | Extend `ScanResponse` in dto/Dtos.java with `reasonCodes`, `impersonatedBrand`, `officialUrl` (keep the old 3 fields; new ones may be empty); update every constructor call | Dtos.java, UrlScanService.java compile | 2h
- [ ] 10.9.3 | Refactor `UrlScanService.scan` into layers: layer 1 existing rules (keep text and scores, attach reason codes), replace the hard-coded `BRANDS` set and `registrable()` with DomainNormalizer and BrandRepository | UrlScanService.java; existing ScanServicesTest still passes | 3h
- [ ] 10.9.4 | Add layer 2 (look-alike match to score, brand and officialUrl), layer 3 (RDAP age and TLS/redirect facts), layer 4 (page fetch plus PageBrandCheck); combine with a cap of 100, and a floor of 85 when look-alike is 80+ | UrlScanService.java, class ScanLayers or private methods | 4h
- [ ] 10.9.5 | Make the page fetch run only when the score after layers 1-3 is between 25 and 84 (saves time and abuse), and never for blocklisted or official domains | UrlScanService.java with comments | 2h
- [ ] 10.9.6 | Update the API: ScanController returns the new fields, threat detail uses the reason codes; update docs/04-API-SPEC.md rows for `/scan/url` and ApiContractIT expectations | ScanController.java, docs/04-API-SPEC.md, ApiContractIT.java | 2h
- [ ] 10.9.7 | Add API tests through ApiFlowIT on port-free Testcontainers: POST `/scan/url` with `fleepkart.com` returns verdict MALICIOUS, `reasonCodes` contains LOOKALIKE_BRAND, `impersonatedBrand` Flipkart, `officialUrl` `https://www.flipkart.com`; `flipkart.com` returns SAFE; google.com SAFE(0); example-phish.test MALICIOUS(100) | ApiFlowIT new tests plus all older 1.4 tests unchanged | 3h
- [ ] 10.9.v | Verify gate: response shape matches and all 1.4 tests pass | `docker run --rm -v "E:/frud detection/backend":/app -v m2cache:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -w /app maven:3.9-eclipse-temurin-21 mvn verify` green; also run the container on port 18080 and `curl -s -X POST localhost:18080/api/v1/scan/url -H "Authorization: Bearer <jwt>" -H "Content-Type: application/json" -d "{\"url\":\"fleepkart.com\"}"` output saved | 2h

## 10.10 [BE] Time budget + cache: whole scan within 3 s, layers degrade independently, 24 h host cache
Goal: scans are always fast and never fail because one layer is slow.
Depends on: 10.9
- [ ] 10.10.1 | Add spring-boot-starter-cache and Caffeine (check whether 4.2 already added them) and a `CacheConfig` with a 24 h, max 50k entries cache named `hostVerdicts` | pom.xml, config/CacheConfig.java | 2h
- [ ] 10.10.2 | Add `fraudshield.scan.*` settings in application.yml: total budget 3000 ms, rdap 1200 ms, tls 1200 ms, page 1500 ms | application.yml, config class ScanProperties | 1h
- [ ] 10.10.3 | Run the slow layers (RDAP, TLS, redirect, page fetch) in parallel on virtual threads with `orTimeout`; any timeout or exception returns "layer unavailable" and the verdict uses the other layers | UrlScanService.java, class LayerRunner | 4h
- [ ] 10.10.4 | Add reason code `PARTIAL_CHECK` (info only, no score) when a layer timed out, and make sure partial results are cached for only 10 minutes, full ones for 24 h | UrlScanService.java | 2h
- [ ] 10.10.5 | Cache by normalised registrable host plus path-free key; do not cache when the blocklist changes (evict host on blocklist insert) | CacheConfig and UrlScanService | 2h
- [ ] 10.10.6 | Write the slow-layer test with stub services that sleep 10 s: total time under 3.2 s and a verdict is returned; also one layer throwing an exception | UrlScanTimeBudgetTest | 3h
- [ ] 10.10.7 | Write the cache test: first scan of `fleepkart.com` then second scan under 50 ms (measure with System.nanoTime on a warm JVM) | UrlScanCacheTest | 2h
- [ ] 10.10.v | Verify gate: slow dependencies still answer within 3 s, repeat scan under 50 ms | `mvn -q test -Dtest=UrlScanTimeBudgetTest,UrlScanCacheTest` green with timings printed | 1h

## 10.11 [FE] Fake-site warning screen
Goal: a clear full screen saying "This looks like Flipkart but is NOT Flipkart" with a safe way out.
Depends on: 10.9
- [ ] 10.11.1 | Update `api_client.dart` to parse the new scan fields (reasonCodes, impersonatedBrand, officialUrl) into a `ScanResult` class | mobile/lib/core/api_client.dart, mobile/lib/core/scan_result.dart | 2h
- [ ] 10.11.2 | Write the letter-difference helper: compare the scanned domain label with the official label and return per-character flags (`fleepkart` vs `flipkart` marks `e`, `e`) | mobile/lib/core/text_diff.dart with test/text_diff_test.dart (3 pairs including `f1ipkart`, `flipkarrt`) | 2h
- [ ] 10.11.3 | Build `FakeSiteScreen` UI: red header, headline with brand name, two lines "You opened" and "Real site is", fake domain with highlighted letters, short reasons list | mobile/lib/screens/fake_site_screen.dart | 3h
- [ ] 10.11.4 | Add the buttons: **Go to real site** (opens `officialUrl`; add a `openUrl` method in MainActivity.kt and native_bridge.dart or use url_launcher after checking pubspec), **Close**, **Report** (for now sends a threat report through POST /threats and shows "Thanks"; switch to feedback endpoint after 11.8) | fake_site_screen.dart, native_bridge.dart, MainActivity.kt | 3h
- [ ] 10.11.5 | Wire it into `ProtectionEngine._handleLink`: when `impersonatedBrand` is set, queue the fake-site screen instead of the generic alert; critical, not dismissible by tap-outside | mobile/lib/core/protection_engine.dart, dashboard_screen.dart `_drain()` | 3h
- [ ] 10.11.6 | Write the widget test: shows brand and highlighted letters, tapping Go to real site calls the opener with the official URL (use a fake opener), Close pops the screen | mobile/test/fake_site_screen_test.dart | 3h
- [ ] 10.11.7 | Create a golden screenshot of the screen for `fleepkart.com` vs `flipkart.com` | mobile/test/goldens/fake_site_screen.png via `flutter test --update-goldens` | 1h
- [ ] 10.11.v | Verify gate: widget test and screenshot; button opens the official URL | `docker run --rm -v "E:/frud detection/mobile":/app -w /app ghcr.io/cirruslabs/flutter:stable sh -c "flutter analyze && flutter test"` green; golden attached; short phone recording of the button opening flipkart.com | 2h

## 10.12 [BE] Labelled test corpus (100 phishing + 100 legitimate) and accuracy report
Goal: measure precision and recall on realistic data and list every mistake.
Depends on: 10.9
- [ ] 10.12.1 | Verify licences and rules of the phishing feeds you will sample from (OpenPhish free feed, PhishTank, URLhaus) and write the source rules | docs/10-corpus-sources.md | 1h
- [ ] 10.12.2 | Collect at least 100 phishing URLs that target Indian brands or imitate big brands (sample from feeds, then add hand-made realistic look-alikes like `fleepkart.com`, `amaz0n-india.top`, `sbi-kyc-update.xyz`, `paytm-reward.click`); mark brand targeted; store host and path only, no live malware | backend/src/test/resources/url-corpus.csv (columns url, label, brand, source) with 100+ rows labelled phishing | 4h
- [ ] 10.12.3 | Collect at least 100 legitimate URLs: official brand pages with paths (`https://www.flipkart.com/mobiles`), popular Indian sites, close-but-legit names (`flickr.com`, `flipboard.com`, `amazonaws.com`, `paytm.com`) | url-corpus.csv with 100+ rows labelled legit | 3h
- [ ] 10.12.4 | Add a column `set` with dev (60 percent) and holdout (40 percent) so tuning on dev does not fool you; fix the split once | url-corpus.csv, note in docs/10-corpus-sources.md | 1h
- [ ] 10.12.5 | Write `AccuracyTest`: scan each corpus row with network layers stubbed off (rules and look-alike only, deterministic), compute precision and recall per set, write the report | backend/src/test/java/com/fraudshield/AccuracyTest.java, report written to target/accuracy-report.md | 3h
- [ ] 10.12.6 | Make the report list every false positive and false negative with reason codes; then tune scores (config only, no per-URL hacks) until targets are met on dev | target/accuracy-report.md sections FP and FN; tuning notes in docs/10-lookalike-rules.md | 4h
- [ ] 10.12.7 | Run on the holdout set once and save the final report in the docs folder | docs/reports/phase10-accuracy.md | 2h
- [ ] 10.12.v | Verify gate: precision 95 percent or more and recall 90 percent or more on holdout, every false positive listed | `mvn -q test -Dtest=AccuracyTest` green (assertions on both numbers) and docs/reports/phase10-accuracy.md | 1h
