"""6.1: join the apps installed under test with FraudShield's audit of them -> labelled CSV in the
AdAbuseAccuracyTest format.  to_csv.py INSTALLED.jsonl AUDIT.jsonl OUT.csv"""
import json, sys

inst = [json.loads(l) for l in open(sys.argv[1], encoding='utf-8') if l.strip()]
audit = {}
for l in open(sys.argv[2], encoding='utf-8'):
    try:
        a = json.loads(l)
        audit[a['packageName']] = a
    except Exception:
        pass
b = lambda v: 'true' if v is True else 'false'
rows, missing = [], []
for x in inst:
    a = audit.get(x['package'])
    if not a:
        missing.append(x['package']); continue
    name = (x['source'].split(':')[0] + ' ' + x['package'] + (' ' + x['source'][3:15] if x['source'].startswith('mb:') else '')).replace(',', ' ')
    rows.append(','.join([x['label'], name, str(a.get('notifPerHour') or 0), b(a.get('hasOverlayPermission')),
                          b(a.get('hasSmsPermission')), b(a.get('hasAccessibility')), b(a.get('hasNotificationListener')),
                          b(a.get('hasFullScreenIntent')), b(a.get('isSystem')), str(a.get('selfLaunchCount') or 0)]))
with open(sys.argv[3], 'w', encoding='utf-8', newline='\n') as f:
    f.write('label,name,notifPerHour,overlay,sms,accessibility,listener,fullScreen,isSystem,selfLaunch\n')
    f.write('\n'.join(rows) + '\n')
print(f'rows {len(rows)} (ABUSIVE {sum(r.startswith("ABUSIVE") for r in rows)}, BENIGN {sum(r.startswith("BENIGN") for r in rows)}); not audited: {missing}')
