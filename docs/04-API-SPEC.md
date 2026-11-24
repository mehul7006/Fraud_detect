# REST API v1  (base `/api/v1`, JSON, `Authorization: Bearer <jwt>` except /auth)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | /auth/register | `{email,password}` | `{token, refreshToken, expiresIn}` (8.2: access token 15 min) |
| POST | /auth/login | `{email,password}` | `{token, refreshToken, expiresIn}` |
| POST | /auth/refresh | `{refreshToken}` | new `{token, refreshToken, expiresIn}`; the old refresh token is used up. Re-using a used one = 401 and that whole login is revoked (theft) |
| POST | /auth/logout | – (Bearer) | 204; every refresh token revoked and every access token issued until now rejected (log out everywhere) |
| POST | /auth/background-session | – (Bearer) | an independent `{token, refreshToken, expiresIn}` for the background scanner (7.11), renewed by itself |
| POST | /devices | `{deviceId,model,osVersion,simHash,rooted,playProtectOn,remoteAppsInstalled[]}` | `{deviceId,cloneRiskScore,reasons[]}` |
| POST | /devices/heartbeat | same as above | same |
| POST | /scan/url | `{url,sourceApp}` | `{verdict,score,reasons[],reasonCodes[],impersonatedBrand,officialUrl,mlScore,explanation{en,hi,source}}` - `reasonCodes[i]` is the stable code of `reasons[i]` (see below); `impersonatedBrand`/`officialUrl` only when the link imitates a known brand (10.9). Answers within 3 s. `mlScore` 0-100 (11.4) is null for official / gov.in / popular sites or when the model is off; `explanation` (11.6) is null for SAFE, `source` = `template` or `ai`. |
| POST | /scan/url/feedback | `{url,kind:FALSE_POSITIVE\|FALSE_NEGATIVE,note?}` (host only is stored) | 204 (409 duplicate) |
| GET | /admin/blocklist | – (ADMIN) | list of `{id,kind,value,reason}` |
| POST | /admin/blocklist | `{kind:DOMAIN\|SHA256,value,reason?}` (ADMIN) | 201 (409 duplicate) |
| DELETE | /admin/blocklist/{id} | – (ADMIN) | 204 |
| POST | /admin/blocklist/import | multipart CSV `kind,value,reason`, max 5000 rows (ADMIN) | `{added,skipped,errors[]}` |
| GET | /admin/feedback?status=OPEN | – (ADMIN) | list of reports |
| GET | /admin/feedback/labels.tsv | – (ADMIN) | 11.8: reviewed reports as training rows `label<TAB>train<TAB>https://host/` (ADD_TO_BLOCKLIST = 1, ALLOW = 0) |
| POST | /admin/feedback/{id}/close | `{resolution:ADD_TO_BLOCKLIST\|ALLOW\|DISMISS}` (ADMIN) | closed report |
| POST | /scan/file | `{sha256,fileName,sizeBytes,mimeType,sourceApp,headerHex,flags?}` - flags: max 10 of `PDF_JS`, `PDF_LAUNCH`, `PDF_OPENACTION`, `OFFICE_MACRO` (names only, never content) | `{verdict,score,reasons[],action}` |
| POST | /scan/apps | `{apps:[{packageName,label,notifPerHour,hasOverlayPermission,hasSmsPermission,hasAccessibility,hasNotificationListener,hasFullScreenIntent,selfLaunchCount?,isSystem,certSha256?,installer?, requestedPermissions?[],grantedPermissions?[],targetSdk?,trackers?[],domainsContacted?[],appCategory?}],deviceId?}` - the 13.x fields are optional (permission names full or short, max 300; trackers = SDK names; domains max 300; appCategory = Android ApplicationInfo category name) | `{results:[{packageName,adAbuseScore,verdict,reasons[],newSinceLastScan,privacyScore,privacyReasons[],reasonCodes[],impersonatedBrand,explanation{en,hi,source}}]}` - verdict = worse of ad-abuse and privacy; privacy fields null when the app sent no 13.x data (older clients: unchanged 6.x answer). Known-GOOD / official apps proven genuine (Play or certificate) are never flagged. Codes: `FAKE_APP`, `PERMISSION_NOT_NEEDED`, `SIDELOADED`, `BANKING_TROJAN_PATTERN`, `LOAN_APP_DATA_GRAB`, `OLD_TARGET_SDK`, `TRACKERS`, `MALWARE_DOMAIN`, `MANY_AD_DOMAINS`, `AD_ABUSE` |
| POST | /scan/apps/label | `{app:{packageName,notifPerHour,hasOverlayPermission,hasSmsPermission,hasAccessibility,hasNotificationListener,hasFullScreenIntent,selfLaunchCount,isSystem},label:ABUSIVE\|BENIGN,scoreShown}` (opt-in on the phone; 6.1) | 204; one row per user + app, a later decision replaces the earlier one |
| GET | /admin/apps?verdict=GOOD\|BAD | – (ADMIN) | list of `{packageName,label,verdict,source,certSha256}` |
| POST | /admin/apps | `{packageName,label?,verdict:GOOD\|BAD,certSha256?}` (ADMIN; cert = SHA-256 hex, colons allowed, several separated by `,`) | the saved entry; 400 on a bad package name or certificate |
| POST | /admin/apps/import | multipart `file` = CSV `package_name,label,verdict,cert_sha256`, `source?` (ADMIN) | `{added,updated,skipped,errors[]}`; max 5000 rows, bad rows skipped and listed |
| GET | /admin/apps/labels.csv | – (ADMIN) | text/csv in the `adabuse-labelled.csv` format (6.1 real labels) |
| GET | /threats?status=OPEN | – | list of `{id,type,severity,title,detail,sourceApp,status,createdAt,explanationEn,explanationHi}` (explanations: link threats from 11.10 on) |
| GET | /threats/summary | – | `{total,open,byType{TYPE:n},bySeverity{SEV:n}}` (every key present, 0 when none) |
| POST | /threats/{id}/resolve | – | 204 |
| DELETE | /me | – | 204 (erase all user data) |
| GET | /config/blocklists?variant=play\|web | – | 18.2 phone-wide block list meta: `{variant,version,count,size,sha256,categories:{ads,trackers,malware},signature}`; `play` (default) = trackers + malware only (no ads, ever), `web` = + ads; count 0 = none published. signature = ECDSA P-256 (same key as /config/blocklist) over `fraudshield-blocklists-v1\n{variant}\n{version}\n{count}\n{sha256}`; 400 for another variant |
| GET | /config/blocklists/file?variant=play\|web | – | 18.2 the list itself, `application/octet-stream`, format FSBL1: `"FSBL1"` + version u64 BE + count u32 BE + count × (hash i64 BE, category u8: 1 ads / 2 trackers / 3 malware), sorted by signed hash; hash = first 8 bytes of SHA-256(lower-case domain) - no domain names in it. The phone checks signature + SHA-256 before use. 404 when none is published. Server reads `blocklists-{variant}.bin` from `fraudshield.blocklists.dir` (env `FRAUDSHIELD_BLOCKLISTS_DIR`, default `./blocklists`), re-read when the file changes; a broken file never replaces the last good one |
| GET | /config/domains?since=v | – | 13.6: `{version,full,added:[{domain,category}],removed:[domain],signature}` - category `ads`/`analytics`/`tracker`/`malware`; a domain also covers its sub-domains; signature = ECDSA P-256 (same key as /config/blocklist) over `fraudshield-domains-v1\n{version}\n{full}\n{domain:category,...}\n{removed,...}` |
| GET | /config/official-apps | – | 13.13: `[{brand,category,packageName,playTitle,certSha256}]` (certSha256 null = not verified yet) |

