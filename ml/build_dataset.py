"""11.1 training data for the link model. One command:

    python ml/build_dataset.py            (downloads into _devtools/ml, writes ml/data/dataset.csv)

PHISH  : Phishing.Database (MIT) active + inactive phishing links.
BENIGN : real page addresses from the Common Crawl URL index (random index blocks), kept only when the site is in the
         Tranco top 1M, plus Tranco home pages. Licences: ml/DATA-LICENSES.md.
Cleaning: one row per URL; at most 3 phishing / 5 benign rows per registrable domain (no single host dominates);
phishing rows on top-100k sites dropped (the scanner never flags those anyway - 11.5 policy, and they are label noise);
train/test split BY DOMAIN (a domain is never in both), so the test score is honest. Fixed seed -> reproducible
for the same downloaded snapshots (their SHA-256 is written to ml/data/manifest.json).
Standard library only.
"""
import csv
import gzip
import hashlib
import io
import json
import os
import random
import sys
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.environ.get("ML_CACHE", os.path.join(ROOT, "_devtools", "ml"))
OUT = os.path.join(ROOT, "ml", "data")
PSL = os.path.join(ROOT, "backend", "src", "main", "resources", "psl", "public_suffix_list.dat")
POPULAR = os.path.join(ROOT, "backend", "src", "main", "resources", "seed", "popular-domains.txt")
CC_CRAWL = "CC-MAIN-2026-43"
N_EACH = 30000
CC_BLOCKS = 2600
SEED = 20261123
UA = {"User-Agent": "FraudShield-dataset-builder/1.0 (research; one-off download)"}

SOURCES = {
    "phishing-links-ACTIVE.txt": "https://raw.githubusercontent.com/Phishing-Database/Phishing.Database/master/phishing-links-ACTIVE.txt",
    "phishing-links-INACTIVE.txt": "https://raw.githubusercontent.com/Phishing-Database/Phishing.Database/master/phishing-links-INACTIVE.txt",
    "tranco-top-1m.csv.zip": "https://tranco-list.eu/top-1m.csv.zip",
    "cc-cluster.idx": f"https://data.commoncrawl.org/cc-index/collections/{CC_CRAWL}/indexes/cluster.idx",
}


def fetch(name):
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        print("downloading", name, file=sys.stderr)
        req = urllib.request.Request(SOURCES[name], headers=UA)
        with urllib.request.urlopen(req, timeout=600) as r, open(path + ".part", "wb") as f:
            while chunk := r.read(1 << 20):
                f.write(chunk)
        os.replace(path + ".part", path)
    return path


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(1 << 20):
            h.update(chunk)
    return h.hexdigest()


class Psl:
    """Public Suffix List, same file and same rules as the Java DomainNormalizer."""

    def __init__(self, path):
        self.rules, self.wild, self.exc = set(), set(), set()
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if not line or line.startswith("//"):
                continue
            line = line.split()[0].lower()
            if line.startswith("!"):
                self.exc.add(line[1:])
            elif line.startswith("*."):
                self.wild.add(line[2:])
            else:
                self.rules.add(line)

    def registrable(self, host):
        host = host.lower().strip(".")
        if host.startswith("www.") and host.count(".") > 1:
            host = host[4:]
        p = host.split(".")
        suffix = p[-1]
        for i in range(len(p)):
            cand = ".".join(p[i:])
            if cand in self.exc:
                suffix = cand.split(".", 1)[1]
                break
            if cand in self.rules or (i + 1 < len(p) and ".".join(p[i + 1:]) in self.wild):
                suffix = cand
                break
        if host == suffix:
            return host
        rest = host[: -len(suffix) - 1]
        return rest.rsplit(".", 1)[-1] + "." + suffix


def host_of(url):
    s = url.split("://", 1)[-1]
    for c in "/?#":
        s = s.split(c, 1)[0]
    s = s.rsplit("@", 1)[-1]
    if s.startswith("["):
        return s.split("]")[0] + "]"
    return s.split(":", 1)[0].lower()


