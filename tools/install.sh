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
# the release for this version (one per versionName in app/build.gradle.kts)
VERSION=$(grep -m1 versionName app/build.gradle.kts | cut -d\" -f2)
DIR=$(mktemp -d)
gh release download "v${VERSION}" --pattern "Fidelis_v${VERSION}.apk" --dir "$DIR" --clobber
adb install -r "$DIR/Fidelis_v${VERSION}.apk"
echo "Installed Fidelis ${VERSION}, build ${SHA:0:7}."
