#!/usr/bin/env python3
"""18.2: phone-wide ad / tracker / malware block list - one command, stdlib only, reproducible.

    python -I ml/blocklists/build_blocklists.py [--download] [--raw DIR] [--out DIR] [--version N]

Sources (licences verified 2026-11-24 from each repository's LICENSE / file header - see ml/blocklists/DATA-LICENSES.md
section "18.2"). Lists whose licence does not allow commercial redistribution are NOT used (MVPS: CC BY-NC-SA,
Dan Pollock: non-commercial, yoyo.org: no licence, OISD: no licence found, Hagezi: GPL-3.0, URLhaus: abuse.ch terms
since 2025 require a paid subscription for commercial use).

Output (never committed - raw lists and outputs live under _devtools/blocklists/):
  blocklists-web.bin   all categories (website flavour)            \\  format FSBL1, see BlockList.kt /
  blocklists-play.bin  trackers + malware only (Google Play flavour) /  BlocklistFileService.java
  domains.tsv.gz       domain<TAB>category<TAB>sources (for review; also how we meet MPL-2.0 for 1Hosts entries:
                       the derived list is published unchanged in this form)
  manifest.json        input SHA-256, counts per source / category, licence per source

Categorisation (conservative for the Play flavour, whose policy forbids interfering with other apps' ads):
  malware  - risk / spam / phishing feeds (never a Tranco-top-100k site or a parent of one)
  trackers - tracking-only sources, or a mixed-source entry whose labels clearly say analytics / telemetry /
             attribution AND that is not listed by any ad source and has no ad-like label
  ads      - everything else from ad sources (also "mixed" entries we cannot prove are tracking-only)
"""
import argparse
import csv
import gzip
import hashlib
import json
import os
import re
import struct
import sys
import time
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_RAW = os.path.join(ROOT, "_devtools", "blocklists", "raw")
DEFAULT_OUT = os.path.join(ROOT, "_devtools", "blocklists", "out")
POPULAR = os.path.join(ROOT, "backend", "src", "main", "resources", "seed", "popular-domains.txt")
BRANDS = os.path.join(ROOT, "backend", "src", "main", "resources", "seed", "brands.csv")
PSL = os.path.join(ROOT, "backend", "src", "main", "resources", "psl", "public_suffix_list.dat")

# file, url, licence, kind (ads / trackers / mixed / malware)
SOURCES = [
    ("adaway.txt", "https://raw.githubusercontent.com/AdAway/adaway.github.io/master/hosts.txt", "CC-BY-3.0", "ads"),
    ("stevenblack-adhoc.txt", "https://raw.githubusercontent.com/StevenBlack/hosts/master/data/StevenBlack/hosts", "MIT", "ads"),
    ("fademind-unchecky.txt", "https://raw.githubusercontent.com/FadeMind/hosts.extras/master/UncheckyAds/hosts", "MIT", "ads"),
    ("hostsvn.txt", "https://raw.githubusercontent.com/bigdargon/hostsVN/master/option/hosts-VN", "MIT", "ads"),
    ("tiuxo-ads.txt", "https://raw.githubusercontent.com/tiuxo/hosts/master/ads", "CC-BY-4.0", "ads"),
    ("1hosts-lite.txt", "https://raw.githubusercontent.com/badmojr/1Hosts/master/Lite/domains.txt", "MPL-2.0", "mixed"),
    ("fademind-2o7net.txt", "https://raw.githubusercontent.com/FadeMind/hosts.extras/master/add.2o7Net/hosts", "MIT", "trackers"),
    ("minecraft-tracking.txt", "https://raw.githubusercontent.com/jamiemansfield/minecraft-hosts/master/lists/tracking.txt", "CC0-1.0", "trackers"),
    ("fademind-risk.txt", "https://raw.githubusercontent.com/FadeMind/hosts.extras/master/add.Risk/hosts", "MIT", "malware"),
    ("fademind-spam.txt", "https://raw.githubusercontent.com/FadeMind/hosts.extras/master/add.Spam/hosts", "MIT", "malware"),
    ("baddboyz.txt", "https://raw.githubusercontent.com/mitchellkrogza/Badd-Boyz-Hosts/master/hosts", "MIT", "malware"),
    ("phishingdb-domains-active.txt", "https://raw.githubusercontent.com/Phishing-Database/Phishing.Database/master/phishing-domains-ACTIVE.txt", "MIT", "malware"),
]

