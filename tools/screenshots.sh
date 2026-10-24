#!/usr/bin/env bash
# Installs the APK on the running emulator and captures Fold 5 cover + inner screenshots.
set -u
export PATH=$PATH:/opt/android-sdk/platform-tools
cd "$(dirname "$0")/.."
OUT=screenshots; PKG=com.victor.familytree/.MainActivity
log(){ echo "$(date +%T) $*"; }
wake(){ adb shell svc power stayon true; adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard; }
shot(){ # name, wait-seconds, extras...
  local name=$1 wait=$2; shift 2
  wake; adb shell am start -S -W -n $PKG "$@" >/dev/null
  # wait until the loading spinner is gone (app window focused), then settle
  sleep "$wait"
  cap "$name"
}
cap(){ # retry until the frame is not blank (black frames are tiny PNGs)
  for i in 1 2 3 4 5 6; do
    adb exec-out screencap -p > "$OUT/$1.png"
    [ "$(stat -c %s "$OUT/$1.png")" -gt 25000 ] && { log "saved $1"; return; }
    log "blank frame for $1, retry $i"; wake; adb shell am start -n $PKG >/dev/null; sleep 20
  done
}
log install; adb install -r dist/family-tree-0.1.apk
adb shell settings put global window_animation_scale 0; adb shell settings put global transition_animation_scale 0; adb shell settings put global animator_duration_scale 0
log "cover screen"; adb shell wm size reset; adb shell wm density reset; adb shell wm size; adb shell wm density
shot fold5-cover-1-list 75 --es tab people
shot fold5-cover-2-detail 75 --es person I0212
adb shell input swipe 450 1900 450 500 600; sleep 15; adb shell input swipe 450 1900 450 500 600; sleep 25; cap fold5-cover-3-detail-scrolled
shot fold5-cover-4-ancestors 90 --es tab ancestors
shot fold5-cover-5-descendants 90 --es tab descendants
shot fold5-cover-6-about 90 --es tab about
shot fold5-cover-7-search-orr 75 --es tab people --es query orr
log "inner screen 1812x2176"; adb shell wm size 1812x2176; adb shell wm density 420; sleep 20
shot fold5-inner-1-two-pane 90 --es tab people --es person I0212
shot fold5-inner-2-ancestors 100 --es tab ancestors --es person I0228
shot fold5-inner-3-descendants 90 --es tab descendants --es person I0149
adb shell wm size reset; adb shell wm density reset
log DONE
