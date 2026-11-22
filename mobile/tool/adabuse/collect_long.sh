#!/usr/bin/env bash
export MSYS_NO_PATHCONV=1
# 6.1 LONG run on a THROWAWAY emulator (created here, deleted at the end).
#   ABUSIVE = every non-Triada adware sample from MalwareBazaar that installs; BENIGN = 29 real F-Droid apps.
# Same "taps Allow on everything" protocol as collect.sh, then HOURS of pocket use (screen off/on) with a
# FraudShield audit every 30 min. Notifications are counted over a sliding hour, so each app keeps its PEAK.
# Every round also records which apps still show a launcher icon (HiddenAds hides it).
# Output: _devtools/adabuse/long/{installed,audit,launcher}.jsonl + measured-long.csv + traits-long.csv
# Usage: collect_long.sh [hours=3]     Needs: backend on :18080, MALWAREBAZAAR_API_KEY in .env
HOURS=${1:-3}
R="E:/fraud detection"; W="$R/_devtools/adabuse"; O="$W/long"; T="$R/mobile/tool"
export ANDROID_AVD_HOME="$R/_devtools/avd" PATH="/e/Android/Sdk/platform-tools:$PATH" ANDROID_SDK_ROOT='E:\Android\Sdk' JAVA_HOME='E:\Android\jdk-17.0.2'
N=fsadlong; PORT=5570; S=emulator-$PORT; A="adb -s $S"
PKG=com.fraudshield.app; APK="$W/app-emu.apk"
mkdir -p "$O"; LOG="$O/collect.log"; : > "$LOG"; log() { echo "$(date +%H:%M) $*" | tee -a "$LOG"; }
: > "$O/installed.jsonl"; : > "$O/audit.jsonl"; : > "$O/launcher.jsonl"

curl -s -o /dev/null -w "%{http_code}" http://localhost:18080/api/v1/config/carriers | grep -q 401 || { log "backend not up on :18080 - start it first"; exit 1; }

