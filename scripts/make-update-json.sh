#!/usr/bin/env bash
# The updater contract, in one place.
#
# The in-app updater fetches update.json, compares versionCode against the
# installed build, downloads apkUrl, and verifies sha256 before installing.
# If the release and nightly tracks generate this file differently, one of
# them silently breaks — so both call this script.
#
# usage: make-update-json.sh <version> <versionCode> <apkName> <apkUrl> <sha256> [publishedAt]
set -euo pipefail

VERSION="${1:?usage: make-update-json.sh <version> <versionCode> <apkName> <apkUrl> <sha256> [publishedAt]}"
CODE="${2:?}"
APK_NAME="${3:?}"
APK_URL="${4:?}"
SHA="${5:?}"
PUBLISHED="${6:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"

cat > update.json <<EOF
{
  "package": "com.teamshryne.mediyo",
  "version": "$VERSION",
  "versionCode": $CODE,
  "apkName": "$APK_NAME",
  "apkUrl": "$APK_URL",
  "sha256": "$SHA",
  "publishedAt": "$PUBLISHED"
}
EOF

cat update.json