Verdict: `SAFE`, `SUSPICIOUS`, `MALICIOUS`. Score 0–100.

### Link reason codes (10.9)
Address: `BLOCKLIST`, `MALFORMED_URL`, `NO_HOST`, `NO_HTTPS`, `RAW_IP`, `LOOKALIKE_CHARACTERS`, `URL_SHORTENER`, `RISKY_TLD`,
`MANY_SUBDOMAINS`, `LONG_DOMAIN`, `MANY_HYPHENS`, `LOOKALIKE_BRAND`, `BRAND_IN_SUBDOMAIN`, `BRAND_IN_PATH`, `BAIT_WORDS`,
`APK_DOWNLOAD`, `CREDENTIALS_TRICK`, `SAFE_BROWSING`, `ML_MODEL` (11.5: the model only adds points).
Domain / certificate / redirects: `NEW_DOMAIN`, `YOUNG_DOMAIN`, `CERT_UNTRUSTED`, `CERT_MISMATCH`, `NEW_CERTIFICATE`,
`TOO_MANY_REDIRECTS`, `REDIRECTS_ELSEWHERE`, `HTTPS_DOWNGRADE`, `PRIVATE_ADDRESS`.
Page: `COPIES_BRAND_PAGE`, `COPIES_BRAND_LOGO`, `ASKS_FOR_SECRETS`, `FORM_TO_OTHER_DOMAIN`, `PASSWORD_OVER_HTTP`,
`SCAM_WORDING`, `META_REFRESH_AWAY`.

