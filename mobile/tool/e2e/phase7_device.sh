#!/usr/bin/env bash
export MSYS_NO_PATHCONV=1
# Phase 7 device checks 7.5-7.10 on one emulator (Google APIs image). Backend on :18080 (dev, docker fs-api/fs-db).
# Usage: phase7_device.sh <serial>
S=${1:-emulator-5562}
D=$(cygpath -m "$(cd "$(dirname "$0")" && pwd)")
A="/e/Android/Sdk/platform-tools/adb -s $S"; P=com.fraudshield.app
APK="E:/fraud detection/mobile/build/app/outputs/flutter-apk/app-debug.apk"
OUT="$D/result-phase7.txt"; : > "$OUT"
pass() { echo "PASS | $1" | tee -a "$OUT"; }
fail() { echo "FAIL | $1" | tee -a "$OUT"; }
note() { echo "     $1" | tee -a "$OUT"; }
db() { docker exec fs-db psql -U fraud -d fraudshield -tAc "$1" | tr -d '\r'; }
dump() { for i in 1 2 3; do $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $A exec-out cat /sdcard/u.xml > "$D/u.xml"; grep -q '</hierarchy>' "$D/u.xml" && break; sleep 1; done
  if grep -q "isn.t responding" "$D/u.xml"; then xy=$(python "$D/ui.py" tap 'aerr_wait|^Wait' "$D/u.xml") && $A shell input tap $xy; sleep 4; dump; fi; }
