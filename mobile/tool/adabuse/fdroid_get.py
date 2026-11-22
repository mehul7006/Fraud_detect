"""6.1 research: download real benign APKs from F-Droid (open source, no ads).  fdroid_get.py OUTDIR
Chosen to include HARD negatives: apps that legitimately use SMS, notification access, accessibility, overlay
or full-screen alerts - the same signals adware uses."""
import json, sys, urllib.request
from pathlib import Path

OUT = Path(sys.argv[1]); OUT.mkdir(parents=True, exist_ok=True)
PKGS = [
    'com.x8bit.bitwarden', 'com.kunzisoft.keepass.libre',            # accessibility (autofill)
    'org.kde.kdeconnect_tp', 'nodomain.freeyourgadget.gadgetbridge',  # notification access + SMS
    'org.schabi.newpipe', 'org.videolan.vlc',                       # overlay / popup player
    'org.fossify.clock', 'org.fossify.calendar', 'ws.xsoh.etar',    # full-screen alarms / reminders
    'org.fossify.messages', 'com.moez.QKSMS', 'org.fossify.phone',  # SMS / phone
    'com.fsck.k9', 'eu.siacs.conversations', 'im.vector.app',       # busy notifications (mail / chat)
    'org.briarproject.briar.android', 'com.keylesspalace.tusky', 'ml.docilealligator.infinityforreddit',
    'de.danoeh.antennapod', 'org.mozilla.fennec_fdroid', 'com.nutomic.syncthingandroid',
    'com.nextcloud.client', 'at.bitfire.davdroid', 'eu.faircode.netguard', 'com.beemdevelopment.aegis',
    'net.gsantner.markor', 'com.termux', 'org.fossify.gallery', 'org.fossify.filemanager', 'org.fossify.notes',
    'com.github.libretube', 'org.wikipedia', 'net.osmand.plus', 'org.thoughtcrime.securesms',
]
UA = {'User-Agent': 'fraudshield-research'}
idx_path = OUT / 'index-v1.json'
if not idx_path.exists():
    idx_path.write_bytes(urllib.request.urlopen(urllib.request.Request('https://f-droid.org/repo/index-v1.json', headers=UA), timeout=300).read())
idx = json.loads(idx_path.read_text(encoding='utf-8'))
apps = {a['packageName']: a for a in idx['apps']}
got = []
for p in PKGS:
    if p not in apps or p not in idx['packages']:
        print('not on F-Droid:', p); continue
    want = int(apps[p].get('suggestedVersionCode') or 0)
    vers = idx['packages'][p]
    pick = next((v for v in vers if v['versionCode'] == want), vers[0])
    # prefer an APK that runs on x86_64 (or has no native code)
    for v in [pick] + vers:
        nc = v.get('nativecode') or []
        if not nc or 'x86_64' in nc:
            pick = v; break
    f = OUT / pick['apkName']
    if not f.exists():
        try:
            f.write_bytes(urllib.request.urlopen(urllib.request.Request('https://f-droid.org/repo/' + pick['apkName'], headers=UA), timeout=300).read())
        except Exception as e:
            print('download failed', p, e); continue
    got.append({'package': p, 'apk': f.name, 'name': apps[p].get('name') or apps[p].get('localized', {}).get('en-US', {}).get('name', p)})
    print('ok', p, f.name)
json.dump(got, open(OUT / 'benign.json', 'w', encoding='utf-8'), indent=1)
print('benign apks:', len(got))
