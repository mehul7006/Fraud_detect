# Phase 10 - Look-alike website engine: results

Date: 2026-11-23. All numbers below come from tests in the repository (re-run with `mvn verify`).

## What a scan does now
1. **Address** (offline, instant): heuristics + look-alike brand match against 252 brands (`seed/brands.csv` ->
   `brands` table). Public Suffix List for the real registrable domain; Tranco top-100k allowlist.
2. **Domain age** (RDAP, IANA bootstrap): new domains score higher.
3. **Certificate + redirects**: wrong-host / untrusted / brand-new certificate; up to 5 redirects; the end of the chain is
   judged again (a short link to `fleepkart.com` is caught).
4. **The page** (jsoup, no JavaScript, 1 MB, no query string, no cookies): brand copied on a non-official site, secrets
   asked, form posting elsewhere, scam wording (English + Hinglish).

Layers 2-4 and Safe Browsing run in parallel inside a **3 s budget**; a slow layer is left out, never the scan.
Official sites and the top-100k are never fetched. Every fetch goes through the SSRF guard.

## Accuracy (10.12)
`UrlAccuracyTest` on `backend/src/test/resources/url-corpus.csv`, offline (address layer only):

| Set | Links | Warned | Result |
|---|---|---|---|
| Indian scam-style links (hand-written from public advisories) | 134 | 132 (126 MALICIOUS) | **recall 98.5 %** |
| Legitimate (official, well-known, 80 random less-known real sites) | 188 | 0 | **precision 100 %** |

Missed: `http://indigo-refund.online/` ("indigo" alone is an ordinary word, so only `goindigo` is a keyword) and one
short link before the alias rule was added (now caught). Honest limit: the scam set was written by us, so real-world
recall will be lower; links on random free-hosting names are only caught by the page check (layer 4).

## False positives on real sites (10.4)
Look-alike engine run over **200,000 real sites outside the allowlist** (Tranco ranks 100,001-300,000):
**161 flagged = 0.08 %**. Most are genuine typo-squats or brand-owned spare domains (`gmial.com`, `facebok.com`,
`gooogle.com`, `discord.gift`, `whatsapp-plus.me`, `instagran.com`). Tuning steps that got there from 893 (0.47 %):
allowlist 10k -> 100k; typo checks only on a brand's real domain name; first letter must stay; 2-letter typos only when
the name still sounds the same; short (5-7 letter) names: typo = SUSPICIOUS only (`agora` vs `agoda`); tiny glue words
(`co`+`in`) no longer count as bait.

## Tests
| Area | Test | Cases |
|---|---|---|
| 10.2 normaliser | `DomainNormalizerTest` | 30 domains + IDN + IP |
| 10.1/10.3/10.4 matcher | `LookalikeMatcherTest` | 102 |
| 10.5 RDAP, 10.10 budget + cache | `DomainIntelAndBudgetTest` | 6 (stub registry + slow servers: answer <=3 s; 2nd scan <50 ms) |
| 10.6 TLS + redirects | `TlsRedirectTest` | 5 (local HTTPS with wrong-host / self-signed certs, 4-hop and 7-hop chains, 1 MB cap, deadline) |
| SSRF guard | `AddressGuardTest` | 39 (private / metadata / CGNAT / IPv6 / mapped / rebinding / ports / redirect into LAN) |
| 10.7/10.8 page | `PageCheckTest` | 7 (fake Flipkart fixture -> MALICIOUS "Copies the Flipkart page"; real shop -> nothing) |
| 10.9 response | `ScanServicesTest`, `Phase10IT` | codes, brand, official URL, blocklist on sub-domains; brands table seeded |
| 10.12 accuracy | `UrlAccuracyTest` | 322 links |
| 10.11 app screen | `fake_site_screen_test.dart`, `accessibility_test.dart` | 5 + 3 (EN/HI, 200 % text, TalkBack, 48 dp) |

## Data shipped (refresh: `tools/lookalike/update_popular.sh`)
- Mozilla Public Suffix List (MPL-2.0) - `backend/src/main/resources/psl/`
- Tranco top 100k (cite: Le Pochat et al., NDSS 2019) - `seed/popular-domains.txt`
- IANA RDAP bootstrap (fallback copy) - `seed/rdap-dns.json`

## Open follow-ups
- **10.8b** fill `brands.favicon_hash` for the top brands (the check is built and tested; no hashes stored yet).
- Admin API for brands (today: edit `seed/brands.csv` or the table; restart reloads).
