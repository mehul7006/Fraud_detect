# REST API v1  (base `/api/v1`, JSON, `Authorization: Bearer <jwt>` except /auth)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | /auth/register | `{email,password}` | `{token}` |
| POST | /auth/login | `{email,password}` | `{token}` |
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
| POST | /scan/apps | `{apps:[{packageName,label,notifPerHour,hasOverlayPermission,hasSmsPermission,hasAccessibility,isSystem}]}` | `{results:[{packageName,adAbuseScore,verdict,reasons[]}]}` |
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
