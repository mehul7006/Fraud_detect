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
| POST | /scan/url | `{url,sourceApp}` | `{verdict,score,reasons[]}` |
| POST | /scan/url/feedback | `{url,kind:FALSE_POSITIVE\|FALSE_NEGATIVE,note?}` (host only is stored) | 204 (409 duplicate) |
| GET | /admin/blocklist | – (ADMIN) | list of `{id,kind,value,reason}` |
| POST | /admin/blocklist | `{kind:DOMAIN\|SHA256,value,reason?}` (ADMIN) | 201 (409 duplicate) |
| DELETE | /admin/blocklist/{id} | – (ADMIN) | 204 |
| POST | /admin/blocklist/import | multipart CSV `kind,value,reason`, max 5000 rows (ADMIN) | `{added,skipped,errors[]}` |
| GET | /admin/feedback?status=OPEN | – (ADMIN) | list of reports |
| POST | /admin/feedback/{id}/close | `{resolution:ADD_TO_BLOCKLIST\|ALLOW\|DISMISS}` (ADMIN) | closed report |
| POST | /scan/file | `{sha256,fileName,sizeBytes,mimeType,sourceApp,headerHex,flags?}` - flags: max 10 of `PDF_JS`, `PDF_LAUNCH`, `PDF_OPENACTION`, `OFFICE_MACRO` (names only, never content) | `{verdict,score,reasons[],action}` |
| POST | /scan/apps | `{apps:[{packageName,label,notifPerHour,hasOverlayPermission,hasSmsPermission,hasAccessibility,hasNotificationListener,hasFullScreenIntent,selfLaunchCount?,isSystem,certSha256?,installer?}],deviceId?}` | `{results:[{packageName,adAbuseScore,verdict,reasons[],newSinceLastScan}]}` (newSinceLastScan only with deviceId). A known-GOOD package is trusted only if certSha256 matches the stored certificate, or - while none is stored - installer is `com.android.vending`; a wrong certificate gives MALICIOUS "Fake copy of ..." |
| POST | /scan/apps/label | `{app:{packageName,notifPerHour,hasOverlayPermission,hasSmsPermission,hasAccessibility,hasNotificationListener,hasFullScreenIntent,selfLaunchCount,isSystem},label:ABUSIVE\|BENIGN,scoreShown}` (opt-in on the phone; 6.1) | 204; one row per user + app, a later decision replaces the earlier one |
| GET | /admin/apps?verdict=GOOD\|BAD | – (ADMIN) | list of `{packageName,label,verdict,source,certSha256}` |
| POST | /admin/apps | `{packageName,label?,verdict:GOOD\|BAD,certSha256?}` (ADMIN; cert = SHA-256 hex, colons allowed, several separated by `,`) | the saved entry; 400 on a bad package name or certificate |
| POST | /admin/apps/import | multipart `file` = CSV `package_name,label,verdict,cert_sha256`, `source?` (ADMIN) | `{added,updated,skipped,errors[]}`; max 5000 rows, bad rows skipped and listed |
| GET | /admin/apps/labels.csv | – (ADMIN) | text/csv in the `adabuse-labelled.csv` format (6.1 real labels) |
| GET | /threats?status=OPEN | – | list of ThreatEvent |
| GET | /threats/summary | – | `{total,open,byType{TYPE:n},bySeverity{SEV:n}}` (every key present, 0 when none) |
| POST | /threats/{id}/resolve | – | 204 |
| DELETE | /me | – | 204 (erase all user data) |

Verdict: `SAFE`, `SUSPICIOUS`, `MALICIOUS`. Score 0–100.

## Planned endpoints (Phases 10–15, not built yet)
| Method | Path | Body | Response | Phase |
|---|---|---|---|---|
| POST | /scan/url | `{url,sourceApp}` | adds `reasonCodes[]`, `impersonatedBrand`, `officialUrl`, `mlScore`, `explanation{en,hi}` | 10.9, 11 |
| POST | /scan/domain | `{domain}` (no path/query) | `{verdict,score,reasonCodes[]}` | 12.6 |
| GET | /config/blocklist?since=v | – | `{version,added[],removed[],signature}` (hashed domains) | 12.5 |
| GET | /config/domains?since=v | – | domain categories (ads/analytics/tracker/malware) | 13.6 |
| POST | /scan/apps | extended with `installer,signerSha256,requestedPermissions[],grantedPermissions[],trackers[],domainsContacted[]` | adds `privacyScore,reasonCodes[]` | 13.3, 13.7 |
| GET | /config/official-apps | – | brand → package + cert hashes | 13.13 |
| POST | /scan/message | `{text,consent:true}` (processed in memory, never stored/logged) | `{category,score,explanation{en,hi}}` | 14.6 |
| POST | /scan/upi | `{vpa}` or `{qrPayload}` | `{verdict,reasons[]}` | 15.5 |
| POST | /reports | `{type:NUMBER|LINK|UPI,valueHash}` | 202 | 15.7 |
| GET | /care-number/{number} | – | `{official:boolean,brand?}` | 15.9 |

## Planned endpoints: Call guard (Phase 16, free data only)
| Method | Path | Body | Response | Task |
|---|---|---|---|---|
| GET | /numbers/range/{prefix5} | - (first 5 hex chars of the number hash) | `[{suffix,category,reportCount}]` | 16.2 |
| GET | /config/number-rules | - | JSON rules: risky country codes, telemarketer/service series, length checks | 16.3 |
| GET | /config/numbers?since=v | - | `{version,added[],removed[],signature}` (hashed numbers) | 16.4 |
| POST | /numbers/report | `{numberHash,category}` | 202 | 16.5 |

## Rate limits (8.1)
Per logged-in user (or per IP when not logged in): **100 requests/minute**; `POST /auth/*` per IP: **10/minute**.
Over the limit: **429** in the standard error shape, with `Retry-After` (seconds). Every response carries
`X-RateLimit-Limit` and `X-RateLimit-Remaining`. Configurable: `RATE_LIMIT_PER_MINUTE`, `RATE_LIMIT_AUTH_PER_MINUTE`.
Health check and API docs are not limited.

## Token errors
A validly signed token whose account was deleted, or that was issued before the user's last logout, gets **401**
(never 500). The app then renews once with its refresh token; if that is refused it shows the login screen.
