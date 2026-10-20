#!/usr/bin/env bash
# Tests scripts/check-notes.sh.
#
# The check is what stands between a change and a release with no notes, or
# with notes nobody wants to read. It runs on every pull request, so it has
# to be right about both: it must not let a missing note through, and it must
# not fail a change that legitimately needs no note.
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

FAILED=0

TMP=$(mktemp -d) || exit 1
trap 'rm -rf "$TMP"' EXIT

# run <subject> <body-file> [extra args...] -- sets RC and OUT.
run() {
  local subject="$1" body="$2"
  shift 2
  OUT=$(scripts/check-notes.sh --subject "$subject" --body-file "$body" "$@" 2>&1)
  RC=$?
}

# expect_ok <description> <subject> <body-file> [extra args...]
expect_ok() {
  local desc="$1" subject="$2" body="$3"
  shift 3
  run "$subject" "$body" "$@"
  if [ "$RC" = "0" ]; then
    echo "  ok   $desc"
  else
    echo "  FAIL $desc — exit $RC"
    echo "$OUT" | sed 's/^/         /'
    FAILED=1
  fi
}

# expect_fail <description> <subject> <body-file> <message the output must contain> [extra args...]
expect_fail() {
  local desc="$1" subject="$2" body="$3" want="$4"
  shift 4
  run "$subject" "$body" "$@"
  if [ "$RC" = "0" ]; then
    echo "  FAIL $desc — accepted a note it should have rejected"
    FAILED=1
    return
  fi
  if ! grep -qF "$want" <<<"$OUT"; then
    echo "  FAIL $desc — the message does not mention '$want'"
    echo "$OUT" | sed 's/^/         /'
    FAILED=1
    return
  fi
  echo "  ok   $desc"
}

body() {
  # body <name> <content> -- writes a body file and echoes its path.
  local name="$1" content="$2"
  local path="$TMP/$name"
  printf '%s\n' "$content" > "$path"
  printf '%s' "$path"
}

GOOD="**Quick picks is a shelf of columns now.** It scrolls sideways and snaps
from one column to the next, with the next one peeking in from the right."

