#!/usr/bin/env bash
# Checks the user-facing release notes in a commit or pull-request body.
#
#   scripts/check-notes.sh --subject "<subject>" --body-file <path> [--exempt]
#
# A change a user can see has to say what it does for them, in their words.
# The note lives between two markers in the body:
#
#   <!-- notes -->
#   **Something a user would notice.** What changed, and why they would care.
#   <!-- /notes -->
#
# Nothing is written to the release notes by hand any more: the release
# assembles them from exactly these blocks, so a note that is missing here is
# a note the release does not have, and a note that reads like a commit
# subject is a note no user wants to read.
#
# The rules are the ones the changelog states: no file names, no commit
# hashes, no "refactored". A note is read by someone who never sees the code.
#
# --exempt is for the pull request check, where the `no-user-note` label says
# the change is not visible to users. The body can say the same thing with a
# "No-User-Note: <why>" trailer, which survives into the commit -- and which
# is therefore the only form the check on a push to main can see.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/notes-lib.sh
. "$SCRIPT_DIR/notes-lib.sh"

SUBJECT=""
BODY_FILE=""
EXEMPT=0

# The label a pull request can carry to say the change is not visible to
# users. It does not exempt the change on its own: the reason has to be in
# the body, where it travels into the commit.
EXEMPT_LABEL="no-user-note"

usage() {
  echo "usage: check-notes.sh --subject <subject> --body-file <path> [--exempt]" >&2
}

while [ $# -gt 0 ]; do
  case "$1" in
    --subject)
      [ $# -ge 2 ] || { echo "::error::--subject needs a value"; exit 1; }
      SUBJECT="$2"; shift 2 ;;
    --body-file)
      [ $# -ge 2 ] || { echo "::error::--body-file needs a value"; exit 1; }
      BODY_FILE="$2"; shift 2 ;;
    --exempt)
      EXEMPT=1; shift ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      echo "::error::unknown argument '$1'"
      usage
      exit 1 ;;
  esac
done

if [ -z "$SUBJECT" ] || [ -z "$BODY_FILE" ]; then
  echo "::error::--subject and --body-file are both required"
  usage
  exit 1
fi

if [ ! -f "$BODY_FILE" ]; then
  echo "::error::body file '$BODY_FILE' does not exist"
  exit 1
fi

# The body can exempt itself. This comes first: a trailer is a statement in
# the change itself, and a label can only be read on a pull request.
if REASON=$(notes_exempt_reason "$BODY_FILE"); then
  echo "ok: exempt — $REASON"
  exit 0
fi

if [ "$EXEMPT" = "1" ]; then
  # The label says the change is not visible to users, but the check that
  # runs on a push to main can only see the commit, and a label does not
  # travel with a commit. So the reason has to be in the body as well --
  # otherwise the same change passes here and fails there.
  echo "::error::this change is labelled '$EXEMPT_LABEL', but the description does not say why"
  echo "::error::add a '$NOTES_EXEMPT_TRAILER: <why>' line to the description as well: the check that runs on a push to main sees the commit, not the label"
  exit 1
fi

TYPE=$(notes_type_of "$SUBJECT")
SCOPE=$(notes_scope_of "$SUBJECT")

if [ -z "$TYPE" ]; then
  echo "ok: '$SUBJECT' has no conventional-commit type, so no note is required"
  echo "hint: 'feat(...):', 'fix(...):' and 'chore(...):' are what tell this check whether a note is needed"
  exit 0
fi

if [[ " $NOTES_EXEMPT_TYPES " == *" $TYPE "* ]]; then
  echo "ok: '$TYPE' changes nothing a user sees"
  exit 0
fi

if [ -n "$SCOPE" ] && [[ " $NOTES_EXEMPT_SCOPES " == *" $SCOPE "* ]]; then
  echo "ok: scope '$SCOPE' is the machinery, not the app"
  exit 0
fi

