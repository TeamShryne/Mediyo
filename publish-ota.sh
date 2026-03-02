#!/usr/bin/env bash
set -euo pipefail

MSG="${1:-OTA update}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$SCRIPT_DIR"
BUILD_DIR="${BUILD_DIR:-$APP_DIR/.tmp/ota-build}"
OTA_REPO="${OTA_REPO:-$APP_DIR/../mediyo-ota-repo}"
BASE_URL="https://teamshryne.github.io/mediyo-ota"
APP_JSON="$APP_DIR/app.json"

echo "==> [1/5] Exporting Android OTA bundle"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
cd "$APP_DIR"
npx expo export --platform android --output-dir "$BUILD_DIR" --dump-assetmap

echo "==> [2/5] Generating manifest JSON files"
node "$APP_DIR/scripts/generate-ota-manifests.js" "$BUILD_DIR" "$APP_JSON" "$BASE_URL"
touch "$BUILD_DIR/.nojekyll"

echo "==> [3/5] Checking OTA repository"
if [ ! -d "$OTA_REPO/.git" ]; then
  echo "ERROR: $OTA_REPO is not a git repository."
  echo "Run this once:"
  echo "  git clone https://github.com/TeamShryne/mediyo-ota.git $OTA_REPO"
  exit 1
fi

echo "==> [4/5] Syncing build output to OTA repository"
if command -v rsync >/dev/null 2>&1; then
  rsync -av --delete "$BUILD_DIR"/ "$OTA_REPO"/
else
  find "$OTA_REPO" -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} +
  cp -a "$BUILD_DIR"/. "$OTA_REPO"/
fi

echo "==> [5/5] Commit + push"
cd "$OTA_REPO"
git add .
if git diff --cached --quiet; then
  echo "No OTA changes detected. Nothing to publish."
  exit 0
fi

git commit -m "$MSG"
git pull --rebase origin main
git push origin main

echo "Done. OTA published: $MSG"
