#!/usr/bin/env bash
# create (if needed) + boot one emulator, run a test on it, shut it down.  Usage: run_api.sh <api> [test58.sh|capture34.sh]
API=$1
D=$(cd "$(dirname "$0")" && pwd)
export ANDROID_AVD_HOME='E:/fraud detection/_devtools/avd' PATH="/e/Android/Sdk/platform-tools:$PATH" ANDROID_SDK_ROOT='E:\Android\Sdk' JAVA_HOME='E:\Android\jdk-17.0.2'
N=fs$API; PORT=5562; S=emulator-$PORT
if [ ! -d "/e/fraud detection/_devtools/avd/$N.avd" ]; then
  echo no | /e/Android/Sdk/cmdline-tools/latest/bin/avdmanager.bat create avd -n $N -k "system-images;android-$API;google_apis;x86_64" -d pixel_5 --force >/dev/null 2>&1
  C="/e/fraud detection/_devtools/avd/$N.avd/config.ini"; sed -i "/^hw.ramSize/d;/^hw.keyboard/d" "$C"; printf "hw.ramSize=1536
hw.keyboard=yes
" >> "$C"
fi
/e/Android/Sdk/emulator/emulator.exe -avd $N -port $PORT -no-window -no-audio -no-snapshot -no-boot-anim -memory 1536 > "$D/emu$API.log" 2>&1 &
adb -s $S wait-for-device
for i in $(seq 1 120); do [ "$(adb -s $S shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break; sleep 4; done
sleep 10
for k in window_animation_scale transition_animation_scale animator_duration_scale; do adb -s $S shell settings put global $k 0; done
bash "$D/${2:-test58.sh}" $API $S
adb -s $S emu kill >/dev/null 2>&1; sleep 5