def main():
    os.makedirs(CACHE, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    rnd = random.Random(SEED)
    psl = Psl(PSL)
    popular = {l.strip() for l in open(POPULAR, encoding="utf-8") if l.strip() and not l.startswith("#")}

    # Tranco top 1M = "real, established site"
    tz = fetch("tranco-top-1m.csv.zip")
    with zipfile.ZipFile(tz) as z:
        name = z.namelist()[0]
        tranco = [l.split(",", 1)[1].strip() for l in io.TextIOWrapper(z.open(name), encoding="utf-8")]
    tranco_set = set(tranco)

    # ---- phishing
    phish, seen, per = [], set(), {}
    for fname in ("phishing-links-ACTIVE.txt", "phishing-links-INACTIVE.txt"):
        for line in open(fetch(fname), encoding="utf-8", errors="ignore"):
            u = line.strip()
            if not u.lower().startswith(("http://", "https://")) or len(u) > 2048 or u in seen or "\t" in u:
                continue
            h = host_of(u)
            if not h or " " in h:
                continue
            reg = psl.registrable(h)
            if reg in popular:
                continue
            seen.add(u)
            phish.append((u, reg))
    rnd.shuffle(phish)
    kept = []
    for u, reg in phish:
        if per.get(reg, 0) >= 3:
            continue
        per[reg] = per.get(reg, 0) + 1
        kept.append((u, reg, "phishing.database"))
    phish = kept[:N_EACH]
    phish_hosts = {host_of(u) for u, _, _ in phish}

    # ---- benign: Common Crawl index blocks. A block holds ~3000 consecutive URLs, usually of one or two sites, so
    # pick blocks that START with a different Tranco site each (the index line names its first URL "com,example)/x")
    by_site = {}
    for b in open(fetch("cc-cluster.idx"), encoding="utf-8").read().splitlines():
        site = ".".join(reversed(b.split(")", 1)[0].split(",")))
        reg = psl.registrable(site.split(":")[0]) if site else ""
        if reg in tranco_set and reg not in by_site:
            by_site[reg] = b
    blocks = rnd.sample(sorted(by_site.values()), min(CC_BLOCKS, len(by_site)))
    os.makedirs(os.path.join(CACHE, "cc-blocks"), exist_ok=True)

    def get_block(b):
        _, file, off, length, _ = b.split("\t")
        cpath = os.path.join(CACHE, "cc-blocks", f"{file}-{off}.gz")
        if os.path.exists(cpath):
            return cpath
        url = f"https://data.commoncrawl.org/cc-index/collections/{CC_CRAWL}/indexes/{file}"
        req = urllib.request.Request(url, headers={**UA, "Range": f"bytes={off}-{int(off) + int(length) - 1}"})
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=120) as r:
                    data = r.read()
                open(cpath + ".part", "wb").write(data)
                os.replace(cpath + ".part", cpath)
                return cpath
            except Exception as e:  # noqa: BLE001 - retry, then skip this block
                if attempt == 2:
                    print("skip block", file, off, e, file=sys.stderr)
        return None

    from concurrent.futures import ThreadPoolExecutor
    with ThreadPoolExecutor(8) as ex:
        paths = [p for p in ex.map(get_block, blocks) if p]
    print("cc blocks", len(paths), "of", len(by_site), "candidate sites", file=sys.stderr)
    benign, per = [], {}
    for cpath in paths:
        for line in gzip.decompress(open(cpath, "rb").read()).decode("utf-8", "ignore").splitlines():
            try:
                rec = json.loads(line.split(" ", 2)[2])
            except (IndexError, ValueError):
                continue
            u = rec.get("url", "")
            if rec.get("status") != "200" or "html" not in rec.get("mime", "") or len(u) > 2048 or "\t" in u:
                continue
            h = host_of(u)
            reg = psl.registrable(h)
            if reg not in tranco_set or h in phish_hosts or per.get(reg, 0) >= 5:
                continue
            per[reg] = per.get(reg, 0) + 1
            benign.append((u, reg, "commoncrawl+tranco"))
    # home pages of random Tranco sites (a third of the benign rows) - phishing links are often bare domains too
    for d in rnd.sample(tranco, 8000):
        if per.get(d, 0) < 5 and d not in phish_hosts:
            per[d] = per.get(d, 0) + 1
            benign.append((rnd.choice(["https://", "https://www.", "http://"]) + d + "/", d, "tranco-home"))
    rnd.shuffle(benign)
    print("available: phish", len(phish), "benign", len(benign), file=sys.stderr)
    n = min(len(phish), len(benign), N_EACH)
    rows = [(u, 1, r, s) for u, r, s in phish[:n]] + [(u, 0, r, s) for u, r, s in benign[:n]]
    # 11.8: reviewed user reports (GET /admin/feedback/labels.tsv -> ml/data/feedback.tsv) win over the feeds
    fb_path = os.environ.get("FEEDBACK_TSV", os.path.join(OUT, "feedback.tsv"))
    fb = 0
    if os.path.exists(fb_path):
        reported = {}
        for line in open(fb_path, encoding="utf-8"):
            p = line.rstrip("\n").split("\t")
            if len(p) == 3 and p[0] in ("0", "1") and p[2].startswith(("http://", "https://")):
                reported[psl.registrable(host_of(p[2]))] = (p[2], int(p[0]))
        rows = [r for r in rows if r[2] not in reported]
        rows += [(u, y, reg, "user-report") for reg, (u, y) in reported.items()]
        fb = len(reported)

    def split(reg):  # by domain (a domain is in one part only): 65 % train, 15 % val (threshold), 20 % test
        b = int(hashlib.sha256(reg.encode()).hexdigest()[:8], 16) % 20
        return "test" if b < 4 else "val" if b < 7 else "train"

    with open(os.path.join(OUT, "dataset.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["url", "label", "registrable", "source", "split"])
        for u, y, reg, src in rows:
            w.writerow([u, y, reg, src, split(reg)])
    # same rows as TSV for the Java feature dump (label <TAB> split <TAB> url)
    with open(os.path.join(OUT, "dataset.tsv"), "w", encoding="utf-8", newline="") as f:
        for u, y, reg, src in rows:
            f.write(f"{y}\t{split(reg)}\t{u}\n")
    manifest = {
        "seed": SEED, "rows": len(rows), "phish": n, "benign": n, "user_reports": fb, "common_crawl": CC_CRAWL, "cc_blocks": CC_BLOCKS,
        "inputs": {k: sha256(os.path.join(CACHE, k)) for k in SOURCES},
        "test_rows": sum(split(r) == "test" for _, _, r, _ in rows),
    }
    json.dump(manifest, open(os.path.join(OUT, "manifest.json"), "w"), indent=2)
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
