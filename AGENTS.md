# AGENTS.md

Notes for agents and contributors working in this repository.

## What this is

Mediyo is an Android music player for YouTube Music: a single-module Android
app (`app/`) written in Kotlin with Jetpack Compose, Hilt for injection, Media3
for playback and Room for the local cache. The native core (`mediyo-core`) is
vendored as an AAR in `app/libs/` and is not built from here.

## The loop

1. Branch off `main`. One change per branch.
2. Commit with a conventional-commit subject: `type(scope): what changed`.
3. Open a pull request against `main`. **The pull request title is the
   conventional-commit subject** — the release-notes check reads the title, not
   the commit subject — and **the pull request description carries the release
   notes** (below).
4. CI builds and drives the app. There is no need to build locally: the `APK`
   job and the emulator tour cover it, and a local build of this project is
   slow. Build locally only when you were asked to.

The required checks on `main` are `APK` (build + lint), `Boot emulator + drive
app` (the emulator tour, which screenshots the app) and `Release notes`.

## Release notes — the block in the pull request description

**Read this before opening a pull request. The block is not reviewer notes: it
is the release note the user reads.**

On a squash merge the pull request description becomes the commit body, and the
release publishes exactly the blocks from those bodies — the notes on the
release page and in the app's "What's new". Nothing writes them by hand any
more, so a note that was never written is a note the release does not have.

The description therefore has two parts:

- **Outside the markers** — the developer-facing part: what was wrong, what
  changed, how it was verified. This is for whoever reviews it.
- **Inside the markers** — the user-facing part: what changed, and why someone
  using the app would care. This is what ships.

### The format

```markdown
<!-- notes -->
**Quick picks is a shelf of columns now.** It scrolls sideways and snaps from
one column to the next, with the next one peeking in from the right so you
know there is more.
<!-- /notes -->
```

- The markers are exactly `<!-- notes -->` and `<!-- /notes -->`.
- **One block per change a user can see.** A change that is three things to a
  user is three blocks, not one block of six paragraphs.
- A block opens with a bold sentence saying what changed, and then says why
  someone would care.
- Write it for someone who never sees the code.

### Which changes need one

Decided by the type in the pull request title:

| type | needs a note |
| --- | --- |
| `feat`, `fix`, `perf` | yes |
| `chore`, `ci`, `docs`, `test`, `refactor`, `build`, `style`, `release` | no |
| any scope `release`, `ci`, `build`, `deps` | no, whatever the type |

A change that needs no note can say so in a line of its own:

```
No-User-Note: this only moves the version resolver
```

The `no-user-note` label does not do it on its own: a label does not travel
with a commit, and the check that runs on a push to `main` only sees the
commit. Use the trailer.

### What the check rejects

A note that reads like a commit message is a note no user wants to read. The
check fails a note that:

- starts like a commit subject (`fix(lyrics): …`),
- contains backticks,
- names a file (`LyricsView.kt`, `scripts/check-notes.sh`, …),
- contains something that looks like a commit hash,
- uses developer words: *refactored*, *cherry-picked*, *rebased*,
  *backported*, *merge conflict*, *stack trace*, *null pointer*, *npe*, *wip*.

A note longer than 24 lines or 1500 characters warns.

### Checking it yourself

```bash
scripts/check-notes.sh --subject "<the pull request title>" --body-file /tmp/pr-body.md
```

It prints the notes it found and `ok: N note(s), and they read`, or tells you
exactly what is wrong. The script arrives with the release-notes rewrite; until
it is in `scripts/`, the format above is still the contract — it is what the
required check enforces.

### Two examples

Good — it says what changed and why someone would care:

```
<!-- notes -->
**Two lines at once now light up together.** When a lead vocal and a backing
vocal are sung at the same time, the app used to finish the first line and
only then jump to the second one. Every line that is being sung now lights up
at the same time.
<!-- /notes -->
```

Bad — developer words, a file name, and it opens like a commit subject:

```
<!-- notes -->
fix(lyrics): refactor the renderer in LyricsView.kt to handle overlapping lines
<!-- /notes -->
```

### And the changelog, for now

Until the release-notes rewrite lands, the release workflow also reads the
user-facing entries from the **Unreleased** section of `CHANGELOG.md` (see
`docs/releases.md`). Keep the two in step: the entries in `CHANGELOG.md` and
the blocks in the pull request description say the same thing in the same
words. The rewrite deletes `CHANGELOG.md` and makes the blocks the only
source — from then on, stop touching the changelog.

## Working on the code

- Follow the structure that is already there: one package per screen under
  `feature/`, the data layer under `data/`, the shared UI under `core/`. A new
  screen is a new package, not a new folder in an old one.
- Comments explain **why**, not what the next line does. The existing comments
  are the house style: they explain the decision and the failure they prevent.
  Match them.
- Keep the public surface of a package small. Prefer a small, focused data
  class over a wide one.
- Parser code (anything that turns a provider payload into a model) is
  defensive by design: a payload that is not what it should be must not crash
  the app. Fall back, do not throw.
- The app is a music player: it is used one-handed, in the dark, in a car.
  Anything that moves should be short and reversible.

## Checks and CI

| check | what it does |
| --- | --- |
| `APK` | builds the debug APK and runs `lintDebug` |
| `Boot emulator + drive app` | boots an emulator, drives the app over adb, uploads screenshots |
| `Release notes` | checks the release notes in the pull request description |

The emulator tour is `scripts/emulator-smoke.sh`; the adb control surface it
uses is described in `docs/adb-control.md`. A change to the player UI shows up
in the screenshots the tour uploads, so look at them.

## Where things live

| | |
| --- | --- |
| `app/src/main/kotlin/com/teamshryne/mediyo/feature/` | one package per screen |
| `app/src/main/kotlin/com/teamshryne/mediyo/data/` | repositories, parsers, caches, preferences |
| `app/src/main/kotlin/com/teamshryne/mediyo/core/` | the shared design system |
| `docs/` | adb control, releases, media3 opt-ins |
| `scripts/` | the helpers the workflows run |
| `.github/workflows/` | the build, the emulator tour, the release |
