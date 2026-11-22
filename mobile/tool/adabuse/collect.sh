#!/usr/bin/env bash
export MSYS_NO_PATHCONV=1
# 6.1: build a REAL labelled set on a THROWAWAY emulator (created here, deleted at the end).
#   ABUSIVE = real adware samples from MalwareBazaar (tags adware / HiddenAds / Mobidash / clicker; Triada excluded)
#   BENIGN  = real open-source apps from F-Droid (incl. hard cases that use SMS / accessibility / overlay)
# Same protocol for both classes ("user who taps Allow on everything"): install with all runtime permissions,
# grant overlay + enable any accessibility / notification-listener service the app declares, open it, poke the UI,
# leave it, then let FraudShield (debug build) audit all apps. Output: _devtools/adabuse/measured.jsonl
# Usage: collect.sh [max_adware]      Needs: backend on :18080, MALWAREBAZAAR_API_KEY in .env
MAXBAD=${1:-30}
R="E:/fraud detection"; W="$R/_devtools/adabuse"; T="$R/mobile/tool"
export ANDROID_AVD_HOME="$R/_devtools/avd" PATH="/e/Android/Sdk/platform-tools:$PATH" ANDROID_SDK_ROOT='E:\Android\Sdk' JAVA_HOME='E:\Android\jdk-17.0.2'
N=fsadabuse; PORT=5566; S=emulator-$PORT; A="adb -s $S"
PKG=com.fraudshield.app; APK="$W/app-emu.apk"  # copy of the 10.0.2.2 debug build
LOG="$W/collect.log"; : > "$LOG"; log() { echo "$*" | tee -a "$LOG"; }

