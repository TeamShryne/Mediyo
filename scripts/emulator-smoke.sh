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
APK="${APK:-${APK_PATH:-app/build/outputs/apk/debug/app-debug.apk}}"
OUT="${OUT:-artifacts}"
QUERY="${QUERY:-daft punk one more time}"

mkdir -p "$OUT/screenshots"

log()  { echo "  $*"; }
step() { echo "== $1"; }

send() {
  adb shell am broadcast \
    -a "$ACTION" \
    -p "$PKG" \
    --es cmd "$1" "${@:2}" >/dev/null 2>&1 || log "broadcast '$1' returned non-zero"
}

# The device shell re-splits whatever adb sends, so a quoted "--es query
# two words" arrives as just "two". The inner single quotes survive one
# round of that re-splitting and reach the receiver intact.
q() { printf "'%s'" "$1"; }

N=0
shot() {
  N=$((N + 1))
  local name
  name=$(printf "%02d-%s" "$N" "$1")
  sleep "$2"
  adb exec-out screencap -p > "$OUT/screenshots/$name.png" 2>/dev/null
  if [ -s "$OUT/screenshots/$name.png" ]; then
    log "captured $name.png"
  else
    log "WARNING: empty screenshot $name.png"
  fi
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

# Gesture navigation, to match a modern phone. The default emulator image uses
# 3-button nav, whose 48dp strip is twice as tall and makes sheet content
# colliding with the system bar look far worse than it does on a real device.
# Without this the screenshots misrepresent the app.
step "switching to gesture navigation"
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1 || true
adb shell settings put secure navigation_mode 2 >/dev/null 2>&1 || true
sleep 3
adb shell wm dismiss-keyguard >/dev/null 2>&1

step "installing $APK"
if ! adb install -r -t "$APK"; then
  echo "::error::apk install failed"
  exit 1
fi
# The notification permission dialog would sit on top of the first screen.
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

focus() { adb shell dumpsys window 2>/dev/null | grep -m1 -E 'mCurrentFocus' || true; }

step "launching"
adb shell am start -n "$PKG/$ACTIVITY" >/dev/null 2>&1
for _ in $(seq 1 30); do
  adb shell pidof "$PKG" >/dev/null 2>&1 && break
  sleep 1
done

# ── Tabs ────────────────────────────────────────────────────────────────────
for t in home search library settings; do
  step "tab: $t"
  send go --es route "$t"
  shot "tab-$t" 7
done

# ── Settings sub-screens ────────────────────────────────────────────────────
for t in settings/content/lyrics settings/updates settings/appearance settings/appearance/tabs \
         settings/appearance/player settings/appearance/lyrics settings/equalizer settings/storage; do
  step "screen: $t"
  send go --es route "$t"
  shot "screen-${t//\//-}" 6
done

# ── Library pages ───────────────────────────────────────────────────────────
for t in liked history downloads profile; do
  step "screen: $t"
  send go --es route "$t"
  shot "screen-$t" 6
done

# ── Search, and the ids needed for the browse pages ────────────────────────
step "search"
send search --es query "$(q "$QUERY")"
shot "screen-search" 12

step "resolve ids"
send resolve --es query "$(q "$QUERY")"
sleep 6
adb logcat -d -s MediyoAdb:D > "$OUT/resolve.log" 2>/dev/null
id_for() { grep -m1 "resolve $1 id=" "$OUT/resolve.log" 2>/dev/null | sed 's/.* id=\([^ ]*\).*/\1/'; }

for kind in artist album playlist; do
  id=$(id_for "$kind")
  if [ -n "$id" ]; then
    step "screen: $kind/$id"
    send go --es route "$kind" --es arg "$id"
    shot "screen-$kind" 10
  else
    log "no $kind id resolved, skipping"
  fi
done

# ── Playback ────────────────────────────────────────────────────────────────
step "play"
send play --es query "$(q "$QUERY")"
shot "screen-player-mini" 18

step "overlay: expanded player"
send overlay --es target player
shot "overlay-player" 8

step "sheet: track menu (from player)"
send sheet --es target menu
shot "sheet-menu-from-player" 6
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1
sleep 2

step "overlay: queue"
send overlay --es target queue
shot "overlay-queue" 8
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1
sleep 2

# ── Every sheet ─────────────────────────────────────────────────────────────
step "sheet: media menu"
send sheet --es target media
shot "sheet-media-menu" 6

step "sheet: comments"
send sheet --es target comments
shot "sheet-comments" 12

step "sheet: add to playlist"
send sheet --es target playlist
shot "sheet-add-to-playlist" 8

step "sheet: sleep timer"
send sheet --es target sleep
shot "sheet-sleep-timer" 8

step "sheet: track menu (root host)"
send sheet --es target menu
shot "sheet-menu-root" 6

# ── Second pass: 3-button navigation ────────────────────────────────────────
# The tour above runs in gesture navigation, which is what a modern phone
# shows. Three-button nav has a 48dp bar instead of a 24dp handle, so sheet
# content colliding with the system bar is far more visible there — that is
# the configuration that exposed the sheet system-bar bugs in the first
# place, so keep it covered.
step "switching to 3-button navigation"
adb shell cmd overlay disable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1 || true
adb shell settings put secure navigation_mode 0 >/dev/null 2>&1 || true
sleep 4

send sheet --es target menu
shot "3btn-sheet-menu" 6
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1
sleep 2

send overlay --es target player
sleep 3
send sheet --es target menu
shot "3btn-sheet-menu-player" 6
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1
sleep 2

send sheet --es target comments
shot "3btn-sheet-comments" 10
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1
sleep 2

step "collecting logs"
adb logcat -d -s MediyoAdb:D > "$OUT/mediyo-adb.log" 2>/dev/null
adb logcat -d -s MediyoSheet:D > "$OUT/mediyo-sheet.log" 2>/dev/null
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null
log "nav bar inset:"
sed 's/^/    /' "$OUT/mediyo-sheet.log" 2>/dev/null | head -3

ALIVE="$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r')"
# `grep -c` prints 0 *and* exits 1 when nothing matches, so a trailing `|| echo 0`
# appends a second 0 and turns "clean" into "0\n0" — which then fails the
# != "0" check and fails the job on a perfectly healthy run.
CRASHES="$(grep -c 'FATAL EXCEPTION' "$OUT/logcat.txt" 2>/dev/null || true)"
CRASHES="${CRASHES:-0}"
# YouTube blocks GitHub runner IPs for anonymous playback (LOGIN_REQUIRED /
# "confirm you're not a bot"), so a loaded queue that will not start playing is
# expected here and is reported rather than treated as a failure.
BLOCKED="$(grep -c 'SignInConfirmNotBot' "$OUT/logcat.txt" 2>/dev/null || true)"
BLOCKED="${BLOCKED:-0}"

{
  echo "package_pid=${ALIVE:-none}"
  echo "fatal_exceptions=$CRASHES"
  echo "youtube_ip_blocked=$BLOCKED"
  echo "screenshots=$(ls -1 "$OUT/screenshots" 2>/dev/null | wc -l)"
} > "$OUT/summary.txt"

if [ -z "$ALIVE" ]; then
  echo "::error::app is not running after the smoke run"
fi
if [ "$CRASHES" != "0" ]; then
  echo "::error::$CRASHES fatal exception(s) in logcat"
  grep -A 25 'FATAL EXCEPTION' "$OUT/logcat.txt" | head -40
fi
if [ "$BLOCKED" != "0" ]; then
  log "note: YouTube blocked anonymous playback for this runner IP ($BLOCKED hits);"
  log "      queue/search assertions still hold, audio will not start."
fi
if [ -z "$ALIVE" ] || [ "$CRASHES" != "0" ]; then
  exit 1
fi
log "smoke run OK"