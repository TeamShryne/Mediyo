#!/usr/bin/env bash
# Builds changelog.json: every release and every nightly, with its notes.
#
#   usage: make-changelog.sh <output.json>
#
# The history is rebuilt from the tags every time, so it cannot drift: the
# notes of a version are the notes in the commits it contains, and a commit
# carries its notes with it.
#
# One entry per release and per nightly:
#
#   { "version": "0.9.0", "versionCode": 9000, "channel": "stable",
#     "publishedAt": "...", "notes": [ { "commit": "...", "text": "..." } ] }
#
# The version code is what the app compares: a note is shown when its entry's
# code is above the code of the build the user is running. The commit in each
# note is what lets the app recognise a change it has already shown, which
# happens when a release rolls up commits that were already announced in a
# nightly.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

OUT="${1:?usage: make-changelog.sh <output.json>}"

emit_entry() {
  # emit_entry <version> <versionCode> <channel> <publishedAt> <range>
  local version="$1" code="$2" channel="$3" published="$4" range="$5"
  local notes
  notes=$("$SCRIPT_DIR/collect-notes.sh" "$range" --format json)
  [ -n "$notes" ] || notes="[]"
  ENTRIES+=("$(cat <<JSON
    {
      "version": "$version",
      "versionCode": $code,
      "channel": "$channel",
      "publishedAt": "$published",
      "notes": $notes
    }
JSON
)")
}

ENTRIES=()

# The releases, oldest first. The first tag has nothing before it, so it has
# no notes to collect: the notes started with the convention, not with the
# first release.
PREV=""
for tag in $(git tag -l 'v[0-9]*' --sort=v:refname); do
  VERSION="${tag#v}"
  if ! [[ "$VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    continue
  fi
  MAJOR="${BASH_REMATCH[1]}"
  MINOR="${BASH_REMATCH[2]}"
  PATCH="${BASH_REMATCH[3]}"
  CODE=$("$SCRIPT_DIR/version-code.sh" "$MAJOR" "$MINOR" "$PATCH" release)
  if [ -n "$PREV" ]; then
    emit_entry "$VERSION" "$CODE" "stable" "$(git log -1 --format=%cI "$tag")" "$PREV..$tag"
  fi
  PREV="$tag"
done

# The nightlies, oldest first. A nightly's notes are the commits since the
# previous nightly on the same line; the first nightly on a line starts from
# the release that line is built on.
for tag in $(git tag -l 'nightly-*.*.*' --sort=v:refname); do
  REST="${tag#nightly-}"
  if ! [[ "$REST" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    continue
  fi
  MAJOR="${BASH_REMATCH[1]}"
  MINOR="${BASH_REMATCH[2]}"
  SEQ="${BASH_REMATCH[3]}"
  CODE=$("$SCRIPT_DIR/version-code.sh" "$MAJOR" "$MINOR" 0 nightly "$SEQ")

  if [ "$SEQ" -gt 1 ]; then
    PREV="nightly-$MAJOR.$MINOR.$((SEQ - 1))"
  else
    PREV=$(git tag -l "v$MAJOR.$MINOR.*" --sort=v:refname | tail -1)
  fi
  [ -n "$PREV" ] || continue

  # The version a nightly was published as: the release it is built on, with
  # the sequence on the end.
  BASE=$(git describe --tags --abbrev=0 --match 'v*' "$tag^" 2>/dev/null || echo "")
  [ -n "$BASE" ] || continue
  VERSION="${BASE#v}-nightly.$SEQ"

  emit_entry "$VERSION" "$CODE" "nightly" "$(git log -1 --format=%cI "$tag")" "$PREV..$tag"
done

{
  echo "{"
  echo "  \"entries\": ["
  first=1
  for entry in "${ENTRIES[@]+"${ENTRIES[@]}"}"; do
    if [ "$first" = "1" ]; then
      first=0
    else
      echo ","
    fi
    echo "$entry"
  done
  echo "  ]"
  echo "}"
} > "$OUT"

cat "$OUT"
