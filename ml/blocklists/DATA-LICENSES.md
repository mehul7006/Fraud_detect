# 18.2 Phone block list - sources and licences (checked 2026-11-24)

Built by `python -I ml/blocklists/build_blocklists.py --download` (stdlib only). Raw lists and outputs stay in
`_devtools/blocklists/` (never committed); `manifest.json` there records the SHA-256 of every input and output.
Each licence was read from the source repository itself (GitHub licence API + the LICENSE / COPYING file, or the
list's own header), not from an aggregator's table.

## Used

| Source | Used for | Licence (verified at) | Obligation / credit |
|---|---|---|---|
| AdAway default hosts - `AdAway/adaway.github.io/hosts.txt` | ads | **CC BY 3.0** - stated in the file header ("License: CC Attribution 3.0"); the repo's GPL-3.0 LICENSE covers the website, not the list | credit "AdAway hosts file, adaway.org, CC BY 3.0" |
| Steven Black ad-hoc list - `StevenBlack/hosts/data/StevenBlack/hosts` | ads | **MIT** (`license.txt`) | keep copyright notice |
| FadeMind UncheckyAds - `FadeMind/hosts.extras` | ads | **MIT** (`COPYING`) | keep copyright notice |
| hostsVN - `bigdargon/hostsVN/option/hosts-VN` | ads | **MIT** (`LICENSE`) | keep copyright notice |
| Tiuxo ads - `tiuxo/hosts/ads` | ads | **CC BY 4.0** (`LICENSE`) | credit "Tiuxo hostlist, CC BY 4.0" |
| 1Hosts Lite - `badmojr/1Hosts/Lite/domains.txt` | ads / trackers (mixed) | **MPL-2.0** (`LICENSE`) | file-level copyleft: the derived list must be available under MPL-2.0 - we publish `domains.tsv.gz` (owner to confirm, see `docs/ad-blocking-policy.md`) |
| FadeMind add.2o7Net | trackers | **MIT** | keep copyright notice |
| minecraft-hosts tracking - `jamiemansfield/minecraft-hosts` | trackers | **CC0-1.0** (`LICENSE.txt`) | none |
| FraudShield curated analytics / tracker endpoints (`seed/domain-categories.csv`, 13.6) | trackers | own | - |
| FadeMind add.Risk, add.Spam | malware | **MIT** | keep copyright notice |
| Badd Boyz Hosts - `mitchellkrogza/Badd-Boyz-Hosts` | malware | **MIT** (`LICENSE.md`; GitHub shows NOASSERTION, the file is the MIT text) | keep copyright notice |
| Phishing.Database - `phishing-domains-ACTIVE.txt` | malware (phishing) | **MIT** (`LICENSE`) | keep copyright notice |
| Tranco top 100k + Phase 10 official brand domains + PSL private section | safety filter only (not blocked) | as in `psl/NOTICE.md` | - |

## Not used (licence does not allow a commercial app, or none found)

| Source | Why not |
|---|---|
| MVPS hosts | CC BY-NC-SA 4.0 (non-commercial) |
| Dan Pollock / someonewhocares | "non-commercial with attribution" |
| Peter Lowe / pgl.yoyo.org | no licence stated |
| OISD | no licence found on oisd.nl |
| Hagezi DNS blocklists | GPL-3.0 (copyleft on the data; avoided) |
| KADhosts | CC BY-SA 4.0 (share-alike; avoided for now) |
| Steven Black *unified* hosts | bundles MVPS / someonewhocares / yoyo (above) - only its MIT ad-hoc list is used |
| URLhaus (abuse.ch) | abuse.ch terms of use (section 4, <https://abuse.ch/terms-of-use/>): commercial use "may require a paid subscription". **Note:** 13.6 `seed/domain-categories.csv` still contains 68 URLhaus hosts recorded as CC0 in `psl/NOTICE.md` - owner to review |

## Credits to show in the app's licences screen (CC BY)

* "AdAway hosts file, adaway.org - CC BY 3.0"
* "Tiuxo hostlist (github.com/tiuxo/hosts) - CC BY 4.0"
* MIT notices: Steven Black, FadeMind, bigdargon (hostsVN), Mitchell Krog (Badd Boyz), Phishing.Database
* "1Hosts (github.com/badmojr/1Hosts) - MPL-2.0"

## Result (version 2026112401, built 2026-11-24)

| | domains |
|---|---|
| website list (ads + trackers + malware) | **542,722** (ads 190,724 · trackers 16,996 · malware 335,002) |
| Play list (trackers + malware) | **351,998** |
| dropped: core infrastructure (never-block set) | 3,098 |
| dropped: phishing entries on / above a popular or official site (not on a user-content platform) | 54,006 |