if [[ " $NOTES_REQUIRED_TYPES " != *" $TYPE "* ]]; then
  echo "ok: '$TYPE' is not a user-visible type"
  exit 0
fi

NOTES=$(notes_extract_all "$BODY_FILE")
RC=$?
if [ "$RC" = "2" ]; then
  echo "::error::a notes block is never closed — add '$NOTES_END'"
  exit 1
fi
if [ "$RC" != "0" ]; then
  echo "::error::a '$TYPE' change needs release notes for users"
  echo "::error::add a block like this to the description:"
  echo "::error::  $NOTES_BEGIN"
  echo "::error::  **Something a user would notice.** What changed, and why they would care."
  echo "::error::  $NOTES_END"
  echo "::error::one block per change a user can see — a change that is three things to a user is three blocks"
  echo "::error::or, if the change is not visible to users, add a '$NOTES_EXEMPT_TRAILER: <why>' line"
  exit 1
fi

FAILED=0
fail() {
  echo "::error::$1"
  FAILED=1
}

# Every block is checked: a pull request that carries one good note and one
# note that is a commit subject is not a pull request with release notes.
BLOCK=0
while IFS= read -r -d $'\x1e' NOTE; do
  [ -n "$NOTE" ] || continue
  BLOCK=$((BLOCK + 1))
  echo "note $BLOCK:"

  # A note that starts like a commit subject is a commit subject.
  FIRST=$(printf '%s\n' "$NOTE" | head -1)
  if [[ "$FIRST" =~ $RE_COMMIT_TYPE ]]; then
    fail "the note starts like a commit subject ('$FIRST') — write it for someone using the app"
  fi

  # Code spans are how a developer names a thing; a user is given plain words.
  if printf '%s\n' "$NOTE" | grep -q '`'; then
    fail "the note contains backticks — name things in plain words"
  fi

  # A file name means the note is about the code, not about the app.
  FILES=$(printf '%s\n' "$NOTE" | grep -oE '[A-Za-z0-9_./-]+\.(kt|kts|go|java|xml|yml|yaml|json|pro|sh|md|gradle|toml|rs|c|h|cpp|aar)' | head -3 | tr '\n' ' ')
  if [ -n "${FILES// /}" ]; then
    fail "the note names a file ($FILES) — say what changed, not where it changed"
  fi

  # A hash is a commit hash to anyone reading it.
  HASHES=$(printf '%s\n' "$NOTE" | grep -oE '[0-9a-fA-F]{7,40}' | while read -r tok; do
    [[ "$tok" =~ [a-fA-F] ]] || continue
    [[ "$tok" =~ [0-9] ]] || continue
    echo "$tok"
  done | head -3 | tr '\n' ' ')
  if [ -n "${HASHES// /}" ]; then
    fail "the note contains what looks like a commit hash ($HASHES) — release notes carry no hashes"
  fi

  # The words of the trade.
  JARGON=$(printf '%s\n' "$NOTE" | grep -inE '\b(refactor(ed|ing)?|cherry[- ]pick(ed)?|rebase(d)?|backport(ed)?|merge conflict|stack ?trace|null pointer|npe|wip)\b' | head -3 | tr '\n' ' ')
  if [ -n "${JARGON// /}" ]; then
    fail "the note is written in developer words ($JARGON) — write it for someone using the app"
  fi

  LINES=$(printf '%s\n' "$NOTE" | wc -l | tr -d ' ')
  CHARS=${#NOTE}
  if [ "$LINES" -gt 24 ] || [ "$CHARS" -gt 1500 ]; then
    echo "::warning::a note is long ($LINES lines, $CHARS characters) — release notes are read on a phone"
  fi

  printf '%s\n' "$NOTE" | sed 's/^/  | /'
done <<< "$NOTES"

if [ "$BLOCK" = "0" ]; then
  fail "the notes block says nothing"
fi

if [ "$FAILED" = "1" ]; then
  exit 1
fi

echo "ok: $BLOCK note(s), and they read"
exit 0
