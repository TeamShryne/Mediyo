#!/usr/bin/env bash
# Tests scripts/resolve-version.sh.
#
# The release path used to compute these values inline in the workflow, so
# nothing ran them until a release was actually published. A value that was
# computed but never exported to $GITHUB_OUTPUT shipped as a broken nightly
# before this test existed.
#
# The important assertion is not the numbers but the KEYS: every key the
# workflow reads back must be present, whatever the mode.
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

FAILED=0

check() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "  ok   $desc"
  else
    echo "  FAIL $desc — expected '$expected', got '$actual'"
    FAILED=1
  fi
}

# A herestring, not a filename: the output is multi-line, and grep would
# treat a multi-line argument as a path and fail with a confusing error.
value_of() { grep -m1 "^$2=" <<<"$1" | cut -d= -f2- ; }

echo "release: v0.8.0"
OUT=$(scripts/resolve-version.sh release v0.8.0)
check "mode"    "release"     "$(value_of "$OUT" mode)"
check "tag"     "v0.8.0"     "$(value_of "$OUT" tag)"
check "version" "0.8.0"       "$(value_of "$OUT" version)"
check "code"    "8000"        "$(value_of "$OUT" code)"
check "apkName" "mediyo-v0.8.0.apk" "$(value_of "$OUT" apkName)"
check "major"   "0"           "$(value_of "$OUT" major)"
check "minor"   "8"           "$(value_of "$OUT" minor)"

echo "nightly: after v0.8.0, sequence 1"
OUT=$(scripts/resolve-version.sh nightly v0.8.0 1)
check "mode"    "nightly"     "$(value_of "$OUT" mode)"
check "tag"     "nightly"     "$(value_of "$OUT" tag)"
check "version" "0.8.0-nightly.1" "$(value_of "$OUT" version)"
check "code"    "8501"        "$(value_of "$OUT" code)"
check "apkName" "mediyo-nightly-0.8.1.apk" "$(value_of "$OUT" apkName)"
check "seq"     "1"           "$(value_of "$OUT" seq)"

echo "nightly: the next sequence in the same line"
OUT=$(scripts/resolve-version.sh nightly v0.8.0 2)
check "code"    "8502"        "$(value_of "$OUT" code)"

echo "nightly: a new release line moves the band up"
OUT=$(scripts/resolve-version.sh nightly v0.9.0 1)
check "code"    "9501"        "$(value_of "$OUT" code)"
check "version" "0.9.0-nightly.1" "$(value_of "$OUT" version)"

# Every key the workflow reads back must exist, in both modes.
echo "every key the workflow reads back is present"
for mode in release nightly; do
  if [ "$mode" = "release" ]; then OUT=$(scripts/resolve-version.sh release v0.8.0); else OUT=$(scripts/resolve-version.sh nightly v0.8.0 1); fi
  for key in mode tag version code apkName major minor seq; do
    if grep -q "^$key=" <<<"$OUT"; then
      echo "  ok   $mode: $key"
    else
      echo "  FAIL $mode: $key is missing"
      FAILED=1
    fi
  done
done

echo "a nightly needs a stable release to exist first"
if scripts/resolve-version.sh nightly "" 1 >/dev/null 2>&1; then
  echo "  FAIL should have refused"
  FAILED=1
else
  echo "  ok   refuses without a stable release"
fi

echo "a bad tag is rejected"
if scripts/resolve-version.sh release vNOPE >/dev/null 2>&1; then
  echo "  FAIL should have refused"
  FAILED=1
else
  echo "  ok   refuses a malformed tag"
fi

if [ "$FAILED" = "0" ]; then
  echo "resolve-version: all good"
  exit 0
fi
echo "resolve-version: FAILURES"
exit 1
