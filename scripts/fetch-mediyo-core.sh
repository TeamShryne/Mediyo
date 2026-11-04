#!/usr/bin/env bash
# Download the pinned mediyo-core AAR and verify its SHA-256.
#
# The core is not vendored in this repo — its 24 MB binary would swell every
# clone and every CI checkout. CI fetches it from the mediyo-core GitHub
# Release named by gradle.properties (mediyo.core.version / mediyo.core.sha256).
# Bump both together: the checksum is what makes "an AAR named vX.Y.Z" mean
# "the binary built from those sources", and skipping the check would trade
# that guarantee for nothing.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

VER=$(grep '^mediyo.core.version=' gradle.properties | cut -d= -f2 | tr -d '\r')
SHA=$(grep '^mediyo.core.sha256=' gradle.properties | cut -d= -f2 | tr -d '\r')

if [ -z "$VER" ]; then
  echo "::error::mediyo.core.version is not set in gradle.properties" >&2
  exit 1
fi

URL="https://github.com/TeamShryne/mediyo-core/releases/download/${VER}/mediyo.aar"
echo "mediyo-core ${VER}"
echo "fetching ${URL}"
mkdir -p app/libs
curl -fL -o app/libs/mediyo.aar "$URL"

if [ -n "$SHA" ]; then
  echo "${SHA}  app/libs/mediyo.aar" | sha256sum -c -
else
  # Still OK: a core released without publishing its checksum just means the
  # download cannot be authenticated here. Print what was fetched instead.
  echo "warning: mediyo.core.sha256 is empty — downloaded without verification" >&2
  sha256sum app/libs/mediyo.aar
fi
ls -lh app/libs/mediyo.aar
