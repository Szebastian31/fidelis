#!/bin/bash
# Takes the README screenshots from the plugged-in phone, then renders screenshots/banner.png.
#   tools/screenshots.sh                 take all five, then make the banner
#   tools/screenshots.sh --banner-only   just rebuild the banner from the existing screenshots
set -e
cd "$(dirname "$0")/.."
OUT=screenshots
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
mkdir -p "$OUT"

demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null 2>&1 || true; }
demo_on() {
  adb shell settings put global sysui_demo_allowed 1 >/dev/null 2>&1 || true
  demo enter
  demo clock -e hhmm 0941
  demo battery -e level 100 -e plugged false
  demo network -e wifi show -e level 4
  demo notifications -e visible false
}
demo_off() { demo exit; }

if [ "$1" != "--banner-only" ]; then
  adb get-state >/dev/null 2>&1 || { echo "No phone found. Plug it in with USB debugging on."; exit 1; }
  trap demo_off EXIT
  demo_on
  shot() {
    echo
    read -r -p "$2  Press Enter when it's on screen… " _
    adb exec-out screencap -p > "$OUT/$1.png"
    echo "  saved $OUT/$1.png"
  }
  echo "Play a song in Fidelis first, so the player and mini player have something to show."
  shot listen "Open the Listen tab (scroll to the very top)."
  shot player "Open the player."
  shot lyrics "Tap the lyrics button and wait for the words."
  shot artist "Open an artist page (Search → tap an artist)."
  shot mood   "Open the Mood tab."
fi

for s in listen player lyrics artist mood; do
  [ -s "$OUT/$s.png" ] || { echo "Missing $OUT/$s.png. Run tools/screenshots.sh without --banner-only."; exit 1; }
done
[ -x "$CHROME" ] || { echo "Google Chrome not found; the screenshots are saved, only the banner is skipped."; exit 1; }
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
  --allow-file-access-from-files --window-size=2400,1260 \
  --screenshot="$PWD/$OUT/banner.png" "file://$PWD/tools/banner.html" >/dev/null 2>&1
echo
echo "Done: $OUT/banner.png and five screenshots."
