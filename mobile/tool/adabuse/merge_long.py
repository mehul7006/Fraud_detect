"""6.1 long run: per app keep the PEAK notifications/hour and self-launches over all audit rounds, OR the
permission flags, and note launcher-icon / namespace traits.  merge_long.py OUTDIR
Writes measured-long.csv (AdAbuseAccuracyTest format) and traits-long.csv (extra traits, for analysis)."""
import json, sys
from pathlib import Path

O = Path(sys.argv[1])
inst = [json.loads(l) for l in open(O / 'installed.jsonl', encoding='utf-8') if l.strip()]
agg, rounds = {}, set()
for l in open(O / 'audit.jsonl', encoding='utf-8'):
    try:
        r = json.loads(l)
    except Exception:
        continue
    a, p = r['a'], r['a']['packageName']; rounds.add(r['round'])
    g = agg.setdefault(p, {'notif': 0, 'self': 0, 'flags': {}, 'seen': 0})
    g['notif'] = max(g['notif'], a.get('notifPerHour') or 0)
    g['self'] = max(g['self'], a.get('selfLaunchCount') or 0)
    g['seen'] += 1
    for k in ('hasOverlayPermission', 'hasSmsPermission', 'hasAccessibility', 'hasNotificationListener',
              'hasFullScreenIntent', 'isSystem'):
        g['flags'][k] = g['flags'].get(k, False) or bool(a.get(k))
icons = [json.loads(l) for l in open(O / 'launcher.jsonl', encoding='utf-8') if l.strip()]
last_icons = set(icons[-1]['withIcon'].split()) if icons else set()
first_icons = set(icons[0]['withIcon'].split()) if icons else set()
b = lambda v: 'true' if v else 'false'
rows, traits, missing = [], [], []
for x in inst:
    p = x['package']; g = agg.get(p)
    if not g:
        missing.append(p); continue
    f = g['flags']
    name = (x['source'].split(':')[0] + ' ' + p + (' ' + x['source'][3:15] if x['source'].startswith('mb:') else '')).replace(',', ' ')
    rows.append(','.join([x['label'], name, str(g['notif']), b(f['hasOverlayPermission']), b(f['hasSmsPermission']),
                          b(f['hasAccessibility']), b(f['hasNotificationListener']), b(f['hasFullScreenIntent']),
                          b(f['isSystem']), str(g['self'])]))
    traits.append(','.join([x['label'], p, b(p in first_icons), b(p in last_icons),
                            b(p.startswith(('com.android.', 'com.google.android.', 'android.'))), str(g['seen'])]))
(O / 'measured-long.csv').write_text('label,name,notifPerHour,overlay,sms,accessibility,listener,fullScreen,isSystem,selfLaunch\n'
                                     + '\n'.join(rows) + '\n', encoding='utf-8')
(O / 'traits-long.csv').write_text('label,package,iconAtStart,iconAtEnd,systemNamespace,auditRounds\n' + '\n'.join(traits) + '\n',
                                   encoding='utf-8')
print(f'rounds {len(rounds)}; rows {len(rows)} (ABUSIVE {sum(r.startswith("ABUSIVE") for r in rows)}, '
      f'BENIGN {sum(r.startswith("BENIGN") for r in rows)}); not audited: {missing}')
