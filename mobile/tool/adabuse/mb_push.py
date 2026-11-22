"""6.1 research: download one MalwareBazaar sample and stream the APK straight into the emulator.
The APK is never written to this PC's disk (zip is decrypted in memory, bytes piped to `adb exec-in`).
mb_push.py SHA256 SERIAL REMOTE_PATH"""
import io, subprocess, sys, urllib.parse, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / '_devtools' / 'adabuse' / 'pylib'))
import pyzipper  # noqa: E402

sha, serial, remote = sys.argv[1:4]
KEY = next(l.split('=', 1)[1].strip() for l in (ROOT / '.env').read_text(encoding='utf-8').splitlines()
           if l.startswith('MALWAREBAZAAR_API_KEY='))
req = urllib.request.Request('https://mb-api.abuse.ch/api/v1/',
                             data=urllib.parse.urlencode({'query': 'get_file', 'sha256_hash': sha}).encode(),
                             headers={'Auth-Key': KEY, 'User-Agent': 'fraudshield-research'})
import time
time.sleep(8)  # be polite: MalwareBazaar rate-limits bursts (HTTP 429)
for attempt in range(5):
    try:
        blob = urllib.request.urlopen(req, timeout=120).read()
        break
    except urllib.error.HTTPError as e:
        if e.code != 429 or attempt == 4:
            raise
        time.sleep(30 * (attempt + 1))
if not blob.startswith(b'PK'):
    sys.exit('not a zip: ' + blob[:120].decode('utf-8', 'replace'))
with pyzipper.AESZipFile(io.BytesIO(blob)) as z:
    z.setpassword(b'infected')
    data = z.read(z.namelist()[0])
adb = r'E:\Android\Sdk\platform-tools\adb.exe'
# One big `exec-in` stream gets cut short on Windows for files over ~10 MB (seen: every sample > 16 MB arrived
# truncated). Send 4 MB pieces, then check the SHA-256 on the device before anyone installs it.
import hashlib
# Even 4 MB pieces arrived damaged for a 17 MB sample; 512 KB pieces + one full retry, then verify SHA-256.
CHUNK = 512 * 1024
want = hashlib.sha256(data).hexdigest()
for attempt in range(2):
    subprocess.run([adb, '-s', serial, 'shell', f'rm -f {remote}'], check=True)
    for i in range(0, len(data), CHUNK):
        p = subprocess.run([adb, '-s', serial, 'exec-in', f'cat >> {remote}'], input=data[i:i + CHUNK])
        if p.returncode:
            break
    got = subprocess.run([adb, '-s', serial, 'shell', f'sha256sum {remote}'], capture_output=True, text=True).stdout.split()
    if got and got[0] == want:
        sys.exit(0)
subprocess.run([adb, '-s', serial, 'shell', f'rm -f {remote}'])
sys.exit('transfer damaged (sha256 mismatch after 2 tries)')
