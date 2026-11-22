#!/usr/bin/env bash
export MSYS_NO_PATHCONV=1
# 5.8 grant / deny / revoke test on one emulator.  Usage: test58.sh <api-level> <serial>
API=$1; S=$2
D=$(cygpath -m "$(cd "$(dirname "$0")" && pwd)")
A="adb -s $S"
PKG=com.fraudshield.app
APK="E:/fraud detection/mobile/build/app/outputs/flutter-apk/app-debug.apk"
LOCK="$D/lock-$S"; mkdir "$LOCK" 2>/dev/null || { echo "another test is already running on $S - stop it first"; exit 3; }
trap 'rmdir "$LOCK"' EXIT
OUT="$D/result-$API.txt"; : > "$OUT"
pass() { echo "PASS | $1" | tee -a "$OUT"; }
fail() { echo "FAIL | $1" | tee -a "$OUT"; $A exec-out screencap -p > "$D/fail-$API-$(date +%s).png"; }
dump() {
  for i in 1 2 3; do $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $A exec-out cat /sdcard/u.xml > "$D/u.xml" 2>/dev/null; grep -q '</hierarchy>' "$D/u.xml" && break; sleep 1; done
  if grep -q "isn.t responding" "$D/u.xml"; then   # slow emulator: Android's "app isn't responding" dialog -> Wait
    local xy; xy=$(python "$D/ui.py" tap 'aerr_wait|^Wait' "$D/u.xml") && $A shell input tap $xy
    echo "NOTE | 'isn't responding' dialog appeared (slow emulator), tapped Wait" >> "$OUT"; sleep 4
    $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $A exec-out cat /sdcard/u.xml > "$D/u.xml" 2>/dev/null
  fi; }
has() { dump; python "$D/ui.py" has "$1" "$D/u.xml"; }
tap() { dump; local xy; xy=$(python "$D/ui.py" tap "$1" "$D/u.xml") || { echo "  (not found: $1)"; return 1; }; $A shell input tap $xy; }
tapnear() { dump; local xy; xy=$(python "$D/ui.py" near "$1" "$2" "$D/u.xml") || { echo "  (not found: $1 below $2)"; return 1; }; $A shell input tap $xy; }
waitfor() { for i in $(seq 1 ${2:-15}); do has "$1" && return 0; sleep 2; done; return 1; }
gone() { for i in $(seq 1 ${2:-10}); do has "$1" || return 0; sleep 2; done; return 1; }
focus() { $A shell dumpsys window | grep -m1 -E 'mCurrentFocus' ; }
inApp() { focus | grep -q "$PKG"; }
fileTile() { # the tile sits below the permission cards: scroll to it, read it, scroll back
  local r=1
  for i in 1 2 3; do has 'File protection
'"$1" && { r=0; break; }; $A shell input swipe 540 1700 540 700 300; sleep 1; done
  for i in 1 2 3; do $A shell input swipe 540 700 540 1900 300; done; sleep 1
  return $r; }
grantBtn() { tapnear 'Grant access' 'Enable file protection'; }
newBadFile() { local n="t58_$RANDOM.pdf"; $A shell "echo '%PDF-1.4 1 0 obj << /OpenAction << /S /Launch /F (cmd.exe) >> >> endobj' > /sdcard/Download/$n"; echo $n; }
caught() { # dialog or notification about the file
  for i in $(seq 1 25); do
    has "MALICIOUS FILE|Suspicious file|$1" && has "$1" && return 0
    sleep 2
  done; return 1; }