CATS = {"ads": 1, "trackers": 2, "malware": 3}

# Infrastructure the phone and most apps need; never blocked whatever a list says (18.7 adds per-app breakages).
NEVER_BLOCK = {
    "localhost", "connectivitycheck.gstatic.com", "connectivitycheck.android.com", "clients3.google.com",
    "googleapis.com", "gstatic.com", "android.com", "google.com", "play.google.com", "googleusercontent.com",
    "firebaseinstallations.googleapis.com", "fcm.googleapis.com", "mtalk.google.com", "firebaseio.com",
    "whatsapp.net", "whatsapp.com", "npci.org.in", "upi.npci.org.in", "apple.com", "icloud.com",
    "microsoft.com", "windowsupdate.com", "github.com", "githubusercontent.com", "cloudflare.com",
    "akamaihd.net", "akamaized.net", "fastly.net", "cloudfront.net", "amazonaws.com", "azureedge.net",
}

TRACKER_LABEL = re.compile(r"(^|[.-])(analytic\w*|metrics?|telemetry|tracking|tracker|track|stats?|statistics|"
                           r"collect(or)?|beacons?|pixel|insights?|measure\w*|attribution|crashlytics|sentry|"
                           r"bugsnag|mixpanel|amplitude|appsflyer|adjust|branch|segment|clarity|hotjar|"
                           r"smetrics|omtrdc|2o7|demdex|scorecardresearch|quantserve|chartbeat|newrelic|nr-data|"
                           r"app-measurement|firebase-settings)([.-]|$)")
AD_LABEL = re.compile(r"(^|[.-])(ads?|adserv\w*|adservice|adsystem|adtech|adx|advert\w*|banners?|doubleclick|"
                      r"googlesyndication|pagead\w*|promo\w*|sponsor\w*|popunder|popads|admob|applovin|unityads|"
                      r"inmobi|vungle|mopub|chartboost|ironsrc|taboola|outbrain|criteo|adcolony|bidder|rtb|"
                      r"prebid|syndication|monetiz\w*|offerwall)([.-]|$)")
DOMAIN = re.compile(r"^(?=.{4,253}$)([a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?\.)+[a-z][a-z0-9-]{1,62}$")


def download(raw):
    os.makedirs(raw, exist_ok=True)
    for f, url, _, _ in SOURCES:
        print("download", f, flush=True)
        req = urllib.request.Request(url, headers={"User-Agent": "fraudshield-blocklist-builder"})
        with urllib.request.urlopen(req, timeout=180) as r, open(os.path.join(raw, f), "wb") as o:
            o.write(r.read())


def parse(path):
    """hosts format ("0.0.0.0 a.b"), plain domain lists and AdBlock "||a.b^" lines -> set of names."""
    out = set()
    with open(path, encoding="utf-8", errors="ignore") as fh:
        for line in fh:
            line = line.split("#", 1)[0].strip().lower()
            if not line or line.startswith("!"):
                continue
            parts = line.split()
            name = parts[1] if len(parts) >= 2 and re.match(r"^(0\.0\.0\.0|127\.0\.0\.1|::1?|::)$", parts[0]) else parts[0]
            name = name.strip("|^").rstrip(".")
            if name.startswith("*."):
                name = name[2:]
            if DOMAIN.match(name) and name not in ("localhost", "local", "broadcasthost"):
                out.add(name)
    return out


def parents(name):
    p = name.split(".")
    return [".".join(p[i:]) for i in range(len(p) - 1)]


