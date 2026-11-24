# Third-party data shipped with the backend

| File | Source | Licence |
|---|---|---|
| `psl/public_suffix_list.dat` | Mozilla Public Suffix List, https://publicsuffix.org/list/ | MPL-2.0 (unchanged copy; see header of the file) |
| `seed/popular-domains.txt` | Tranco top-sites list, https://tranco-list.eu (top 100,000) | Free to use; please cite: Le Pochat et al., "Tranco: A Research-Oriented Top Sites Ranking Hardened Against Manipulation", NDSS 2019 |

Refresh both with `tools/lookalike/update_popular.sh`.
| `seed/domain-categories.csv` - `ads` | AdAway default hosts, https://adaway.org/hosts.txt (downloaded 2026-11-23) | CC BY 3.0 - credit: "AdAway hosts file, adaway.org" |
| `seed/domain-categories.csv` - `malware` | URLhaus recent URLs (abuse.ch), host names only, popular sites removed (2026-11-23) | CC0 |
| `seed/domain-categories.csv` - `analytics` / `tracker` / some `ads` | own list of well-known SDK endpoints | ours |
| `seed/official-apps.csv` | packages from `seed/known-good-apps.csv` (checked on Google Play India 2026-11-22) | ours (facts) |

Not used for licence reasons: Exodus Privacy tracker list (AGPL-3.0), Disconnect services list (CC BY-NC-SA, non-commercial),
DuckDuckGo Tracker Radar (CC BY-NC-SA).