## Planned endpoints (Phases 10–15, not built yet)
| Method | Path | Body | Response | Phase |
|---|---|---|---|---|
| POST | /scan/domain | `{domain}` (no path/query) | `{verdict,score,reasonCodes[]}` | 12.6 |
| GET | /config/blocklist?since=v | – | `{version,added[],removed[],signature}` (hashed domains) | 12.5 |
| POST | /scan/message | `{text,consent:true}` (processed in memory, never stored/logged) | `{category,score,explanation{en,hi}}` | 14.6 |

## Public-safety extras (Phase 15, backend built)
All need `Authorization: Bearer` and are rate-limited (8.1). Nothing the user checks is logged; UPI IDs and numbers
are stored only as hashes. **Hash rule** (`SafetyValues`): `lowercase hex SHA-256(UTF-8 normalised value)`, where
UPI = trimmed + lower-case; LINK = host only (lower-case, no scheme/port/path/`www.`/trailing dot, user-info dropped).
Phone numbers are reported into the ONE Phase 16 store (E.164 hash, below). The 15.9 directory matches numbers as digits only, `+91`/`0091`/`91` before 10 digits and a single trunk `0` dropped (`+91 98765 43210` ->
`9876543210`, `1800 1234` -> `18001234`, `1930` stays). The directory key is not E.164 on purpose -
customer-care numbers include short codes and toll-free numbers that have no E.164 form (these cannot be community-reported).

| Method | Path | Body | Response | Task |
|---|---|---|---|---|
| POST | /scam-signals | `{signal:LONG_UNKNOWN_CALL\|REMOTE_ACCESS_APP\|BANKING_APP\|OTP_SEEN, unknownCaller?, callSeconds?}` | `{counted,level:NONE\|WARNING\|CRITICAL,signals[],windowMinutes,threatId?,advice?{en,hi}}` | 15.1 |
| GET | /scam-signals/config | – | `{windowMinutes,longCallSeconds,remoteAccessPackages[]}` | 15.1/15.2 |
| POST | /scan/upi | `{vpa}` or `{qrPayload}` | `{verdict,score,kind,reasonCodes[],reasons[],reasonsHi[],claimedBrand?,payee?}` | 15.5 |
| POST | /reports | `{type:LINK\|UPI, value}` or `{type, valueHash}`; `{type:NUMBER\|CARE_NUMBER, value}` is passed to the Phase 16 number store (same as `/numbers/report` with category SCAM; plain value only) | 202 `{counted,status}` | 15.7 |
| GET | /reports/status?type=&value= (or `valueHash=`) | – | `{type,status,reportCount}` | 15.7 |
| GET | /admin/reports/queue | – (ADMIN) | `[{id,type,valueHash,linkHost?,reportCount,trustScore,status,firstAt,updatedAt}]` | 15.7 |
| POST | /admin/reports/{id}/review | `{decision:CONFIRM\|CLEAR}` (ADMIN) | the item; CONFIRM on a LINK adds its host to the blocklist | 15.7 |
| GET | /care-number/{number}?brand= | – (brand optional: name or keyword, e.g. `SBI`) | `{number,official,brand?,verdict:OFFICIAL\|UNKNOWN\|SUSPICIOUS\|FAKE,score,reasonCodes[],reasons[],reasonsHi[],officialNumbers[{number,purpose}],officialUrl?,sourceUrl?}` | 15.9 |