def category_of(name, kinds):
    if "malware" in kinds:
        return "malware"
    # our own curated analytics / attribution endpoints (13.6, ours): tracking, not ad display
    if "curated" in kinds and not AD_LABEL.search(name):
        return "trackers"
    if "ads" in kinds:
        return "ads"
    if "trackers" in kinds:
        return "ads" if AD_LABEL.search(name) else "trackers"
    # only "mixed" sources: tracking-only when the name clearly says so
    if TRACKER_LABEL.search(name) and not AD_LABEL.search(name):
        return "trackers"
    return "ads"


def hash64(name):
    """First 8 bytes of SHA-256 (big-endian, signed like a Java/Kotlin long)."""
    return struct.unpack(">q", hashlib.sha256(name.encode("utf-8")).digest()[:8])[0]


def write_bin(path, version, entries):
    """FSBL1 | version u64 | count u32 | count x (hash i64, category u8) sorted by hash (signed order)."""
    rows = {}
    for name, cat in entries:
        h = hash64(name)
        c = CATS[cat]
        rows[h] = max(rows.get(h, 0), c)  # on a 64-bit collision keep the most severe category
    with open(path, "wb") as o:
        o.write(b"FSBL1")
        o.write(struct.pack(">QI", version, len(rows)))
        for h in sorted(rows):
            o.write(struct.pack(">qB", h, rows[h]))
    return len(rows)