has() { dump; python "$D/ui.py" has "$1" "$D/u.xml"; }
tap() { dump; local xy; xy=$(python "$D/ui.py" tap "$1" "$D/u.xml") || { note "(not found: $1)"; return 1; }; $A shell input tap $xy; }
waitfor() { for i in $(seq 1 ${2:-15}); do has "$1" && return 0; sleep 2; done; return 1; }
typeslow() { local t=$1 i; for ((i=0; i<${#t}; i+=3)); do $A shell input text "${t:i:3}"; sleep 0.4; done; }
front() { $A shell am start -n $P/.MainActivity >/dev/null; sleep 4; }
# close a popup for real: tap its button until its title is gone (a missed tap would block the alert queue)
dismiss() { for i in 1 2 3 4; do has "$1" || return 0; tap "$2"; sleep 2; done; has "$1" && note "(could not close: $1)"; }

echo "== Phase 7 device checks on $S (Android $($A shell getprop ro.build.version.release | tr -d '\r')) ==" | tee -a "$OUT"
for k in window_animation_scale transition_animation_scale animator_duration_scale; do $A shell settings put global $k 0; done
# clean start: test SMS from earlier runs would be re-posted by the Messages app and (correctly) alert again
$A shell pm clear com.google.android.apps.messaging >/dev/null 2>&1; $A shell cmd notification cancel-all 2>/dev/null
$A uninstall $P >/dev/null 2>&1; $A install -g "$APK" >/dev/null || { fail "install"; exit 1; }
$A shell cmd notification allow_listener $P/$P.AdNotificationListener
$A shell appops set --uid $P USE_FULL_SCREEN_INTENT default 2>/dev/null
front; sleep 25
EMAIL="p7x$RANDOM@t.com"
for i in 1 2 3 4; do tap 'New here\? Register'; sleep 2; has 'Create account' && break; done
tap 'EditText$'; sleep 1; typeslow "$EMAIL"; tap 'EditText PASSWORD'; sleep 1; typeslow "password123"; sleep 1; tap 'Create account'
waitfor 'Agree and continue' 20 && tap 'Agree and continue'
waitfor 'Call forwarding' 30 || { fail "dashboard after register + consent"; exit 1; }
UID_=$(db "select id from users where email='$EMAIL'")
sleep 20

# ---- 7.5 device fingerprint -> backend DTO
row=$(db "select model is not null and model<>'', os_version like 'Android%', length(sim_hash)=64 or sim_hash is null, length(fingerprint), clone_risk_score from devices where user_id=$UID_")
note "device row: $row"
IFS='|' read -r m o s fp score <<<"$row"
[ "$m" = t ] && [ "$o" = t ] && [ "$fp" = 64 ] && [ "${score:-0}" -ge 25 ] \
  && pass "7.5 device info stored as the backend expects (model, Android version, SHA-256 fingerprint; emulator scored +25)" \
  || fail "7.5 device info ($row)"

# ---- 7.6 heartbeat updates last_seen
t1=$(db "select extract(epoch from last_seen) from devices where user_id=$UID_")
$A shell am force-stop $P; front; sleep 25
t2=$(db "select extract(epoch from last_seen) from devices where user_id=$UID_")
python -c "import sys; sys.exit(0 if float('$t2') > float('$t1') else 1)" && pass "7.6 check-in updates last_seen ($t1 -> $t2)" || fail "7.6 last_seen did not move ($t1 / $t2)"

# ---- 7.10 part 1: Android 14 full-screen restriction detected -> card
if [ "$($A shell getprop ro.build.version.sdk | tr -d '\r')" -ge 34 ]; then
  $A shell appops set --uid $P USE_FULL_SCREEN_INTENT deny;  # the UID mode is what Android checks (-g install set it to allow)
  $A shell input keyevent KEYCODE_HOME; sleep 2; front; sleep 6
  has 'Allow full-screen security alerts' && pass "7.10 Android 14: full-screen alerts not allowed -> card shown" || fail "7.10 no card while not allowed"
  tap 'Allow full-screen security alerts'; sleep 1; tap '^ \| Allow \|' >/dev/null; sleep 3
  has 'full.screen|Full.screen|Allow full screen' && $A shell dumpsys window | grep -m1 mCurrentFocus | grep -qi settings     && pass "7.10 card opens Android's full-screen-notifications page" || fail "7.10 card did not open the full-screen page ($($A shell dumpsys window | grep -m1 mCurrentFocus | tr -s ' '))"
  $A shell input keyevent KEYCODE_BACK; sleep 2
  $A shell appops set --uid $P USE_FULL_SCREEN_INTENT allow; $A shell input keyevent KEYCODE_HOME; sleep 2; front; sleep 6
  has 'Allow full-screen security alerts' && fail "7.10 card still shown after allowing" || pass "7.10 after allowing: card gone (refreshed on return)"
fi

# ---- 7.7 login-code detector (app on screen)
$A emu sms send 5551234 "Your Telegram code is 55271. Do not give this code to anyone." >/dev/null
waitfor 'Telegram login code received' 15 && pass "7.7 Telegram code SMS -> alert 'Telegram login code received'" || fail "7.7 no login-code alert"
ev=$($A shell run-as $P cat shared_prefs/fraudshield_events.xml 2>/dev/null)
echo "$ev" | grep -q 55271 && fail "7.7 the code was stored" || pass "7.7 the code itself is never stored"
dismiss 'Telegram login code received' '^Ignore |^ \| Ignore \|'

# SMS apps re-post earlier messages when a new one arrives in the same thread: the code alert must not come back
$A emu sms send 5551234 "ok thanks" >/dev/null
sleep 20
has 'Telegram login code received' && { fail "7.7 the same login code alerted twice (SMS app re-posted it)"; dismiss 'Telegram login code received' '^ \| Ignore \|'; }   || pass "7.7 the same notice is not alerted twice when the SMS app re-posts it"

# ---- 7.8 new-device notice (app on screen) -> CRITICAL + server
$A emu sms send 5551235 "WhatsApp: new login on another device. If this was not you, secure your account." >/dev/null
waitfor 'WhatsApp may be accessed from another device' 15 && pass "7.8 new-device SMS -> CRITICAL alert" || fail "7.8 no new-device alert"
sleep 3
n=$(db "select count(*) from threat_events where user_id=$UID_ and type='WHATSAPP_TAKEOVER' and severity='CRITICAL'")
[ "$n" -ge 1 ] && pass "7.8 reported to the server as WHATSAPP_TAKEOVER CRITICAL" || fail "7.8 not on the server"
dismiss 'WhatsApp may be accessed from another device' 'I understand'

# ---- 7.9 WhatsApp wizard
for i in 1 2 3 4 5 6; do has 'Secure WhatsApp' && break; $A shell input swipe 540 1700 540 900 900; sleep 2; done  # slow swipe: no fling
has 'Secure WhatsApp' || { note "screen before 7.9:"; python "$D/ui.py" texts "$D/u.xml" | grep -v "' |  |  |" | head -8 | cut -c1-110 | sed 's/^/       /' | tee -a "$OUT"; }
tap 'Secure WhatsApp' && sleep 3
has 'Linked devices' && pass "7.9 wizard opens with readable steps (Linked devices ...)" || fail "7.9 wizard steps not shown"
tap 'Open WhatsApp now'   # the snackbar stays 6 s: look at once
quick() { $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $A exec-out cat /sdcard/u.xml > "$D/u.xml"; python "$D/ui.py" has "$1" "$D/u.xml"; }
if $A shell pm list packages | grep -q com.whatsapp; then
  $A shell dumpsys window | grep -m1 mCurrentFocus | grep -q whatsapp && pass "7.9 opens WhatsApp" || fail "7.9 WhatsApp not opened"
else { quick 'App not installed' || quick 'App not installed' || quick 'App not installed'; } && pass "7.9 WhatsApp not installed -> clear message" || fail "7.9 no message"; fi
$A shell input keyevent KEYCODE_BACK; sleep 1

# ---- 7.10 part 2: lock screen - critical alert while locked, details hidden
$A shell input keyevent KEYCODE_HOME; sleep 1; $A shell input keyevent KEYCODE_SLEEP; sleep 2
$A emu sms send 5551236 "SBI: KYC expired. Update now http://192.168.4.20/sbi-kyc-update/verify.apk" >/dev/null
for i in $(seq 1 12); do  # the background scan needs a few seconds (server round trip)
  rec=$($A shell dumpsys notification --noredact | awk '/NotificationRecord.*pkg=com.fraudshield.app/{f=1} f')
  echo "$rec" | grep -q "android.title=String (Dangerous link!)" && break; sleep 5; done
echo "$rec" | grep -q "android.title=String (Dangerous link!)" && pass "7.10 critical alert posted while the phone is locked" || fail "7.10 no alert while locked"
echo "$rec" | grep -qE "fullScreenIntent=PendingIntent|fullscreenIntent" && pass "7.10 full-screen intent attached (allowed)" || note "full-screen intent not visible in dumpsys"
echo "$rec" | grep -q "vis=PRIVATE" && pass "7.10 lock screen shows the public version only (details hidden)" || fail "7.10 visibility not PRIVATE"
$A shell input keyevent KEYCODE_WAKEUP
echo "== done: $(grep -c PASS "$OUT") pass, $(grep -c FAIL "$OUT") fail ==" | tee -a "$OUT"
