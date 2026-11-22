#!/usr/bin/env bash
export MSYS_NO_PATHCONV=1
# 3.4: what Android really hands the app for the 4 forwarding codes, per Android version and language.
# Usage: capture34.sh <api-level> <serial>   (emulator with google_apis image = adb root works; backend on :18080)
# Writes capture-<api>.jsonl next to this script: one line per (language, forwarding state, code).
API=$1; S=$2
D=$(cygpath -m "$(cd "$(dirname "$0")" && pwd)")
A="adb -s $S"
PKG=com.fraudshield.app
APK="E:/fraud detection/mobile/build/app/outputs/flutter-apk/app-debug.apk"
NUM=9876543210
OUT="$D/capture-$API.jsonl"; : > "$OUT"
dump() { for i in 1 2 3; do $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $A exec-out cat /sdcard/u.xml > "$D/u.xml" 2>/dev/null; grep -q '</hierarchy>' "$D/u.xml" && break; sleep 1; done
  if grep -q "isn.t responding" "$D/u.xml"; then local xy; xy=$(python "$D/ui.py" tap 'aerr_wait|^Wait' "$D/u.xml") && $A shell input tap $xy; sleep 4; fi; }
has() { dump; python "$D/ui.py" has "$1" "$D/u.xml"; }
tap() { dump; local xy; xy=$(python "$D/ui.py" tap "$1" "$D/u.xml") || { echo "  (not found: $1)"; return 1; }; $A shell input tap $xy; }
waitfor() { for i in $(seq 1 ${2:-15}); do has "$1" && return 0; sleep 2; done; return 1; }
typeslow() { local t=$1 i; for ((i=0; i<${#t}; i+=3)); do $A shell input text "${t:i:3}"; sleep 0.4; done; }
mmi() { # dial a forwarding MMI code as root (no dialer UI), then close the result dialog
  $A shell am start -a android.intent.action.CALL -d "tel:$(python -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$1")" >/dev/null 2>&1
  sleep 7; dump; local msg; msg=$(python "$D/ui.py" texts "$D/u.xml" | grep -m1 "android:id/message" | cut -d"'" -f2 | cut -d'|' -f1)
  if [ -z "$msg" ]; then # Android 14: CALL from the shell is ignored -> type it in the dialer and press call
    $A shell am start -a android.intent.action.DIAL -d "tel:$(python -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$1")" >/dev/null 2>&1; sleep 4
    tap 'dialpad_voice_call_button|content-desc="dial"|^ \| dial \|' >/dev/null; sleep 7; dump
    msg=$(python "$D/ui.py" texts "$D/u.xml" | grep -m1 "android:id/message" | cut -d"'" -f2 | cut -d'|' -f1)
  fi
  echo "  $1 -> ${msg:-?}"; $A shell input keyevent KEYCODE_ENTER; sleep 1; $A shell input keyevent KEYCODE_BACK; sleep 1; }
capture() { # $1 = language, $2 = forwarding state label
  $A shell am force-stop $PKG; $A logcat -c
  $A shell am start -n $PKG/.MainActivity >/dev/null; sleep 30
  local flag; flag=$($A shell dumpsys telephony.registry | grep -m1 mCallForwarding | tr -d ' \r' | cut -d= -f2)
  dump; local tile; tile=$(python "$D/ui.py" texts "$D/u.xml" | grep -m1 "Call forwarding" | cut -d'|' -f2 | tr '\n' ' ')
  $A logcat -d -v raw -s flutter:I > "$D/flog.txt"
  python -I "$D/parse_capture.py" "$D/flog.txt" "$API" "$1" "$2" "$flag" "$tile" >> "$OUT"
  echo "  [$1 / $2] flag=$flag tile=$(echo "$tile" | head -c 70)"; }
setlocale() {
  $A shell setprop persist.sys.locale "$1"; $A shell stop; sleep 2; $A shell start
  for i in $(seq 1 60); do sleep 3; $A shell dumpsys activity activities 2>/dev/null | grep -q "mResumedActivity\|ResumedActivity" && break; done; sleep 15
  $A shell input keyevent KEYCODE_HOME; }

echo "== capture API $API ($S) =="
$A root >/dev/null; sleep 3; $A wait-for-device
$A shell getprop ro.build.version.release | tr -d '\r' | sed 's/^/Android version: /'
if ! $A shell pm list packages | grep -q "$PKG"; then
  $A install "$APK" >/dev/null || { echo "install failed"; exit 1; }
else $A install -r "$APK" >/dev/null; fi
[ "$API" -ge 33 ] && $A shell pm grant $PKG android.permission.POST_NOTIFICATIONS
$A shell pm grant $PKG android.permission.CALL_PHONE; $A shell pm grant $PKG android.permission.READ_PHONE_STATE
$A shell am start -n $PKG/.MainActivity >/dev/null; sleep 25
if has 'New here\? Register'; then
  for i in 1 2 3 4; do tap 'New here\? Register'; sleep 2; has 'Create account' && break; done
  tap 'EditText$'; sleep 1; typeslow "c34x${API}x$RANDOM@t.com"
  tap 'EditText PASSWORD'; sleep 1; typeslow "password123"; sleep 1; tap 'Create account'
  waitfor 'Call forwarding' 30 || echo "  dashboard did not appear after register"
fi
for LOC in en-US hi-IN; do
  setlocale $LOC
  mmi "##002#";                      capture $LOC OFF
  mmi "**21*$NUM#";                  capture $LOC ON_ALL_CALLS
  mmi "##002#"; mmi "**67*$NUM#";    capture $LOC ON_WHEN_BUSY
  mmi "##002#"
done
setlocale en-US
echo "== done API $API: $(wc -l < "$OUT") rows =="