def sha256_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as fh:
        for b in iter(lambda: fh.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--download", action="store_true")
    ap.add_argument("--raw", default=DEFAULT_RAW)
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--version", type=int, default=int(time.strftime("%Y%m%d")) * 100 + 1)
    a = ap.parse_args()
    if a.download:
        download(a.raw)
    popular = set()
    with open(POPULAR, encoding="utf-8") as fh:
        for line in fh:
            d = line.strip().lower()
            if d and not d.startswith("#"):
                popular.add(d)
    # official brand domains (Phase 10 registry) count as popular: a phishing feed entry under them is a mistake
    with open(BRANDS, encoding="utf-8") as fh:
        for row in csv.DictReader(l for l in fh if not l.startswith("#")):
            popular.update(d.strip().lower() for d in (row.get("official_domains") or "").split("|") if "." in d)

    # user-content platforms (PSL private section: weebly.com, firebaseapp.com, github.io ...): a phishing site
    # ON them is blockable, the platform itself is not
    private, in_private = set(), False
    with open(PSL, encoding="utf-8") as fh:
        for line in fh:
            if "===BEGIN PRIVATE DOMAINS===" in line:
                in_private = True
            elif in_private and line.strip() and not line.startswith("//"):
                private.add(line.strip().lstrip("*.!").lower())
    popular_parents = set()
    for d in popular:
        popular_parents.update(parents(d))

    kinds, srcs, manifest_src = {}, {}, []
    curated_file = os.path.join(ROOT, "backend", "src", "main", "resources", "seed", "domain-categories.csv")
    with open(curated_file, encoding="utf-8") as fh:
        curated = {l.split(",")[0].strip().lower() for l in fh if l.strip().endswith((",analytics", ",tracker"))}
    manifest_src.append({"file": "seed/domain-categories.csv (analytics + tracker rows)", "url": "own list (13.6)",
                         "licence": "own", "kind": "curated", "sha256": sha256_file(curated_file), "domains": len(curated)})
    for n in curated:
        kinds.setdefault(n, set()).add("curated")
        srcs.setdefault(n, []).append("fraudshield-curated")
    for f, url, lic, kind in SOURCES:
        p = os.path.join(a.raw, f)
        if not os.path.exists(p):
            sys.exit(f"missing {p} - run with --download")
        names = parse(p)
        manifest_src.append({"file": f, "url": url, "licence": lic, "kind": kind, "sha256": sha256_file(p), "domains": len(names)})
        for n in names:
            kinds.setdefault(n, set()).add(kind)
            srcs.setdefault(n, []).append(f.rsplit(".", 1)[0])

    # 18.7 compatibility: under an OFFICIAL brand domain (banks, UPI, shopping, OTT ... from the Phase 10 registry) an
    # entry is kept only when its own labels say ads / tracking (ads., analytics., smetrics., sentry. ...). Anything
    # else there is a working part of that app (youtubei.youtube.com = the YouTube app's API, m.navi.com, dl.purplle.com
    # deep links, devicebind.ebay.com = login) and is never blocked; each such entry goes to allow-defaults.json.
    brand_of = {}
    with open(BRANDS, encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            for d in (row.get("official_domains") or "").split("|"):
                d = d.strip().lower()
                if "." in d: brand_of[d] = row.get("name", "")
    allow_defaults = []

    dropped = {"never_block": 0, "popular_malware": 0, "brand_functional": 0}
    final = {}
    for n, k in kinds.items():
        if any(p in NEVER_BLOCK for p in parents(n)) or n in NEVER_BLOCK:
            dropped["never_block"] += 1
            continue
        brand_dom = next((p for p in parents(n) if p in brand_of), None)
        if brand_dom and n != brand_dom:
            below = n[: -len(brand_dom) - 1]  # the labels in front of the brand's own domain
            if not (TRACKER_LABEL.search(below) or AD_LABEL.search(below)):
                dropped["brand_functional"] += 1
                allow_defaults.append({"domain": n, "brand": brand_of[brand_dom], "listedAs": sorted(k),
                                       "reason": "official brand domain, name is not an ad/tracking host"})
                continue
        c = category_of(n, k)
        # a phishing / risk entry must never take down a popular site (or one of its subdomains via a parent entry)
        # blocking n also blocks everything below it: n must not be a popular site, a parent of one, or a
        # subdomain of one - unless the popular parent is a user-content platform (then n is one user's site)
        risky = n in popular or n in popular_parents or n in private or any(
            p in popular and p not in private and not any(q in private for q in parents(p)) for p in parents(n)[1:])
        if c == "malware" and risky:
            if "ads" in k or "trackers" in k or "mixed" in k:
                c = category_of(n, k - {"malware"})
            else:
                dropped["popular_malware"] += 1
                continue
        final[n] = c

    os.makedirs(a.out, exist_ok=True)
    web = sorted(final.items())
    play = [(n, c) for n, c in web if c != "ads"]
    n_web = write_bin(os.path.join(a.out, "blocklists-web.bin"), a.version, web)
    n_play = write_bin(os.path.join(a.out, "blocklists-play.bin"), a.version, play)
    with gzip.open(os.path.join(a.out, "domains.tsv.gz"), "wt", encoding="utf-8") as g:
        g.write("# FraudShield phone block list v%d - derived from the sources in manifest.json (licences there).\n" % a.version)
        g.write("# Entries from 1Hosts (MPL-2.0) are redistributed here under MPL-2.0.\n")
        for n, c in web:
            g.write(f"{n}\t{c}\t{','.join(sorted(set(srcs[n])))}\n")
    counts = {c: sum(1 for _, x in web if x == c) for c in CATS}
    manifest = {"version": a.version, "built": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "sources": manifest_src,
                "domains": {"web": n_web, "play": n_play, **counts}, "dropped": dropped,
                "files": {f: sha256_file(os.path.join(a.out, f)) for f in ("blocklists-web.bin", "blocklists-play.bin")}}
    with open(os.path.join(a.out, "manifest.json"), "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, indent=2)
    # 18.7: the entries never blocked because they are working parts of an official brand's app (reviewable)
    allow_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "allow-defaults.json")
    with open(allow_path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"version": a.version, "rule": "official brand domain + no ad/tracking word in the host name",
                   "count": len(allow_defaults), "entries": sorted(allow_defaults, key=lambda e: (e["brand"], e["domain"]))},
                  fh, indent=1, ensure_ascii=False)
    print(json.dumps(manifest["domains"]), json.dumps(dropped))


if __name__ == "__main__":
    main()