echo "== Android API $API ($S) =="
$A shell getprop ro.build.version.release | tr -d '\r' | sed 's/^/Android version: /' | tee -a "$OUT"
[ "$API" -ge 30 ] && $A shell appops set --uid $PKG MANAGE_EXTERNAL_STORAGE default >/dev/null 2>&1
$A uninstall $PKG >/dev/null 2>&1
$A install "$APK" >/dev/null || { fail "install"; exit 1; }
[ "$API" -ge 33 ] && $A shell pm grant $PKG android.permission.POST_NOTIFICATIONS
$A shell rm -f '/sdcard/Download/t58_*'
# warm-up: first start of a debug build is slow on a software-GPU emulator
$A shell am start -n $PKG/.MainActivity >/dev/null; sleep 20; $A shell am force-stop $PKG
$A shell am start -n $PKG/.MainActivity >/dev/null
waitfor 'New here\? Register' 30 || { fail "login screen did not appear"; exit 1; }
for i in 1 2 3 4; do tap 'New here\? Register'; sleep 2; has 'Create account' && break; done
typeslow() { local t=$1 i; for ((i=0; i<${#t}; i+=3)); do $A shell input text "${t:i:3}"; sleep 0.4; done; }
tap 'EditText$'; sleep 1; typeslow "t58x${API}x$RANDOM@t.com"
tap 'EditText PASSWORD'; sleep 1; typeslow "password123"
sleep 1; tap 'Create account'
waitfor 'Enable file protection' 30 && pass "fresh install: 'Enable file protection' card shown" || fail "card not shown after login"
fileTile OFF && pass "fresh install: File protection tile OFF" || fail "tile not OFF"
F0=$(newBadFile); sleep 25
has "$F0" && fail "file scanned without permission" || pass "no permission: new file is NOT scanned"

if [ "$API" -ge 30 ]; then
  # --- deny: open the All files access page and come back without allowing
  echo "  state before deny: $($A shell appops get $PKG MANAGE_EXTERNAL_STORAGE | tr -d '
' | tr '
' ' ')"
  has 'Enable file protection' || echo "  card already gone before deny"
  grantBtn; sleep 3
  focus | grep -q settings && pass "Grant access opens system 'All files access' page" || fail "settings page did not open ($(focus))"
  $A shell input keyevent 4; sleep 3
  has 'Enable file protection' && fileTile OFF && pass "deny (back without allowing): card stays, tile OFF" || fail "deny state wrong"
  # --- grant
  grantBtn; sleep 3
  waitfor 'manage all files|all files' 10
  tap 'Allow access to manage all files|Permit access to manage all files|android:id/switch_widget|switch_widget' ; sleep 2
  $A shell input keyevent 4; sleep 4
  inApp || { $A shell am start -n $PKG/.MainActivity >/dev/null; sleep 4; }
  gone 'Enable file protection' && fileTile ON && pass "grant: card gone, tile ON (refreshed on return)" || fail "grant state wrong"
  F1=$(newBadFile)
  caught "$F1" && pass "granted: new file $F1 caught" || fail "granted: new file not caught"
  # --- revoke in Settings
  $A shell input keyevent 4; sleep 1
  $A shell am start -a android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION -d package:$PKG >/dev/null; sleep 3
  tap 'Allow access to manage all files|Permit access to manage all files|android:id/switch_widget|switch_widget'; sleep 2
  $A shell input keyevent 4; sleep 2
  $A shell am start -n $PKG/.MainActivity >/dev/null; sleep 5
  waitfor 'Enable file protection' 15 && fileTile OFF && pass "revoked in Settings: card back, tile OFF" || fail "revoke state wrong"
else
  # --- Android 8-10: runtime storage permission
  grantBtn; sleep 2
  waitfor 'permission_deny_button|Deny' 10 && pass "Grant access shows the Android storage permission dialog" || fail "no runtime dialog ($(focus))"
  tap 'permission_deny_button'; sleep 3
  has 'Enable file protection' && fileTile OFF && pass "deny: card stays, tile OFF" || fail "deny state wrong"
  grantBtn; sleep 2
  tap 'permission_allow_button'; sleep 4
  gone 'Enable file protection' && fileTile ON && pass "allow: card gone, tile ON" || fail "allow state wrong"
  F1=$(newBadFile)
  caught "$F1" && pass "granted: new file $F1 caught (legacy storage works)" || fail "granted: new file not caught"
  $A shell pm revoke $PKG android.permission.READ_EXTERNAL_STORAGE; $A shell pm revoke $PKG android.permission.WRITE_EXTERNAL_STORAGE
  sleep 2; $A shell am start -n $PKG/.MainActivity >/dev/null; sleep 6
  waitfor 'Enable file protection' 15 && fileTile OFF && pass "revoked: card back, tile OFF" || fail "revoke state wrong"
  grantBtn; sleep 2
  has 'permission_deny_button' && pass "after a reset the permission dialog is shown again (not sent to Settings)" || fail "no dialog after reset ($(focus))"
  tap 'permission_deny_button'; sleep 2
  grantBtn; sleep 2
  if tap 'permission_deny_and_dont_ask_again_button|do_not_ask_checkbox'; then
    sleep 1; has 'permission_deny_button' && { tap 'permission_deny_button'; sleep 2; }
    grantBtn; sleep 3
    focus | grep -qi 'settings' && has 'App info|fraudshield' && pass "after the user chose 'Don't ask again': Grant access opens App info" || fail "dont-ask-again fallback ($(focus))"
    $A shell input keyevent 4; sleep 2
  else
    python "$D/ui.py" texts "$D/u.xml" | grep -iv "^' |  |" | head -10; fail "no 'Don't ask again' option found"
  fi
  inApp && has 'Enable file protection' && pass "back in the app after Settings: card still shown" || fail "not back in app"
fi
echo "== done API $API: $(grep -c PASS "$OUT") pass, $(grep -c FAIL "$OUT") fail =="
