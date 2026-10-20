#!/usr/bin/env bash
# Tests scripts/collect-notes.sh.
#
# The collector is what a release publishes, so it has to get two things
# right: the notes come out in the order the changes landed, and the ones
# that belong to a commit are the ones that commit carried.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
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

check_lacks() {
  local desc="$1" haystack="$2" needle="$3"
  if grep -qF -- "$needle" <<<"$haystack"; then
    echo "  FAIL $desc — '$needle' should not be in the output"
    FAILED=1
  else
    echo "  ok   $desc"
  fi
}

TMP=$(mktemp -d) || exit 1
trap 'rm -rf "$TMP"' EXIT

# A repository of our own: the collector must read the repository it is run
# in, not the one it lives in.
REPO="$TMP/repo"
mkdir -p "$REPO"
cd "$REPO" || exit 1
git init -q .
git config user.email "notes@example.com"
git config user.name "Notes"

commit() {
  # commit <message> <body>
  printf '%s\n\n%s\n' "$1" "$2" | git commit -q --allow-empty -F -
}

commit "chore: first" "Nothing to say here."
FIRST=$(git rev-parse HEAD)

commit "feat(home): paged quick-picks columns" "Technical detail about the carousel.

<!-- notes -->
**Quick picks is a shelf of columns now.** It scrolls sideways and snaps from
one column to the next.
<!-- /notes -->"
SECOND=$(git rev-parse HEAD)

commit "fix(lyrics): the highlight follows the speed" "Technical detail about the clock.

<!-- notes -->
**The lyrics keep up when you slow a song down.** The highlight used to run
ahead of the music and then jump back.
<!-- /notes -->"
THIRD=$(git rev-parse HEAD)

git tag v0.8.0 "$FIRST"

echo "the notes come out in the order the changes landed"
OUT=$("$COLLECT" "$FIRST..$THIRD")
FIRST_LINE=$(grep -n 'shelf of columns' <<<"$OUT" | cut -d: -f1)
SECOND_LINE=$(grep -n 'lyrics keep up' <<<"$OUT" | cut -d: -f1)
if [ "$FIRST_LINE" -lt "$SECOND_LINE" ]; then
  echo "  ok   the first change is the first note"
else
  echo "  FAIL the notes are not in the order the changes landed ($FIRST_LINE then $SECOND_LINE)"
  FAILED=1
fi

echo "a commit with no note contributes nothing"
check_lacks "the empty commit is not in the notes" "$OUT" "Nothing to say here"
check_contains "the first note is there" "$OUT" "shelf of columns now"
check_contains "the second note is there" "$OUT" "lyrics keep up when you slow"

echo "the technical part of the body never reaches the notes"
check_lacks "the technical detail is not in the notes" "$OUT" "Technical detail"
check_lacks "the markers are not in the notes" "$OUT" "<!-- notes -->"

echo "a range with no notes is empty, not an error"
OUT=$("$COLLECT" "$FIRST..$FIRST" 2>/dev/null)
check "an empty range gives nothing" "" "$OUT"

echo "the json format carries the commit as the id"
JSON=$("$COLLECT" "$FIRST..$THIRD" --format json)
check_contains "the json has the first commit" "$JSON" "\"commit\": \"$SECOND\""
check_contains "the json has the second commit" "$JSON" "\"commit\": \"$THIRD\""
check_contains "the json has the text" "$JSON" "shelf of columns now"

echo "the json is valid, and the text survives the round trip"
if command -v python3 >/dev/null 2>&1; then
  PARSE_OUT=$(echo "$JSON" | python3 -c '
import json, sys
entries = json.load(sys.stdin)
assert len(entries) == 2, entries
assert entries[0]["commit"] and entries[1]["commit"]
assert entries[0]["text"].startswith("**Quick picks"), entries[0]["text"]
assert "\n" in entries[0]["text"], "the line break was lost"
assert "<!-- notes -->" not in entries[0]["text"]
' 2>&1)
  if [ $? = "0" ]; then
    echo "  ok   the json parses and keeps the text intact"
  else
    echo "  FAIL the json does not parse, or the text was mangled"
    echo "$PARSE_OUT" | sed 's/^/         /'
    FAILED=1
  fi
else
  echo "  skip the json parse (no python3)"
fi

echo "a commit that is two things to a user is two notes"
commit "feat(home): a shelf and a gesture" "Technical detail.

<!-- notes -->
**Pull down to refresh.** Drag the home screen downwards and it reloads.
<!-- /notes -->

<!-- notes -->
**A tap is not a play.** A track has to be heard for ten seconds.
<!-- /notes -->"
FOURTH=$(git rev-parse HEAD)
JSON2=$("$COLLECT" "$FIRST..$FOURTH" --format json)
NOTE_COUNT=$(grep -c '"commit"' <<<"$JSON2")
check "the commit contributes two notes" "4" "$NOTE_COUNT"
if grep -qF "\"commit\": \"$FOURTH\"" <<<"$JSON2"; then
  echo "  ok   both notes carry the commit they came from"
else
  echo "  FAIL the notes do not carry their commit"
  FAILED=1
fi
MD2=$("$COLLECT" "$FIRST..$FOURTH")
check_contains "the first of the two notes is there" "$MD2" "Pull down to refresh"
check_contains "the second of the two notes is there" "$MD2" "A tap is not a play"

echo "a range that is not a range is refused"
if "$COLLECT" "not-a-range" >/dev/null 2>&1; then
  echo "  FAIL accepted a range that is not a range"
  FAILED=1
else
  echo "  ok   refuses a range that is not a range"
fi

echo "a range this repository does not know is refused"
if "$COLLECT" "v0.0.1..v0.0.2" >/dev/null 2>&1; then
  echo "  FAIL accepted a range with unknown tags"
  FAILED=1
else
  echo "  ok   refuses a range with unknown tags"
fi

if [ "$FAILED" = "0" ]; then
  echo "collect-notes: all good"
  exit 0
fi
echo "collect-notes: FAILURES"
exit 1