echo no | /e/Android/Sdk/cmdline-tools/latest/bin/avdmanager.bat create avd -n $N -k "system-images;android-34;google_apis;x86_64" -d pixel_5 --force >/dev/null 2>&1
C="$R/_devtools/avd/$N.avd/config.ini"; [ -f "$C" ] || { log "AVD create failed"; exit 1; }
sed -i "/^hw.ramSize/d;/^hw.keyboard/d;/^disk.dataPartition.size/d" "$C"
printf 'hw.ramSize=2048\nhw.keyboard=yes\ndisk.dataPartition.size=8G\n' >> "$C"
/e/Android/Sdk/emulator/emulator.exe -avd $N -port $PORT -no-window -no-audio -no-snapshot -no-boot-anim -wipe-data -memory 2048 > "$O/emu.log" 2>&1 &
$A wait-for-device
for i in $(seq 1 150); do [ "$($A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break; sleep 4; done; sleep 15
$A root >/dev/null; sleep 3; $A wait-for-device
for k in window_animation_scale transition_animation_scale animator_duration_scale; do $A shell settings put global $k 0; done
$A shell settings put system screen_off_timeout 1800000
sleep 60  # let the system settle before driving the UI
log "emulator up: Android $($A shell getprop ro.build.version.release | tr -d '\r')"

$A install -g "$APK" >/dev/null || { log "FraudShield install failed"; exit 1; }
$A shell cmd notification allow_listener $PKG/$PKG.AdNotificationListener
$A shell appops set $PKG GET_USAGE_STATS allow
U="$O/u.xml"
dump() { $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $A exec-out cat /sdcard/u.xml > "$U" 2>/dev/null
  if grep -q "isn.t responding" "$U"; then  # overloaded emulator: Android's ANR dialog -> Wait
    local xy; xy=$(python "$T/e2e/ui.py" tap 'aerr_wait|^Wait' "$U") && $A shell input tap $xy; log "  (tapped Wait on an 'isn't responding' dialog)"; sleep 5
    $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $A exec-out cat /sdcard/u.xml > "$U" 2>/dev/null
  fi; }
tap() { dump; local xy; xy=$(python "$T/e2e/ui.py" tap "$1" "$U") && $A shell input tap $xy; }
has() { dump; python "$T/e2e/ui.py" has "$1" "$U"; }
typeslow() { local t=$1 i; for ((i=0; i<${#t}; i+=3)); do $A shell input text "${t:i:3}"; sleep 0.4; done; }
login_ok=0
for attempt in 1 2 3 4 5; do
  $A shell am force-stop $PKG; $A shell am start -n $PKG/.MainActivity >/dev/null; sleep 30
  has 'Call forwarding|Risky|Enable link' && { login_ok=1; break; }
  for i in 1 2 3 4; do tap 'New here\? Register'; sleep 2; has 'Create account' && break; done
  tap 'EditText$'; sleep 1; typeslow "long61x$RANDOM@t.com"; tap 'EditText PASSWORD'; sleep 1; typeslow "password123"; sleep 1
  tap 'Create account'; sleep 25
  has 'Call forwarding|Risky|Enable link' && { login_ok=1; break; }
  log "login attempt $attempt not confirmed, retrying"
done
[ $login_ok = 1 ] && log "FraudShield logged in" || { log "FraudShield login FAILED - stopping (audit needs the dashboard)"; $A emu kill; exit 1; }
$A shell input keyevent KEYCODE_HOME

grant_all() {
  local p=$1 c
  $A shell appops set $p SYSTEM_ALERT_WINDOW allow 2>/dev/null
  for c in $($A shell cmd package query-services --components -a android.accessibilityservice.AccessibilityService 2>/dev/null | tr -d '\r' | grep "^$p/"); do
    local cur; cur=$($A shell settings get secure enabled_accessibility_services | tr -d '\r'); [ "$cur" = null ] && cur=""
    $A shell settings put secure enabled_accessibility_services "${cur:+$cur:}$c"; $A shell settings put secure accessibility_enabled 1
  done
  for c in $($A shell cmd package query-services --components -a android.service.notification.NotificationListenerService 2>/dev/null | tr -d '\r' | grep "^$p/"); do
    $A shell cmd notification allow_listener "$c"
  done; }
exercise() {
  $A shell monkey -p $1 -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 12
  $A shell monkey -p $1 --throttle 400 --pct-syskeys 0 --pct-appswitch 0 30 >/dev/null 2>&1; sleep 3
  $A shell input keyevent KEYCODE_HOME; sleep 2; }
install_one() {
  local pre post newp err
  pre=$($A shell pm list packages -3 | tr -d '\r' | sort)
  err=$($A shell pm install -g -r --bypass-low-target-sdk-block "$3" 2>&1 | tr -d '\r' | grep -m1 -E "Success|Failure|Exception|Error")
  $A shell rm -f "$3"
  [ "$err" = Success ] || { log "  install failed: $2 ($err)"; return 1; }
  post=$($A shell pm list packages -3 | tr -d '\r' | sort)
  newp=$(comm -13 <(echo "$pre") <(echo "$post") | sed 's/^package://' | head -1)
  [ -z "$newp" ] && { log "  no new package: $2"; return 1; }
  echo "{\"label\":\"$1\",\"package\":\"$newp\",\"source\":\"$2\"}" >> "$O/installed.jsonl"
  grant_all "$newp"; exercise "$newp"; log "  $1 $newp ($2)"; }

log "--- benign (F-Droid)"
mapfile -t BEN < <(python -I -c "import json;[print(x['package'],x['apk']) for x in json.load(open('$W/benign/benign.json'))]" | tr -d '\r')
for line in "${BEN[@]}"; do p=${line%% *}; f=${line#* }
  { $A push "$W/benign/$f" /data/local/tmp/b.apk >/dev/null 2>&1 && install_one BENIGN "fdroid:$p" /data/local/tmp/b.apk; } </dev/null
done

log "--- adware (streamed, 4 MB chunks, sha256 checked on the emulator)"
mapfile -t BAD < <(python -I -c "
import json
d=[x for x in json.load(open('$W/mb_samples.json')) if x['signature']!='Triada' and not any(k in t.lower() for t in (x['tags'] or []) for k in ('triada','tirada','trida'))]
[print(x['sha256']) for x in d]" | tr -d '\r')
for h in "${BAD[@]}"; do
  { out=$(python -I "$T/adabuse/mb_push.py" "$h" $S /data/local/tmp/m.apk 2>&1); rc=$?
    if [ $rc = 0 ]; then install_one ABUSIVE "mb:$h" /data/local/tmp/m.apk; else log "  download/transfer failed: mb:$h ($(echo "$out" | tail -1))"; fi; } </dev/null
done
log "installed: $(grep -c BENIGN "$O/installed.jsonl") benign, $(grep -c ABUSIVE "$O/installed.jsonl") adware"

audit_round() { # $1 round number
  $A shell input keyevent KEYCODE_WAKEUP; $A shell input keyevent 82
  $A logcat -c; $A shell am force-stop $PKG; $A shell am start -n $PKG/.MainActivity >/dev/null; sleep 60
  $A logcat -d -v raw -s flutter:I | tr -d '\r' | grep '^app audit ' | sed "s/^app audit /{\"round\":$1,\"a\":/; s/\$/}/" >> "$O/audit.jsonl"
  local l; l=$($A shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER 2>/dev/null | tr -d '\r' | grep '/' | cut -d/ -f1 | sort -u | tr '\n' ' ')
  echo "{\"round\":$1,\"withIcon\":\"$l\"}" >> "$O/launcher.jsonl"
  $A shell input keyevent KEYCODE_HOME
  log "audit round $1: $(grep -c "\"round\":$1," "$O/audit.jsonl") apps"; }

ROUNDS=$((HOURS * 2))
log "--- pocket use for $HOURS h, audit every 30 min ($ROUNDS rounds)"
audit_round 0
for r in $(seq 1 $ROUNDS); do
  for i in $(seq 1 12); do $A shell input keyevent KEYCODE_SLEEP; sleep 80; $A shell input keyevent KEYCODE_WAKEUP; $A shell input keyevent 82; sleep 60; done
  audit_round $r
done

python -I "$T/adabuse/merge_long.py" "$O" | tee -a "$LOG"
$A emu kill >/dev/null 2>&1; sleep 8
/e/Android/Sdk/cmdline-tools/latest/bin/avdmanager.bat delete avd -n $N >/dev/null 2>&1
log "emulator deleted"
