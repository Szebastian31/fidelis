#!/bin/bash
# Waits for the build of the commit you just pushed, then installs its APK on the plugged-in phone.
#   tools/install.sh
set -e
cd "$(dirname "$0")/.."
SHA=$(git rev-parse HEAD)
echo "Waiting for the build of ${SHA:0:7}..."
ID=""
for _ in $(seq 1 30); do
  ID=$(gh run list --commit "$SHA" --limit 1 --json databaseId --jq '.[0].databaseId // empty')
  [ -n "$ID" ] && break
  sleep 4
done
[ -n "$ID" ] || { echo "No build found for ${SHA:0:7}. Did you push?"; exit 1; }
gh run watch "$ID" --exit-status >/dev/null || {
  echo "Build failed. Errors:"
  gh run view "$ID" --log-failed | grep -E 'e: |error:|FAILED' | head -n 40
  exit 1
}
DIR=$(mktemp -d)
gh release download latest-debug --pattern fidelis-debug.apk --dir "$DIR" --clobber
adb install -r "$DIR/fidelis-debug.apk"
echo "Installed build ${SHA:0:7}."
