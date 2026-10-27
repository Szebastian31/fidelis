#!/bin/bash
# Waits for the newest GitHub Actions build, downloads its APK and installs it on the plugged-in phone.
#   tools/install.sh
set -e
cd "$(dirname "$0")/.."
sleep 5 # give GitHub a moment to start the run after a push
ID=$(gh run list --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$ID" --exit-status || { echo "Build failed. Errors: gh run view $ID --log-failed | grep 'e: '"; exit 1; }
DIR=$(mktemp -d)
gh run download "$ID" --dir "$DIR"
APK=$(find "$DIR" -name '*.apk' | head -n 1)
[ -n "$APK" ] || { echo "No APK in that build."; exit 1; }
adb install -r "$APK"
echo "Installed $(basename "$APK")"
