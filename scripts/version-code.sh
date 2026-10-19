#!/usr/bin/env bash
# The one place versionCode is computed.
#
#   release: major*1_000_000 + minor*1_000 + patch
#   nightly: major*1_000_000 + minor*1_000 + 500 + seq
#
# Nightlies live in the 500..999 slot of a minor line, which gives the
# ordering the updater needs without any channel-aware comparison:
#
#   0.1.0        release  ->  1000
#   0.1.1        nightly  ->  1501   (outranks the release of that line)
#   0.2.0        release  ->  2000   (outranks every 0.1.x nightly)
#
# A stable install is therefore never offered a nightly (the stable feed does
# not carry it), and a nightly install is offered the next release as soon as
# it ships, because 2000 > 1999.
set -euo pipefail

MAJOR="${1:?usage: version-code.sh <major> <minor> <patch> [release|nightly] [seq]}"
MINOR="${2:?}"
PATCH="${3:?}"
KIND="${4:-release}"
SEQ="${5:-0}"

case "$KIND" in
  release)
    if [ "$PATCH" -gt 499 ]; then
      echo "error: release patch must be 0..499 (got $PATCH) — the 500+ band belongs to nightlies" >&2
      exit 1
    fi
    echo $(( MAJOR * 1000000 + MINOR * 1000 + PATCH ))
    ;;
  nightly)
    if [ "$SEQ" -lt 1 ] || [ "$SEQ" -gt 499 ]; then
      echo "error: nightly seq must be 1..499 (got $SEQ) — bump the minor version" >&2
      exit 1
    fi
    echo $(( MAJOR * 1000000 + MINOR * 1000 + 500 + SEQ ))
    ;;
  *)
    echo "error: kind must be 'release' or 'nightly' (got '$KIND')" >&2
    exit 1
    ;;
esac
