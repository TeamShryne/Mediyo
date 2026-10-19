#!/usr/bin/env bash
# Resolves the version coordinates for a release or a nightly.
#
#   resolve-version.sh release <tag>                    e.g. v0.8.0
#   resolve-version.sh nightly <latest-stable-tag> [seq]
#
# Prints KEY=VALUE lines: mode tag version code apkName major minor seq
#
# This lives in a script rather than inside the workflow so it can be tested
# by CI. The release path used to compute these values inline in the workflow,
# which meant nothing ever ran them until a release was actually published --
# and a value that was computed but never exported to $GITHUB_OUTPUT was
# exactly the bug that shipped. Every key the workflow reads back is asserted
# by scripts/test-resolve-version.sh, so a missing one fails the build.
set -euo pipefail

# Root ourselves: version-code.sh is called by relative path below.
cd "$(dirname "$0")/.." || exit 1

MODE="${1:?usage: resolve-version.sh <release|nightly> <tag> [seq]}"
ARG="${2:?}"
SEQ_OVERRIDE="${3:-}"

emit() { echo "$1=$2"; }

case "$MODE" in
  release)
    TAG="$ARG"
    VERSION="${TAG#v}"
    if ! [[ "$VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
      echo "::error::tag must be like v1.2.3 (got '$TAG')" >&2
      exit 1
    fi
    MAJOR="${BASH_REMATCH[1]}"
    MINOR="${BASH_REMATCH[2]}"
    PATCH="${BASH_REMATCH[3]}"
    CODE=$(scripts/version-code.sh "$MAJOR" "$MINOR" "$PATCH" release)
    APK_NAME="mediyo-$TAG.apk"
    SEQ=""
    ;;

  nightly)
    LATEST="$ARG"
    if [ -z "$LATEST" ]; then
      echo "::error::no stable release yet — cut one before publishing nightlies." >&2
      echo "::error::the nightly channel only exists in builds that support it." >&2
      exit 1
    fi
    BASE_VERSION="${LATEST#v}"
    MAJOR="${BASE_VERSION%%.*}"
    REST="${BASE_VERSION#*.}"
    MINOR="${REST%%.*}"
    # Sequence: how many nightlies this minor line already has. Monotonic
    # within the line, reset by the next release.
    if [ -n "$SEQ_OVERRIDE" ]; then
      SEQ="$SEQ_OVERRIDE"
    else
      SEQ=$(( $(git tag -l "nightly-$MAJOR.$MINOR.*" | wc -l) + 1 ))
    fi
    CODE=$(scripts/version-code.sh "$MAJOR" "$MINOR" 0 nightly "$SEQ")
    VERSION="${BASE_VERSION}-nightly.${SEQ}"
    APK_NAME="mediyo-nightly-${MAJOR}.${MINOR}.${SEQ}.apk"
    TAG=nightly
    # The nightly must outrank the release it is based on, or nobody on that
    # release would ever be offered it.
    LATEST_CODE=$(scripts/version-code.sh "$MAJOR" "$MINOR" 0 release)
    if [ "$CODE" -le "$LATEST_CODE" ]; then
      echo "::error::nightly code $CODE does not outrank $LATEST ($LATEST_CODE)" >&2
      exit 1
    fi
    ;;

  *)
    echo "::error::mode must be 'release' or 'nightly' (got '$MODE')" >&2
    exit 1
    ;;
esac

emit mode "$MODE"
emit tag "$TAG"
emit version "$VERSION"
emit code "$CODE"
emit apkName "$APK_NAME"
emit major "$MAJOR"
emit minor "$MINOR"
emit seq "$SEQ"