echo no | /e/Android/Sdk/cmdline-tools/latest/bin/avdmanager.bat create avd -n $N -k "system-images;android-34;google_apis;x86_64" -d pixel_5 --force >/dev/null 2>&1
C="$R/_devtools/avd/$N.avd/config.ini"; sed -i "/^hw.ramSize/d;/^hw.keyboard/d;/^disk.dataPartition.size/d" "$C"
printf 'hw.ramSize=2048\nhw.keyboard=yes\ndisk.dataPartition.size=6G\n' >> "$C"
/e/Android/Sdk/emulator/emulator.exe -avd $N -port $PORT -no-window -no-audio -no-snapshot -no-boot-anim -wipe-data -memory 2048 > "$W/emu.log" 2>&1 &
$A wait-for-device
for i in $(seq 1 150); do [ "$($A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break; sleep 4; done; sleep 15
$A root >/dev/null; sleep 3; $A wait-for-device
for k in window_animation_scale transition_animation_scale animator_duration_scale; do $A shell settings put global $k 0; done
$A shell settings put system screen_off_timeout 1800000
log "emulator up: Android $($A shell getprop ro.build.version.release | tr -d '\r')"

# --- FraudShield with notification access + usage access (it must see other apps' notifications / launches)
$A install -g "$APK" >/dev/null || { log "FraudShield install failed"; exit 1; }
$A shell cmd notification allow_listener $PKG/$PKG.AdNotificationListener
$A shell appops set $PKG GET_USAGE_STATS allow
U="$W/u.xml"
dump() { $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $A exec-out cat /sdcard/u.xml > "$U" 2>/dev/null; }
tap() { dump; local xy; xy=$(python "$T/e2e/ui.py" tap "$1" "$U") && $A shell input tap $xy; }
has() { dump; python "$T/e2e/ui.py" has "$1" "$U"; }
typeslow() { local t=$1 i; for ((i=0; i<${#t}; i+=3)); do $A shell input text "${t:i:3}"; sleep 0.4; done; }
$A shell am start -n $PKG/.MainActivity >/dev/null; sleep 30
for i in 1 2 3 4; do tap 'New here\? Register'; sleep 2; has 'Create account' && break; done
tap 'EditText$'; sleep 1; typeslow "ad61x$RANDOM@t.com"; tap 'EditText PASSWORD'; sleep 1; typeslow "password123"; sleep 1
tap 'Create account'; sleep 20; has 'Call forwarding|Risky|Enable link' && log "FraudShield logged in" || log "WARNING: FraudShield login not confirmed"
$A shell input keyevent KEYCODE_HOME

before=$($A shell pm list packages -3 | tr -d '\r' | sort)
grant_all() { # $1 package: the "taps Allow on everything" user
  local p=$1 c
  $A shell appops set $p SYSTEM_ALERT_WINDOW allow 2>/dev/null
  for c in $($A shell cmd package query-services --components -a android.accessibilityservice.AccessibilityService 2>/dev/null | tr -d '\r' | grep "^$p/"); do
    local cur; cur=$($A shell settings get secure enabled_accessibility_services | tr -d '\r'); [ "$cur" = null ] && cur=""
    $A shell settings put secure enabled_accessibility_services "${cur:+$cur:}$c"; $A shell settings put secure accessibility_enabled 1
  done
  for c in $($A shell cmd package query-services --components -a android.service.notification.NotificationListenerService 2>/dev/null | tr -d '\r' | grep "^$p/"); do
    $A shell cmd notification allow_listener "$c"
  done; }
exercise() { # open, poke the UI with random taps, leave
  $A shell monkey -p $1 -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 12
  $A shell monkey -p $1 --throttle 400 --pct-syskeys 0 --pct-appswitch 0 30 >/dev/null 2>&1; sleep 3
  $A shell input keyevent KEYCODE_HOME; sleep 2; }
install_one() { # $1 label, $2 source id, $3 remote apk
  local pre post newp
  pre=$($A shell pm list packages -3 | tr -d '\r' | sort)
  local err; err=$($A shell pm install -g -r --bypass-low-target-sdk-block "$3" 2>&1 | tr -d "" | tail -1)  # Android 14 blocks old targetSdk by default
  [ "$err" = Success ] || { log "  install failed: $2 ($err)"; $A shell rm -f "$3"; return 1; }
  $A shell rm -f "$3"
  post=$($A shell pm list packages -3 | tr -d '\r' | sort)
  newp=$(comm -13 <(echo "$pre") <(echo "$post") | sed 's/^package://' | head -1)
  [ -z "$newp" ] && { log "  no new package (already installed?): $2"; return 1; }
  echo "{\"label\":\"$1\",\"package\":\"$newp\",\"source\":\"$2\"}" >> "$W/installed.jsonl"
  grant_all "$newp"; exercise "$newp"; log "  $1 $newp ($2)"; }
: > "$W/installed.jsonl"

log "--- benign (F-Droid)"
python -I -c "import json;[print(x['package'],x['apk']) for x in json.load(open('$W/benign/benign.json'))]" | tr -d '\r' |
while read -r p f; do { $A push "$W/benign/$f" /data/local/tmp/b.apk >/dev/null 2>&1 && install_one BENIGN "fdroid:$p" /data/local/tmp/b.apk; } </dev/null; done

log "--- adware (MalwareBazaar, streamed into the emulator, never stored on the PC)"
python -I -c "
import json
d=[x for x in json.load(open('$W/mb_samples.json')) if x['signature']!='Triada' and not any('triada' in t.lower() or 'tirada' in t.lower() or 'trida' in t.lower() for t in (x['tags'] or []))]
d.sort(key=lambda x: x['first_seen'] or '', reverse=True)
[print(x['sha256']) for x in d[:$MAXBAD]]" | tr -d '\r' |
while read -r h; do { python -I "$T/adabuse/mb_push.py" "$h" $S /data/local/tmp/m.apk >/dev/null 2>>"$LOG" && install_one ABUSIVE "mb:$h" /data/local/tmp/m.apk; } </dev/null; done

# --- let them run like a phone in a pocket: screen off/on cycles (adware often shows ads on unlock)
log "--- soak 20 min"
for i in $(seq 1 10); do $A shell input keyevent KEYCODE_SLEEP; sleep 50; $A shell input keyevent KEYCODE_WAKEUP; $A shell input keyevent 82; sleep 70; done

# --- FraudShield audit -> one log line per app
$A logcat -c; $A shell am force-stop $PKG; $A shell am start -n $PKG/.MainActivity >/dev/null; sleep 60
$A logcat -d -v raw -s flutter:I | tr -d '\r' | grep '^app audit ' | sed 's/^app audit //' > "$W/audit.jsonl"
log "audited apps: $(wc -l < "$W/audit.jsonl"), installed under test: $(wc -l < "$W/installed.jsonl")"
python -I "$T/adabuse/to_csv.py" "$W/installed.jsonl" "$W/audit.jsonl" "$W/measured.csv" | tee -a "$LOG"

# --- throw the emulator away
$A emu kill >/dev/null 2>&1; sleep 8
/e/Android/Sdk/cmdline-tools/latest/bin/avdmanager.bat delete avd -n $N >/dev/null 2>&1
log "emulator deleted"
