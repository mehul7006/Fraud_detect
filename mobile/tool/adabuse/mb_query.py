"""6.1 research: list Android APK samples on MalwareBazaar for some ad-fraud tags (metadata only).
Reads MALWAREBAZAAR_API_KEY from the repo .env, never prints it.  mb_query.py OUT.json"""
import json, sys, urllib.parse, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
KEY = next(l.split('=', 1)[1].strip() for l in (ROOT / '.env').read_text(encoding='utf-8').splitlines()
           if l.startswith('MALWAREBAZAAR_API_KEY='))
TAGS = ['adware', 'HiddenAds', 'hiddad', 'hiddenad', 'Joker', 'clicker', 'AdDisplay', 'adfraud', 'Ewind', 'Mobidash', 'Hummer', 'Triada']


def api(**form):
    req = urllib.request.Request('https://mb-api.abuse.ch/api/v1/', data=urllib.parse.urlencode(form).encode(),
                                 headers={'Auth-Key': KEY, 'User-Agent': 'fraudshield-research'})
    return json.loads(urllib.request.urlopen(req, timeout=60).read())


seen = {}
for tag in TAGS:
    try:
        r = api(query='get_taginfo', tag=tag, limit='1000')
    except Exception as e:
        print(f'{tag}: error {e}'); continue
    rows = r.get('data') if isinstance(r.get('data'), list) else []
    apks = [x for x in rows if x.get('file_type') == 'apk']
    print(f'{tag}: {r.get("query_status")} total={len(rows)} apk={len(apks)}')
    for x in apks:
        seen.setdefault(x['sha256_hash'], {'sha256': x['sha256_hash'], 'signature': x.get('signature'),
                                           'tags': x.get('tags'), 'size': x.get('file_size'),
                                           'first_seen': x.get('first_seen'), 'name': x.get('file_name')})
json.dump(list(seen.values()), open(sys.argv[1], 'w', encoding='utf-8'), indent=1)
print('distinct apk samples:', len(seen))
