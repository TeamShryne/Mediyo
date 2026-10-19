#!/usr/bin/env bash
# Drives the debug APK on the CI emulator and leaves screenshots behind so an
# agent (or a human) can see what the app actually rendered.
#
# Every step is best-effort on purpose: a failed screenshot is far more useful
# than a red CI with no artifacts. The one hard check is the crash scan at the
# end, which still uploads the logs before failing.
set -uo pipefail

PKG="${PKG:-com.teamshryne.mediyo.debug}"
ACTIVITY="${ACTIVITY:-com.teamshryne.mediyo.MainActivity}"
ACTION="${ACTION:-com.teamshryne.mediyo.adb}"
APK="${APK:-app/build/outputs/apk/debug/app-debug.apk}"
OUT="${OUT:-artifacts}"

mkdir -p "$OUT/screenshots"

log()  { echo "  $*"; }
step() { echo "== $1"; }

send() {
  adb shell am broadcast \
    -a "$ACTION" \
    -p "$PKG" \
    --es cmd "$1" "${@:2}" >/dev/null 2>&1 || log "broadcast '$1' returned non-zero"
}

# Wait for the guest, then land on the launcher with animations off so
# screenshots are deterministic.
step "waiting for boot"
adb wait-for-device
for _ in $(seq 1 90); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 2
done
adb shell settings put global window_animation_scale 0     >/dev/null 2>&1
adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1
adb shell settings put global animator_duration_scale 0    >/dev/null 2>&1
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
adb shell svc power stayon true >/dev/null 2>&1
adb shell wm dismiss-keyguard >/dev/null 2>&1

step "installing $APK"
if ! adb install -r -t "$APK"; then
  echo "::error::apk install failed"
  exit 1
fi
# The notification permission dialog would sit on top of the first screen.
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

shot() {
  sleep "$2"
  adb exec-out screencap -p > "$OUT/screenshots/$1.png" 2>/dev/null
  if [ -s "$OUT/screenshots/$1.png" ]; then
    log "captured $1.png"
  else
    log "WARNING: empty screenshot $1.png"
  fi
}

focus() { adb shell dumpsys window 2>/dev/null | grep -m1 -E 'mCurrentFocus|mFocusedApp' || true; }

step "launching"
adb shell am start -n "$PKG/$ACTIVITY" >/dev/null 2>&1
for _ in $(seq 1 30); do
  adb shell pidof "$PKG" >/dev/null 2>&1 && break
  sleep 1
done
shot 01-home 10
log "focus: $(focus)"

step "search"
send search --es query "daft punk one more time"
shot 02-search 12

step "play"
send play --es query "daft punk one more time"
shot 03-player 20

step "state dump"
send dump
sleep 2

step "navigate: liked"
send go --es route liked
shot 04-liked 6

step "navigate: settings"
send go --es route settings
shot 05-settings 5

step "back to search results"
send go --es route search
shot 06-search-again 5

step "collecting logs"
adb logcat -d -s MediyoAdb:D > "$OUT/mediyo-adb.log" 2>/dev/null
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null
log "mediyo-adb.log:"
sed 's/^/    /' "$OUT/mediyo-adb.log" 2>/dev/null | head -20

ALIVE="$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r')"
CRASHES="$(grep -c 'FATAL EXCEPTION' "$OUT/logcat.txt" 2>/dev/null || echo 0)"

{
  echo "package_pid=${ALIVE:-none}"
  echo "fatal_exceptions=${CRASHES}"
  echo "screenshots=$(ls -1 "$OUT/screenshots" 2>/dev/null | wc -l)"
} > "$OUT/summary.txt"

if [ -z "$ALIVE" ]; then
  echo "::error::app is not running after the smoke run"
fi
if [ "$CRASHES" != "0" ]; then
  echo "::error::$CRASHES fatal exception(s) in logcat"
  grep -A 25 'FATAL EXCEPTION' "$OUT/logcat.txt" | head -40
fi
if [ -z "$ALIVE" ] || [ "$CRASHES" != "0" ]; then
  exit 1
fi
log "smoke run OK"