**15.1 rules.** Signals are kept only as kind + time, deleted after 24 h. A call counts when `unknownCaller` is not
`false` and `callSeconds >= 180`. All four kinds within 10 min -> CRITICAL `LIVE_SCAM` threat (one OPEN at a time,
pushed). Call + remote-access app + one more -> `WARNING` (no threat). Fewer -> `NONE`.

**15.5 verdicts.** score >= 70 MALICIOUS, >= 40 SUSPICIOUS, else SAFE. `kind`: VPA, PAY, COLLECT, MANDATE, URL (QR held
a web link: checked like `/scan/url`), INVALID. Codes: `INVALID_VPA` 50, `INVALID_QR` 50, `UNKNOWN_HANDLE` 20,
`COMMUNITY_REPORTED` 70, `COMMUNITY_CONFIRMED` 100, `BRAND_NAME_MISMATCH` 45, `SCAM_WORDS` 25, `RECEIVE_MONEY_TRICK` 45,
`COLLECT_REQUEST` 50, `AUTOPAY_MANDATE` 30, and only on top of other signs `NEW_VPA` 10 and `HIGH_AMOUNT` 10 (>= Rs 20,000).

**15.7 rules.** One vote per user per item. Weight 1.0 for accounts older than 7 days, 0.2 for newer ones, minus 0.5
for every earlier report of that user a moderator CLEARED (floor 0). Trust score >= 5.0 -> SUSPICIOUS. Max 30 reports
per user per 24 h (429). Moderation queue = SUSPICIOUS items plus UNVERIFIED items with >= 3 reports, not yet reviewed.
All values in `fraudshield.reports.*`.

Examples:
```
POST /api/v1/scam-signals {"signal":"OTP_SEEN"}
-> {"counted":true,"level":"CRITICAL","signals":["LONG_UNKNOWN_CALL","REMOTE_ACCESS_APP","BANKING_APP","OTP_SEEN"],
    "windowMinutes":10,"threatId":17,"advice":{"en":"This looks like a screen-sharing scam. Hang up now. ...","hi":"..."}}

POST /api/v1/scan/upi {"qrPayload":"upi://collect?pa=refund.desk@ybl&pn=Refund&am=4999&tn=Receive%20refund"}
-> {"verdict":"MALICIOUS","score":75,"kind":"COLLECT","reasonCodes":["SCAM_WORDS","COLLECT_REQUEST"],
    "reasons":["The name or note uses words common in scams ...","This is a request to TAKE money from your account, ..."],
    "reasonsHi":["...","..."],"claimedBrand":null,
    "payee":{"vpa":"refund.desk@ybl","name":"Refund","amount":"4999","handleKnown":true,"handleOwner":"PhonePe"}}

POST /api/v1/reports {"type":"UPI","value":"refund.desk@ybl"}   -> 202 {"counted":true,"status":"UNVERIFIED"}

GET /api/v1/care-number/919876543210?brand=SBI
-> {"number":"9876543210","official":false,"brand":"State Bank of India","verdict":"FAKE","score":100,
    "reasonCodes":["PERSONAL_MOBILE_FOR_BRAND","NOT_IN_OFFICIAL_LIST"],"reasons":["..."],"reasonsHi":["..."],
    "officialNumbers":[{"number":"1800 1234","purpose":"toll-free customer care"}, ...],
    "officialUrl":"https://...","sourceUrl":null}
```

