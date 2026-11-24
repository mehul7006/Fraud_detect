# Phase 18 - phone-wide ad / tracker / malware block list

## Build the list (weekly, ~10 s after download)

```sh
python -I ml/blocklists/build_blocklists.py --download     # sources + licences: DATA-LICENSES.md
# -> _devtools/blocklists/out/blocklists-play.bin, blocklists-web.bin, domains.tsv.gz, manifest.json
```

## Publish it on the server

The backend serves `blocklists-{play,web}.bin` from `fraudshield.blocklists.dir`
(env `FRAUDSHIELD_BLOCKLISTS_DIR`, default `./blocklists` relative to the working directory) and re-reads them
when they change - copy new files in, no restart. A broken file is refused and the last good one stays.

Dev container: mount the folder and set the env once, e.g.
`docker run ... -v "E:/fraud detection/_devtools/blocklists/out:/blocklists:ro" -e FRAUDSHIELD_BLOCKLISTS_DIR=/blocklists ...`
Check: `GET /api/v1/config/blocklists?variant=play` -> `count` 351,998 (version 2026112401).

## App builds (18.9)

```sh
flutter build apk --debug --dart-define=API_BASE=http://127.0.0.1:18080/api/v1                          # = Play behaviour
flutter build apk --debug --dart-define=FS_FLAVOUR=web --dart-define=API_BASE=http://127.0.0.1:18080/api/v1  # website build (+ ads)
flutter build appbundle --release --dart-define=FS_FLAVOUR=play                                          # Play upload
```
No `--flavor` is needed (no Gradle productFlavors). Gradle gets the flag from the dart-defines
(`tool/flavour_gradle.py`, run by `tool/setup_android.sh`), so `BuildConfig.FS_FLAVOUR` and Dart's `kFlavour` agree.

## Device test script (18.4 - 18.8, phone)

Preconditions: server publishes the list (above); app installed (web build for the ad tests); VPN on.

1. **Sync** - VPN screen -> "Ad & tracker blocking" -> *Update now* -> "Block list: 542,722 sites" (web) or
   351,998 (play). `adb logcat -s FraudShield` shows no "signature invalid".
2. **18.3 live switches** - in Chrome open `http://doubleclick.net` (web build): DNS_PROBE_FINISHED_NXDOMAIN.
   Switch *Ads* off -> reload (wait ~30 s or toggle airplane mode: Android caches "does not exist") -> loads.
   Play build: no *Ads* switch, doubleclick.net loads; `app-measurement.com` is blocked.
   RAM: `adb shell dumpsys meminfo com.fraudshield.app` before/after sync (expect +~5 MB web, +~3 MB play).
3. **18.4 escape hatches** - pick an ad-heavy free game: ads gone with blocking on; *Allow an app -> Allow* ->
   restart the game -> ads back. *Pause 5 min* -> ads back; after 5 min (or *Resume*) blocked again.
   *Allow a site* `doubleclick.net` -> loads. *This app broke* -> app allowed + note kept (Settings shows it).
4. **18.5 stats** - after 1 hour of normal use: *Blocking statistics* total = "N requests blocked since the VPN
   started" on the VPN screen and = `adBlocked` in `vpnStatus` (same counter, flushed every 30 s).
5. **18.6 browsers** - Chrome, Samsung Internet, Firefox, Edge, Brave: open `http://doubleclick.net` and a
   tracker (`http://app-measurement.com`); record which use their own secure DNS (DoH hosts are blocked by 12.3,
   so they fall back; Firefox/Brave "max protection" modes may need the user to switch to "default").
   Evidence -> `docs/evidence/18-browsers.md`.
6. **18.7 compatibility** - top Indian apps (banking, UPI, shopping, OTT): log in, pay Rs 1, play a video with
   blocking on; list breakages and the domain that caused them (Stats -> top sites for that app) -> add to the
   never-block set in `build_blocklists.py` (server side, all users) or an allow-default.
7. **18.8 battery / speed** - 24 h soak with blocking on: compare battery with the 12.9 baseline; page-load time
   for 5 sites with blocking on vs off (`overheadMs` in the VPN stats log stays ~11-14 ms; lookup in the list is
   ~5 us on a PC).
