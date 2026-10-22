#!/usr/bin/env bash
# Merges a pull request, and carries its description into the commit.
#
#   scripts/merge-pr.sh <number | url> [--admin]
#
# The release reads its notes out of the body of the squash commit, and the
# squash button does not put the description there: this repository's merge
# settings write the branch's commit subjects instead. A pull request merged
# that way passes the `Release notes` check -- which reads the description --
# and then stops the next release, because the commit it landed as has no note
# in it at all. 0.8.0-nightly.4 failed to publish on exactly that.
#
# So the description is handed to the merge as the commit body, and the commit
# that comes back is read again: a note that only ever existed on the pull
# request is a note the release does not have.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PR=""
ADMIN=()
for arg in "$@"; do
  case "$arg" in
    --admin) ADMIN=(--admin) ;;
    -h|--help)
      sed -n '2,15p' "${BASH_SOURCE[0]}" | sed -e 's/^# \{0,1\}//'
      exit 0 ;;
    -*)
      echo "::error::unknown flag '$arg'" >&2
      exit 1 ;;
    *)
      PR="$arg" ;;
  esac
done

if [ -z "$PR" ]; then
  echo "usage: merge-pr.sh <number|url> [--admin]" >&2
  exit 1
fi

NUMBER=$(gh pr view "$PR" --json number --jq .number) || exit 1
TITLE=$(gh pr view "$PR" --json title --jq .title) || exit 1
STATE=$(gh pr view "$PR" --json state --jq .state) || exit 1
DRAFT=$(gh pr view "$PR" --json isDraft --jq .isDraft) || exit 1
LABELS=$(gh pr view "$PR" --json labels --jq '[.labels[].name] | join(",")') || exit 1

if [ "$STATE" != "OPEN" ]; then
  echo "::error::#$NUMBER is $STATE, not OPEN" >&2
  exit 1
fi
if [ "$DRAFT" = "true" ]; then
  echo "::error::#$NUMBER is a draft" >&2
  exit 1
fi

BODY_FILE=$(mktemp)
COMMIT_BODY=$(mktemp)
trap 'rm -f "$BODY_FILE" "$COMMIT_BODY"' EXIT

gh pr view "$NUMBER" --json body --jq .body > "$BODY_FILE" || exit 1

# The same check the pull request ran, on the text that is about to be written
# into the commit -- not on the description alone. The two are the same string
# here, which is the point of this script.
ARGS=(--subject "$TITLE" --body-file "$BODY_FILE")
case ",$LABELS," in
  *",no-user-note,"*) ARGS+=(--exempt) ;;
esac
"$SCRIPT_DIR/check-notes.sh" "${ARGS[@]}" || exit 1

# The title keeps the pull request number, as every commit on main does: the
# number is how a commit is traced back to the discussion that produced it.
SUBJECT="$TITLE (#$NUMBER)"

gh pr merge "$NUMBER" --squash --subject "$SUBJECT" --body-file "$BODY_FILE" \
  "${ADMIN[@]+"${ADMIN[@]}"}" || exit 1

MERGE_SHA=$(gh pr view "$NUMBER" --json mergeCommit --jq '.mergeCommit.oid // ""')
if [ -z "$MERGE_SHA" ]; then
  echo "::error::#$NUMBER merged, but GitHub reports no merge commit to check" >&2
  exit 1
fi

# The commit GitHub produced is the thing the release reads. Read it back:
# a merge that quietly dropped the body looks identical from here otherwise.
git fetch --quiet origin main || true
if ! git log -1 --format=%B "$MERGE_SHA" > "$COMMIT_BODY" 2>/dev/null; then
  echo "::error::#$NUMBER merged as $MERGE_SHA, but that commit is not in this clone — run 'git fetch origin main' and check it carries the notes" >&2
  exit 1
fi

if ! "$SCRIPT_DIR/check-notes.sh" \
    --subject "$(git log -1 --format=%s "$MERGE_SHA")" \
    --body-file "$COMMIT_BODY" > /dev/null; then
  echo "::error::#$NUMBER landed without its release notes: the body of $MERGE_SHA is not the description" >&2
  echo "::error::the next release will refuse to publish it — amend the commit body to the pull request description and force-push main" >&2
  exit 1
fi

echo "merged #$NUMBER as $MERGE_SHA, with its description in the commit"
