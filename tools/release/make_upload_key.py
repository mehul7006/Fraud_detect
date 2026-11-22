"""9.5: creates the Play upload key once (random passwords), keeps it out of git.
  key file  -> _devtools/keys/fraudshield-upload.jks   (back it up!)
  passwords -> .env  RELEASE_STORE_PASSWORD / RELEASE_KEY_PASSWORD / RELEASE_KEY_ALIAS
  mobile/android/key.properties is (re)written from .env.  Never prints a password."""
import secrets, string, subprocess
from pathlib import Path

R = Path(__file__).resolve().parents[2]
env = R / '.env'
lines = env.read_text(encoding='utf-8').splitlines() if env.exists() else []
vals = {l.split('=', 1)[0]: l.split('=', 1)[1] for l in lines if '=' in l and not l.startswith('#')}
jks = R / '_devtools' / 'keys' / 'fraudshield-upload.jks'
if 'RELEASE_STORE_PASSWORD' not in vals:
    pw = ''.join(secrets.choice(string.ascii_letters + string.digits) for _ in range(32))
    vals.update(RELEASE_STORE_PASSWORD=pw, RELEASE_KEY_PASSWORD=pw, RELEASE_KEY_ALIAS='upload')
    lines += ['', '# 9.5 Play upload key (file: _devtools/keys/fraudshield-upload.jks - BACK IT UP). Rotate if leaked.',
              f'RELEASE_STORE_PASSWORD={pw}', f'RELEASE_KEY_PASSWORD={pw}', 'RELEASE_KEY_ALIAS=upload']
    env.write_text('\n'.join(lines) + '\n', encoding='utf-8')
if not jks.exists():
    jks.parent.mkdir(parents=True, exist_ok=True)
    keytool = r'E:\Android\jdk-17.0.2\bin\keytool.exe'
    subprocess.run([keytool, '-genkeypair', '-v', '-keystore', str(jks), '-storetype', 'PKCS12', '-alias', vals['RELEASE_KEY_ALIAS'],
                    '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10000',
                    '-dname', 'CN=FraudShield upload key, O=FraudShield, C=IN',
                    '-storepass', vals['RELEASE_STORE_PASSWORD'], '-keypass', vals['RELEASE_KEY_PASSWORD']],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
kp = R / 'mobile' / 'android' / 'key.properties'
kp.write_text(f"storeFile={jks.as_posix()}\nstorePassword={vals['RELEASE_STORE_PASSWORD']}\n"
              f"keyAlias={vals['RELEASE_KEY_ALIAS']}\nkeyPassword={vals['RELEASE_KEY_PASSWORD']}\n", encoding='utf-8')
print('upload key ready:', jks.name, '| key.properties written | passwords in .env (not printed)')
