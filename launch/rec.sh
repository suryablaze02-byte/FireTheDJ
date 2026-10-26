#!/usr/bin/env bash
# Records FireTheDJ clips on the connected phone for the launch video.
# Usage: rec.sh <step>   (each step leaves clips in launch/clips/)
export MSYS_NO_PATHCONV=1
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb -s ZA222L7V8X"
OUT="C:/Users/surya/DJ APP/launch/clips"
mkdir -p "$OUT"

# Taps the centre of the first element whose text or description starts with $1.
tap() {
  $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null
  local b; b=$($ADB shell cat /sdcard/ui.xml | grep -oE "(text|content-desc)=\"$1[^\"]*\"[^>]*bounds=\"[^\"]+\"" | head -1 | grep -oE 'bounds="[^"]+"' | grep -oE '[0-9]+' | tr '\n' ' ')
  set -- $b
  [ -z "$4" ] && { echo "tap: not found"; return 1; }
  $ADB shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
# Records $2 seconds into clips/$1.mp4 in the background; wait for it with `wait`.
rec() { $ADB shell screenrecord --bit-rate 16000000 --time-limit "$2" "/sdcard/$1.mp4" & }
pull() { $ADB pull "/sdcard/$1.mp4" "$OUT/$1.mp4" >/dev/null && $ADB shell rm "/sdcard/$1.mp4"; echo "clip $1 ok"; }

case "$1" in
  splash)
    $ADB shell am force-stop com.autodj
    rec splash 5; sleep 0.6; $ADB shell am start -n com.autodj/.ui.MainActivity >/dev/null; wait; pull splash ;;
  library)
    rec library 6; sleep 1.2; $ADB shell input swipe 540 1700 540 1100 1600; sleep 1; $ADB shell input swipe 540 1100 540 1700 1400; wait; pull library ;;
  ytmusic)
    tap "YT Music" && sleep 6
    rec ytmusic 7; sleep 1.5; $ADB shell input swipe 900 1300 200 1300 900; sleep 1.5; $ADB shell input swipe 540 1600 540 900 1200; wait; pull ytmusic
    $ADB shell input keyevent KEYCODE_BACK ;;
  cast)
    $ADB shell input tap 985 215; sleep 5; rec cast 4; wait; pull cast; tap "Close" ;;
  start)
    tap "Start AI mix" && sleep 6; rec nowplaying 6; wait; pull nowplaying ;;
  mixing)
    # Waits for the next transition (Now Playing says "AI mixes in m:ss"); the mixing view opens
    # by itself for it. Records from just before the effects start.
    $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null
    left=$($ADB shell cat /sdcard/ui.xml | grep -oE 'AI mixes in [0-9]+:[0-9]+' | head -1 | grep -oE '[0-9]+:[0-9]+')
    [ -z "$left" ] && { echo "no transition planned yet"; exit 1; }
    secs=$(( 10#${left%%:*} * 60 + 10#${left##*:} ))
    echo "transition in ${secs}s"
    sleep $(( secs > 2 ? secs - 2 : 0 )); rec mixing 12; wait; pull mixing ;;
  vis)
    # $2 = v (portrait) or h (landscape). Opens the full-screen visualizer and records each style
    # once its controls have hidden again.
    shape=$2
    tap "Visualizer" && sleep 2
    if [ "$shape" = h ]; then $ADB shell input tap 540 1200; sleep .8; tap "Landscape"; sleep 4.5; fi
    for st in "Club:club" "Neon Tunnel:tunnel" "Haze:haze" "LED Field:led"; do
      $ADB shell input tap 400 600; sleep .9
      tap "${st%%:*}"; sleep 4.3
      rec "vis_${st##*:}_$shape" 4; wait; pull "vis_${st##*:}_$shape"
    done
    $ADB shell input keyevent KEYCODE_BACK ;;
  *) echo "unknown step $1" ;;
esac
