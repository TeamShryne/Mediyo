#!/usr/bin/env bash
# Shared helpers for the release-notes scripts.
#
# One place owns the notes format, so the checker that enforces it and the
# collector that publishes it cannot disagree about what a note is.
#
# A note lives in a commit or pull-request body, between two markers:
#
#   <!-- notes -->
#   **Something a user would notice.** What changed, and why they would care.
#   <!-- /notes -->
#
# The block is what a release ships. Everything outside it stays developer
# facing: the body of a pull request explains the change to whoever reviews
# it, the block explains it to the person using the app.
#
# Sourced, not executed.

NOTES_BEGIN='<!-- notes -->'
NOTES_END='<!-- /notes -->'

# Types whose changes a user can see. A change with one of these needs a note.
NOTES_REQUIRED_TYPES="feat fix perf"

# Types that never change what the app does for a user.
NOTES_EXEMPT_TYPES="chore ci docs test refactor build style release"

# Scopes that are the machinery rather than the app, whatever the type is.
NOTES_EXEMPT_SCOPES="release ci build deps"

# The escape hatch, as a trailer in the body: "No-User-Note: <why>". It lives
# in the body rather than only in a label because the body is what survives
# into the commit, and the check that runs on a push to main can only see the
# commit.
NOTES_EXEMPT_TRAILER="No-User-Note"

# notes_strip_comments -- drop HTML comments from stdin.
#
# The notes block is delimited by HTML comments, and the pull request
# template ships its placeholder inside one. Stripping comments out of the
# extracted text means a template that was never filled in reads as empty --
# which is what it is -- instead of shipping the placeholder to users.
notes_strip_comments() {
  awk '
    BEGIN { in_c = 0 }
    {
      line = $0; out = ""
      while (1) {
        if (in_c) {
          p = index(line, "-->")
          if (p == 0) { line = ""; break }
          line = substr(line, p + 3); in_c = 0
        } else {
          p = index(line, "<!--")
          if (p == 0) { out = out line; break }
          out = out substr(line, 1, p - 1)
          line = substr(line, p + 4); in_c = 1
        }
      }
      print out
    }
  '
}

# notes_trim -- strip trailing spaces, and the blank lines at both ends.
notes_trim() {
  sed -e 's/[[:space:]]*$//' | awk '
    { lines[NR] = $0 }
    END {
      start = 0; last = 0
      for (i = 1; i <= NR; i++) {
        if (lines[i] != "") { if (start == 0) start = i; last = i }
      }
      if (last == 0) exit
      for (i = start; i <= last; i++) print lines[i]
    }
  '
}

# The conventional-commit patterns, in variables: an unquoted pattern with
# parentheses is a syntax error inside [[ ]] on some shells.
RE_COMMIT_TYPE='^([A-Za-z]+)(\([^)]*\))?(!)?:'
RE_COMMIT_SCOPE='^[A-Za-z]+\(([^)]*)\)'

# notes_type_of <subject> -- the conventional-commit type, lowercased, or
# empty when the subject does not follow the convention.
notes_type_of() {
  local subject="${1:-}"
  if [[ "$subject" =~ $RE_COMMIT_TYPE ]]; then
    printf '%s' "${BASH_REMATCH[1],,}"
  fi
}

# notes_scope_of <subject> -- the conventional-commit scope, lowercased, or
# empty when there is none.
notes_scope_of() {
  local subject="${1:-}"
  if [[ "$subject" =~ $RE_COMMIT_SCOPE ]]; then
    printf '%s' "${BASH_REMATCH[1],,}"
  fi
}

# notes_exempt_reason <file> -- the "No-User-Note:" trailer, if the body
# carries one. Prints the reason and returns 0, or returns 1 without
# printing anything.
#
# Comments are stripped first: a line that mentions the trailer inside a
# comment -- as the pull request template does -- is not a trailer.
notes_exempt_reason() {
  local line
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      "$NOTES_EXEMPT_TRAILER":*)
        printf '%s\n' "${line#*:}"
        return 0
        ;;
    esac
  done < <(notes_strip_comments < "$1")
  return 1
}

# notes_extract_all <file> -- every notes block that says something, separated
# by \x1e.
#
#   0 = found, 1 = no block, 2 = a block was never closed.
#
# A commit can carry more than one block: one per change a user can see. A
# pull request that adds a shelf of columns, a pull-to-refresh gesture and a
# new set of shelves is three things to a user, and the release notes read
# better as three notes than as one block of six paragraphs.
#
# The first *non-empty* block of each is what counts: the pull request
# template ships a placeholder, and a placeholder that was never filled in
# is not a note.
notes_extract_all() {
  local file="$1" line rest content
  local -a block=()
  local state=0
  local have=0
  local raw=""

  while IFS= read -r line || [ -n "$line" ]; do
    if [ "$state" = "0" ]; then
      case "$line" in
        *"$NOTES_BEGIN"*)
          rest="${line#*"$NOTES_BEGIN"}"
          if [[ "$rest" == *"$NOTES_END"* ]]; then
            block=("${rest%%"$NOTES_END"*}")
            have=1
          else
            block=("$rest")
            state=1
          fi
          ;;
      esac
    else
      if [[ "$line" == *"$NOTES_END"* ]]; then
        block+=("${line%%"$NOTES_END"*}")
        have=1
        state=0
      else
        block+=("$line")
      fi
    fi

    if [ "$have" = "1" ]; then
      content=$(printf '%s\n' "${block[@]}" | notes_strip_comments | notes_trim)
      if [ -n "$content" ]; then
        raw+="$content"$'\x1e'
      fi
      have=0
      block=()
    fi
  done < "$file"

  [ "$state" = "1" ] && return 2
  [ -n "$raw" ] || return 1
  printf '%s' "$raw"
  return 0
}

# notes_extract <file> -- the first notes block that says something.
#
#   0 = found, 1 = no block, 2 = a block was never closed.
notes_extract() {
  local raw
  raw=$(notes_extract_all "$1")
  RC=$?
  [ "$RC" = "0" ] || return "$RC"
  printf '%s' "${raw%%$'\x1e'*}"
  return 0
}

# notes_json_escape <text> -- the text as a JSON string body: backslashes,
# newlines, tabs and quotes escaped, nothing else touched.
notes_json_escape() {
  local s="${1:-}"
  s="${s//\\/\\\\}"
  s="${s//$'\n'/\\n}"
  s="${s//$'\t'/\\t}"
  s="${s//$'\r'/\\r}"
  s="${s//\"/\\\"}"
  printf '%s' "$s"
}