echo "a change a user can see needs a note, and a good one passes"
GOOD_BODY=$(body good.md "<!-- notes -->
$GOOD
<!-- /notes -->")
expect_ok "a user-facing note on a feat" "feat(home): paged quick-picks columns" "$GOOD_BODY"

echo "a missing note is rejected"
EMPTY_BODY=$(body empty.md "Some technical description of the change.")
expect_fail "a feat with no notes block" "feat(home): paged quick-picks columns" \
  "$EMPTY_BODY" "needs release notes for users"

echo "a note that says nothing is rejected"
BLANK_BODY=$(body blank.md "<!-- notes -->
<!-- TODO: write this -->
<!-- /notes -->")
expect_fail "a feat with an empty notes block" "feat(home): paged quick-picks columns" \
  "$BLANK_BODY" "needs release notes for users"

echo "a note that reads like a commit subject is rejected"
SUBJECT_BODY=$(body subject.md "<!-- notes -->
feat(home): paged quick-picks columns and a queue-branching discovery graph
<!-- /notes -->")
expect_fail "a note that is a commit subject" "feat(home): paged quick-picks columns" \
  "$SUBJECT_BODY" "starts like a commit subject"

echo "developer words are rejected"
JARGON_BODY=$(body jargon.md "<!-- notes -->
The player was refactored so the clock re-anchors on every sample.
<!-- /notes -->")
expect_fail "a note that says 'refactored'" "fix(lyrics): the clock re-anchors" \
  "$JARGON_BODY" "written in developer words"

echo "file names, hashes and code spans are rejected"
FILE_BODY=$(body file.md "<!-- notes -->
The change lives in LyricsClock.kt now.
<!-- /notes -->")
expect_fail "a note that names a file" "fix(lyrics): the clock re-anchors" \
  "$FILE_BODY" "names a file"

HASH_BODY=$(body hash.md "<!-- notes -->
This reverts the change in 23d39e7 and 44e6c5a.
<!-- /notes -->")
expect_fail "a note that carries a commit hash" "fix(lyrics): the clock re-anchors" \
  "$HASH_BODY" "commit hash"

TICK_BODY=$(body tick.md "<!-- notes -->
The \`LyricsClock\` now re-anchors on every sample.
<!-- /notes -->")
expect_fail "a note with a code span" "fix(lyrics): the clock re-anchors" \
  "$TICK_BODY" "backticks"

echo "every block is checked, not just the first"
TWO_BAD_BODY=$(body twobad.md "<!-- notes -->
**The shelf scrolls sideways now.** It snaps from one column to the next.
<!-- /notes -->

<!-- notes -->
feat(home): paged quick-picks columns and a queue-branching discovery graph
<!-- /notes -->")
expect_fail "a good note and a commit subject" "feat(home): paged quick-picks columns" \
  "$TWO_BAD_BODY" "starts like a commit subject"

TWO_GOOD_BODY=$(body twogood.md "<!-- notes -->
**The shelf scrolls sideways now.** It snaps from one column to the next.
<!-- /notes -->

<!-- notes -->
**Pull down to refresh.** Drag the home screen downwards and it reloads.
<!-- /notes -->")
expect_ok "two good notes" "feat(home): paged quick-picks columns" "$TWO_GOOD_BODY"

echo "changes nobody can see need no note"
expect_ok "a chore" "chore(release): publish nightlies by hand" "$EMPTY_BODY"
expect_ok "a fix to the release machinery" "fix(release): export the nightly sequence number" "$EMPTY_BODY"
expect_ok "a ci change" "ci: let the emulator tour act as a merge gate" "$EMPTY_BODY"
expect_ok "a subject with no type" "Update the readme" "$EMPTY_BODY"

echo "the escape hatches work"
# The label alone is not enough: the check that runs on a push to main sees
# the commit, not the label, so the reason has to be in the body.
expect_fail "the no-user-note label without a reason" "feat(home): paged quick-picks columns" \
  "$EMPTY_BODY" "does not say why" --exempt
TRAILER_BODY=$(body trailer.md "Technical description of the change.

No-User-Note: this only changes the build script.")
expect_ok "a No-User-Note trailer in the body" "feat(home): paged quick-picks columns" "$TRAILER_BODY"
expect_ok "the label and the trailer together" "feat(home): paged quick-picks columns" \
  "$TRAILER_BODY" --exempt

echo "a block that is never closed is an error, not a missing note"
UNCLOSED_BODY=$(body unclosed.md "<!-- notes -->
**Something a user would notice.** The block never ends.")
expect_fail "an unterminated notes block" "feat(home): paged quick-picks columns" \
  "$UNCLOSED_BODY" "never closed"

echo "a template placeholder is not a note"
TEMPLATE_BODY=$(body template.md "<!-- notes -->
<!--
Write the user-facing note here.
-->
<!-- /notes -->")
expect_fail "the untouched template" "feat(home): paged quick-picks columns" \
  "$TEMPLATE_BODY" "needs release notes for users"

echo "a real note after a placeholder is still found"
AFTER_BODY=$(body after.md "<!-- notes -->
<!--
Write the user-facing note here.
-->
<!-- /notes -->

<!-- notes -->
**The shelf scrolls sideways now.** It snaps from one column to the next.
<!-- /notes -->")
expect_ok "a note below an untouched placeholder" "feat(home): paged quick-picks columns" \
  "$AFTER_BODY"

echo "a note that is one long line is fine"
LONG_BODY=$(body long.md "<!-- notes -->
**One line.** It is long but it says what changed and why a user would care about it.
<!-- /notes -->")
expect_ok "a single-line note" "feat(home): paged quick-picks columns" "$LONG_BODY"

echo "the usage is enforced"
OUT=$(scripts/check-notes.sh --subject "feat(x): y" 2>&1)
if [ $? = "0" ]; then
  echo "  FAIL accepted a call with no body file"
  FAILED=1
else
  echo "  ok   refuses a call with no body file"
fi

if [ "$FAILED" = "0" ]; then
  echo "check-notes: all good"
  exit 0
fi
echo "check-notes: FAILURES"
exit 1
