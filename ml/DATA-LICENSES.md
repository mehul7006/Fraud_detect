# 11.1 Training data - sources and licences (checked 2026-11-23)

| Source | Used for | Licence / terms | Notes |
|---|---|---|---|
| [Phishing.Database](https://github.com/Phishing-Database/Phishing.Database) - `phishing-links-ACTIVE.txt`, `phishing-links-INACTIVE.txt` | phishing rows | **MIT** | ~920k links; we sample 30k (max 3 per domain) |
| [Common Crawl URL index](https://commoncrawl.org/) `CC-MAIN-2026-43` (2,600 random index blocks) | legitimate deep links | [Common Crawl Terms of Use](https://commoncrawl.org/terms-of-use) - free, research and commercial | only the URL column is used, no page content |
| [Tranco top 1M](https://tranco-list.eu) | "is an established site" filter + legitimate home pages | free; cite Le Pochat et al., NDSS 2019 | also the source of `seed/popular-domains.txt` |
| User reports (`GET /admin/feedback/labels.tsv`, 11.8) | corrections | our own data; host only, reviewed by an admin | merged by `build_dataset.py` (`ml/data/feedback.tsv`) |

**Not used:** OpenPhish community feed (terms limit commercial use; only ~300 live URLs), PhishTank (needs an account
key and has share-alike terms), URLhaus (malware distribution, not phishing - a candidate for a later APK-link model).

Downloads are cached in `_devtools/ml` (git-ignored); `ml/data/manifest.json` records the SHA-256 of every input so a
training run can be repeated exactly.
