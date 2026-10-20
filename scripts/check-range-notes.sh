#!/usr/bin/env bash
# Checks every commit in a range for release notes.
#
#   usage: check-range-notes.sh <range>
#
# The same check the pull request runs, on the commits themselves: a change a
# user can see has to say what it does for them. The release runs this before
# it publishes, so a note that was never written cannot reach a user -- even
# if the check on the pull request was skipped, or the change landed on main
# without one.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

RANGE="${1:?usage: check-range-notes.sh <range>}"
case "$RANGE" in
  *..*) ;;
  *)
    echo "::error::the range must be like 'v0.8.0..v0.9.0' (got '$RANGE')" >&2
    exit 1 ;;
esac

if ! git rev-list "$RANGE" >/dev/null 2>&1; then
  echo "::error::'$RANGE' is not a range this repository knows" >&2
  exit 1
fi

FAILED=0
BODY=$(mktemp)
trap 'rm -f "$BODY"' EXIT

# Oldest first: the notes come out in the order the changes landed, and a
# failure reads better in that order too.
for SHA in $(git rev-list --reverse "$RANGE"); do
  SUBJECT=$(git log -1 --format=%s "$SHA")
  git log -1 --format=%B "$SHA" > "$BODY"
  if ! "$SCRIPT_DIR/check-notes.sh" --subject "$SUBJECT" --body-file "$BODY"; then
    echo "::error::commit $SHA has no release notes for users" >&2
    FAILED=1
  fi
done

exit "$FAILED"
