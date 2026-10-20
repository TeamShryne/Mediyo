#!/usr/bin/env bash
# Assembles the user-facing release notes for a range of commits.
#
#   scripts/collect-notes.sh <range> [--format markdown|json]
#
# <range> is a git range. For a release it is 'v0.8.0..v0.9.0'; for a
# nightly it is the last nightly tag to the build, e.g.
# 'nightly-0.8.0.3..HEAD'.
#
# The notes are the blocks the commit bodies carry -- the same ones
# scripts/check-notes.sh enforces -- so a release cannot ship a note nobody
# wrote, and it cannot ship a note that says nothing to a user.
#
# markdown: the notes, oldest change first, the way a release reads.
# json:     [{"commit": "<sha>", "text": "..."}] -- one entry per note, with
#           the commit it came from, so the app can tell two entries that
#           say the same thing apart and skip the one it has already shown.
#
# The commits are read in the order they landed, and a commit with no note is
# skipped: it either changed nothing a user sees, or it was exempt.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/notes-lib.sh
. "$SCRIPT_DIR/notes-lib.sh"

RANGE=""
FORMAT="markdown"

usage() {
  echo "usage: collect-notes.sh <range> [--format markdown|json]" >&2
}

while [ $# -gt 0 ]; do
  case "$1" in
    --format)
      [ $# -ge 2 ] || { echo "::error::--format needs a value"; exit 1; }
      FORMAT="$2"; shift 2 ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      RANGE="$1"; shift ;;
  esac
done

if [ -z "$RANGE" ]; then
  echo "::error::a range is required"
  usage
  exit 1
fi

case "$RANGE" in
  *..*) ;;
  *)
    echo "::error::the range must be like 'v0.8.0..v0.9.0' (got '$RANGE')"
    exit 1 ;;
esac

case "$FORMAT" in
  markdown|json) ;;
  *)
    echo "::error::the format must be 'markdown' or 'json' (got '$FORMAT')"
    exit 1 ;;
esac

if ! git rev-list "$RANGE" >/dev/null 2>&1; then
  echo "::error::'$RANGE' is not a range this repository knows"
  exit 1
fi

TMP=$(mktemp) || exit 1
trap 'rm -f "$TMP"' EXIT

COMMITS=()
TEXTS=()

# %x1f separates the commit from its body, %x1e separates the records. The
# body is read whole, because a note can be any number of lines.
while IFS= read -r -d $'\x1e' record; do
  # git puts a newline after every record, so the next one starts with one.
  record="${record#$'\n'}"
  [ -n "$record" ] || continue
  case "$record" in
    *$'\x1f'*) ;;
    *) continue ;;
  esac
  SHA="${record%%$'\x1f'*}"
  BODY="${record#*$'\x1f'}"

  printf '%s' "$BODY" > "$TMP"
  BLOCKS=$(notes_extract_all "$TMP")
  RC=$?
  if [ "$RC" = "2" ]; then
    echo "::error::commit $SHA has a notes block that is never closed" >&2
    exit 1
  fi
  [ "$RC" = "0" ] || continue

  # One note per block: a change that is three things to a user is three
  # notes, and the app shows them as three.
  while IFS= read -r -d $'\x1e' NOTE; do
    [ -n "$NOTE" ] || continue
    COMMITS+=("$SHA")
    TEXTS+=("$NOTE")
  done <<< "$BLOCKS"
done < <(git log --reverse --format='%H%x1f%B%x1e' "$RANGE")

COUNT=${#TEXTS[@]}

if [ "$FORMAT" = "json" ]; then
  printf '['
  i=0
  while [ "$i" -lt "$COUNT" ]; do
    if [ "$i" -gt 0 ]; then printf ','; fi
    printf '\n  {"commit": "%s", "text": "%s"}' \
      "${COMMITS[$i]}" "$(notes_json_escape "${TEXTS[$i]}")"
    i=$((i + 1))
  done
  if [ "$COUNT" -gt 0 ]; then printf '\n'; fi
  printf ']\n'
  exit 0
fi

# markdown: the notes in the order the changes landed, one blank line apart.
i=0
while [ "$i" -lt "$COUNT" ]; do
  if [ "$i" -gt 0 ]; then printf '\n'; fi
  printf '%s\n' "${TEXTS[$i]}"
  i=$((i + 1))
done