## Call guard (Phase 16, free data only)
All need `Authorization: Bearer`; all are rate-limited (8.1). Numbers are identified by
`hash = lowercase hex SHA-256(UTF-8 canonical E.164)`, e.g. `+919876543210` ->
`f3a47ce5ce3d4ca8ad15225a245b2759022f79489f5c62719b8c9490f7aab90e`. Canonical: drop spaces `-` `.` `(` `)`; `00` -> `+`;
bare 10 digits, `0`+10 digits, `91`+10 digits -> `+91...`; `+` numbers need 8-15 digits and `+91` exactly 10 after it;
anything else (short codes such as 1930) has no canonical form (`PhoneNumbers.canonical`).

| Method | Path | Body | Response | Task |
|---|---|---|---|---|
| GET | /numbers/range/{prefix5} | - (first 5 hex chars of the hash; case-insensitive) | `{prefix, entries:[{suffix, category, reports, trust, status:"SUSPICIOUS", updatedAt}]}` - only SUSPICIOUS numbers; 400 if not 5 hex | 16.2 |
| GET | /config/number-rules | - | `{version, matching, rules:[{id,type,...,category,action,label:{en,hi},note,sources[]}], riskyCountryCodes[], countryNotes{}, sources{id:{title,url}}}` | 16.3 |
| GET | /config/numbers?since=v | - | `{version, full, added:[{hash,category}], removed:[hash], signature}` | 16.4 |
| POST | /numbers/report | `{number, category: SCAM\|SPAM\|TELEMARKETING}` | `{status: REPORTED\|SUSPICIOUS, counted, reports}`; 400 bad number/category, 422 `PROTECTED_SERIES` (1600 / 140), 429 daily limit | 16.5 |
| POST | /admin/numbers/{hash}/clear | - (ADMIN) | `{cleared:true}`; 404 unknown; listed numbers appear in the next delta's `removed` | 16.5 |

Signed text for `/config/numbers` (ECDSA P-256 SHA256withECDSA, same key as `/config/blocklist`):
`"fraudshield-numbers-v1\n" + version + "\n" + full + "\n" + join(",", hash + ":" + category for added, sorted by hash) + "\n" + join(",", removed sorted)`
(`full` is `true`/`false`; `category` is the reputation's top category, e.g. `SCAM`).
Rule types: `NATIONAL_PREFIX` (Indian 10-digit national number starts with `prefix`, length `length`), `COUNTRY_LENGTH`
(`+countryCode` followed by != `notLength` digits), `E164_LENGTH` (`+` number outside `minDigits`..`maxDigits`),
`COUNTRY_CODE_IN` (starts with one of `riskyCountryCodes`), `NOT_COUNTRY` (`+` number not `+countryCode`). First match wins.
Actions: `NEVER_BLOCK` (TRAI: 1600 / 140 may not be tagged or blocked), `WARN`, `LABEL`.

## Rate limits (8.1)
Per logged-in user (or per IP when not logged in): **100 requests/minute**; `POST /auth/*` per IP: **10/minute**.
Over the limit: **429** in the standard error shape, with `Retry-After` (seconds). Every response carries
`X-RateLimit-Limit` and `X-RateLimit-Remaining`. Configurable: `RATE_LIMIT_PER_MINUTE`, `RATE_LIMIT_AUTH_PER_MINUTE`.
Health check and API docs are not limited.

## Token errors
A validly signed token whose account was deleted, or that was issued before the user's last logout, gets **401**
(never 500). The app then renews once with its refresh token; if that is refused it shows the login screen.
