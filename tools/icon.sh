#!/bin/bash
# Renders the Fidelis icon (tools/icon.html) everywhere the app and the README use it:
#   icons/icon-1024.png, icon-512.png, icon-192.png   README and banner: rounded, transparent corners
#   res/mipmap-nodpi/ic_launcher_full.png             Android 8+ adaptive icon layer: full bleed, record inside the safe zone
#   res/mipmap-nodpi/ic_launcher.png                  older Android: rounded
#   res/drawable/ic_launcher.png                      the old path, kept in case anything still points at it
# The themed icon and the status-bar icon are vectors (ic_launcher_monochrome.xml, ic_stat_fidelis.xml).
cd "$(dirname "$0")/.."
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
[ -x "$CHROME" ] || { echo "Google Chrome isn't in /Applications. Install it, or change CHROME in this file."; exit 1; }
RES=app/src/main/res
TMP="$(mktemp -d)"
mkdir -p icons "$RES/mipmap-nodpi" "$RES/drawable"
shoot() { # mode, file (always 1024 px; sips makes the smaller sizes)
  rm -f "$2"
  "$CHROME" --headless --disable-gpu --hide-scrollbars --no-first-run --no-default-browser-check --allow-file-access-from-files \
    --user-data-dir="$(mktemp -d)" --default-background-color=00000000 --force-device-scale-factor=1 --window-size=1024,1024 \
    --virtual-time-budget=2000 --screenshot="$2" "file://$PWD/tools/icon.html?mode=$1" >/dev/null 2>&1 || true
  [ -s "$2" ] || { echo "  $2 FAILED"; exit 1; }
}
size() { sips -Z "$1" "$2" --out "$3" >/dev/null && echo "  $3"; }
echo "Icon:"
shoot round "$TMP/round.png"
shoot full  "$TMP/full.png"
size 1024 "$TMP/round.png" icons/icon-1024.png
size 512  "$TMP/round.png" icons/icon-512.png
size 192  "$TMP/round.png" icons/icon-192.png
size 432  "$TMP/full.png"  "$RES/mipmap-nodpi/ic_launcher_full.png"
size 192  "$TMP/round.png" "$RES/mipmap-nodpi/ic_launcher.png"
size 512  "$TMP/round.png" "$RES/drawable/ic_launcher.png"
