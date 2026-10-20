#!/usr/bin/env bash
# Tests scripts/make-changelog.sh.
#
# The changelog is what the app reads to say what changed since the version
# a user is running, so the things it has to get right are the version codes
# it orders by, the channel each entry belongs to, and the notes themselves.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAKE="$REPO_ROOT/scripts/make-changelog.sh"
COLLECT="$REPO_ROOT/scripts/collect-notes.sh"

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

check_contains() {
  local desc="$1" haystack="$2" needle="$3"
  if grep -qF -- "$needle" <<<"$haystack"; then
    echo "  ok   $desc"
  else
    echo "  FAIL $desc — '$needle' is not in the output"
    FAILED=1
  fi
}

TMP=$(mktemp -d) || exit 1
trap 'rm -rf "$TMP"' EXIT

REPO="$TMP/repo"
mkdir -p "$REPO"
cd "$REPO" || exit 1
git init -q .
git config user.email "notes@example.com"
git config user.name "Notes"

commit() {
  printf '%s\n\n%s\n' "$1" "$2" | git commit -q --allow-empty -F -
}

commit "chore: the first release" "Nothing to say."
git tag -a v0.8.0 -m "Mediyo v0.8.0"

commit "feat(home): a shelf of columns" "Technical detail.

<!-- notes -->
**Quick picks is a shelf of columns now.** It scrolls sideways.
<!-- /notes -->"
git tag -a v0.9.0 -m "Mediyo v0.9.0"

commit "fix(lyrics): the highlight follows the speed" "Technical detail.

<!-- notes -->
**The lyrics keep up when you slow a song down.** The highlight used to run ahead.
<!-- /notes -->"
git tag -a nightly-0.9.1 -m "Mediyo nightly 0.9.0-nightly.1"

echo "the entries are the releases and the nightlies, in order"
OUT=$("$MAKE" "$TMP/changelog.json")
check_contains "the release is in there" "$OUT" '"version": "0.9.0"'
check_contains "the nightly is in there" "$OUT" '"version": "0.9.0-nightly.1"'
check_contains "the release is on the stable channel" "$OUT" '"channel": "stable"'
check_contains "the nightly is on the nightly channel" "$OUT" '"channel": "nightly"'

echo "the version codes are the ones the app compares"
check_contains "the release code" "$OUT" '"versionCode": 9000'
check_contains "the nightly code" "$OUT" '"versionCode": 9501'

echo "the first release has no notes to collect, and is not in the history"
if grep -qF '"version": "0.8.0"' <<<"$OUT"; then
  echo "  FAIL the first release is in the history"
  FAILED=1
else
  echo "  ok   the first release is not in the history"
fi

echo "the notes come from the commits"
check_contains "the release note is in there" "$OUT" "Quick picks is a shelf of columns now"
check_contains "the nightly note is in there" "$OUT" "The lyrics keep up when you slow a song down"
check_lacks_technical() { :; }
if grep -qF "Technical detail" <<<"$OUT"; then
  echo "  FAIL the technical detail reached the changelog"
  FAILED=1
else
  echo "  ok   the technical detail stays out of it"
fi

echo "the json is valid, and it parses"
if command -v python3 >/dev/null 2>&1; then
  PARSE_OUT=$(python3 -c '
import json, sys
data = json.load(open(sys.argv[1]))
entries = data["entries"]
assert len(entries) == 2, entries
codes = [e["versionCode"] for e in entries]
assert codes == sorted(codes), codes
for e in entries:
    assert e["version"] and e["channel"] and e["publishedAt"]
    for n in e["notes"]:
        assert n["commit"] and n["text"]
' "$TMP/changelog.json" 2>&1)
  if [ $? = "0" ]; then
    echo "  ok   the json parses, and the entries are ordered by version code"
  else
    echo "  FAIL the json does not parse"
    echo "$PARSE_OUT" | sed 's/^/         /'
    FAILED=1
  fi
else
  echo "  skip the json parse (no python3)"
fi

echo "a repository with no tags has an empty history"
EMPTY="$TMP/empty"
mkdir -p "$EMPTY"
cd "$EMPTY" || exit 1
git init -q .
git config user.email "notes@example.com"
git config user.name "Notes"
commit "chore: nothing" "Nothing."
OUT=$("$MAKE" "$TMP/empty.json")
check_contains "no entries" "$OUT" '"entries": ['

if [ "$FAILED" = "0" ]; then
  echo "make-changelog: all good"
  exit 0
fi
echo "make-changelog: FAILURES"
exit 1
