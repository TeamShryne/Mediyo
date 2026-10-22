# Releases and nightlies

Mediyo has two update tracks. Both are published as GitHub Releases and both
are read by the in-app updater, which fetches an `update.json` and compares
`versionCode` against the installed build.

| | Stable | Nightly |
| --- | --- | --- |
| What | a tagged release (`v1.2.3`) | a daily build of `main` |
| Guarantee | supported | tested, but less thoroughly than a release |
| Who gets it | everyone | whoever opts in via **Settings → App updates → Nightly** |
| Published as | a normal release | a **prerelease** |
| Version | `1.2.3` | `1.2.3-nightly.4` |

A nightly is the proving ground: a release should be a commit that has already
survived as a nightly for a few days.

## Why nightlies are prereleases

GitHub's `releases/latest` resolves to the newest **non-prerelease**. Nightlies
are published as prereleases precisely so that they stay out of that
namespace — otherwise every stable user would be offered a build of `main`.

The updater reaches them through a separate rolling release tagged `nightly`,
whose assets are replaced by each daily build. Only users who picked the
Nightly channel read that feed.

## The channel the app follows

The app stores the track the user picked and reads it **from disk** before
every check: the startup check, the manual check, and the check that follows
a channel switch. The stored value, not the one in memory — the preference is
read asynchronously, and a check that ran before the read landed would look
at the stable feed and tell a nightly user there was nothing new.

Picking Nightly asks once, in the app, before the channel changes. The
metadata URL carries a cache-busting stamp, because the nightly channel
republishes the same asset name every day and a cached copy would mean never
seeing the new build.

## The version code scheme

One script owns it: `scripts/version-code.sh`.

```
release  major*1_000_000 + minor*1_000 + patch
nightly  major*1_000_000 + minor*1_000 + 500 + seq
```

Nightlies live in the `500..999` slot of a minor line. That gives the ordering
the updater needs with no channel-aware comparison:

```
0.7.0   release  →  7000
0.7.1   nightly  →  7501   outranks the release of that line
0.8.0   release  →  8000   outranks every 0.7.x nightly
```

So a stable install is never offered a nightly (the stable feed does not carry
it), and a nightly install is offered the next release as soon as it ships,
because `8000 > 7999`.

The nightly's band follows the **latest stable release's** minor line, so it
moves up when a release ships. The sequence counter is the number of nightly
tags already on that line, so it resets at each release.

## Order matters: stable first, then nightly

**A stable release must exist before the first nightly.**

Two reasons, and the second is the one that bites:

1. The nightly channel only exists in builds that know about it. The release
   users currently have installed has no channel preference and always reads
   the stable feed, so it would never see a nightly.
2. The nightly's band is the latest stable release's minor line. A nightly
   published *before* that release lands in the old band, so users who then
   update to the new release are on a higher version code and are never
   offered it. It becomes unreachable.

`publish.yml` enforces this: it refuses to publish a nightly when no stable
release exists, and asserts the nightly's code outranks the release it is
based on.

## Where the notes come from

The notes live in the change. Nothing else writes them, and nothing else has
to.

### The notes live in the change

Every change a user can see carries its own release note, in a marked block in
the commit body — which, for a squash merge, is the pull request
description:

<!-- notes -->
**Quick picks is a shelf of columns now.** It scrolls sideways and snaps from
one column to the next.
<!-- /notes -->

The block is what a release publishes. Everything outside it stays developer
facing: the description of a pull request explains the change to whoever
reviews it, the block explains it to the person using the app.

The description only becomes the commit body if the merge was given it.
GitHub's squash button writes the commit message the repository's merge
settings describe — here, the branch's commit subjects — so a pull request
merged from the web page lands with its description left behind, and the next
release refuses to publish. `scripts/merge-pr.sh` is the merge: it passes the
description as the body and reads the commit back to check it arrived.

**One block per change a user can see.** A pull request that adds a shelf of
columns, a pull-to-refresh gesture and a new family of shelves is three
things to a user, and the release notes read better as three notes than as
one block of six paragraphs.

`scripts/check-notes.sh` enforces this, and the **Release notes** job in the
`Android` workflow runs it on every pull request: the note has to be there,
and it has to read like something written for a person — no file names, no
commit hashes, no "refactored". The pull request template carries the block,
so its shape is always in front of the author.

Which changes need one is decided by the conventional-commit type in the
subject:

| | needs a note |
| --- | --- |
| `feat`, `fix`, `perf` | yes |
| `chore`, `ci`, `docs`, `test`, `refactor`, `build`, `style`, `release` | no |
| any scope `release`, `ci`, `build`, `deps` | no, whatever the type |

A change that needs no note can say so in a line of its own:

```
No-User-Note: this only moves the version resolver
```

The `no-user-note` label does not do this on its own: a label does not travel
with a commit, and the check that runs on a push to main only sees the commit.
Use the trailer.

### The release reads them

`scripts/collect-notes.sh` reads the same blocks and assembles them for a
range of commits:

```bash
# the notes for the next release
scripts/collect-notes.sh v0.8.0..HEAD

# the notes for the next nightly: the commits since the last nightly
scripts/collect-notes.sh nightly-0.8.1..HEAD --format json
```

The markdown output is the release notes, oldest change first. The json output
is one entry per note — `{"commit": "<sha>", "text": "..."}` — with the
commit it came from, so the app can tell two entries that say the same thing
apart and skip the one it has already shown.

Before it publishes, a release runs `scripts/check-range-notes.sh` over the
same range: every change a user can see has to have said what it does for
them, or the release does not ship. So a note that was never written cannot
reach a user, even if the check on the pull request was skipped.

A release with nothing a user would notice still says so — *"Internal fixes
and stability improvements."* A nightly with no new commits says *"No changes
since the last build."*

### The app reads the history

Every release and every nightly also publishes `changelog.json`: the whole
history, with the notes, rebuilt from the tags by
`scripts/make-changelog.sh` so it cannot drift from the commits.

```json
{
  "entries": [
    {
      "version": "0.9.0",
      "versionCode": 9000,
      "channel": "stable",
      "publishedAt": "2026-10-20T15:20:17Z",
      "notes": [{ "commit": "23d39e7", "text": "**Quick picks is a shelf of columns now.** …" }]
    }
  ]
}
```

The app fetches it and shows the entries above the build the user is running:

- a stable entry counts on either channel, because a nightly is built on top
  of the latest release;
- a nightly entry counts only for a user who follows the nightly channel;
- the commit in each note is what tells two notes apart, because a release
  rolls up the commits a nightly already announced — a user who was on the
  nightly channel and then moved to stable would otherwise read it twice.

The two feeds are `releases/latest/download/changelog.json` for stable and
`releases/download/nightly/changelog.json` for nightly. The app keeps the last
copy on disk, because the notes are worth reading offline.

## Cutting a release

### The notes are already written

There is nothing to write at release time. The notes for a version are the
notes in the commits it contains, and they were written when the changes
were made.

```markdown
## 0.9.0

**Something a user would notice.** What changed, and why they would care.
```

The release reads the notes out of the commits it contains and uses them as
the release notes. **A release with a change a user can see but no note for
it fails** rather than shipping the commit log to users — so this cannot be
forgotten at tag time.

The generated list of technical changes is still attached, collapsed under a
"Technical changes" heading.

### Then cut the tag

Push a tag:

```bash
git tag -a v0.8.0 -m "Mediyo v0.8.0" && git push origin v0.8.0
```

or dispatch the workflow with a version, which cuts the tag for you:

```bash
gh workflow run publish.yml -f version=0.8.0
```

Either way the tag push is what publishes. The run:

1. computes the version code with `scripts/version-code.sh`
2. builds and signs the APK with the release keystore
3. verifies the signature, package name, version code and label
4. writes `update.json` with the download URL and SHA-256
5. collects the notes for the range, and refuses to publish if a change a
   user can see has no note for it
6. publishes the GitHub Release with the APK, a `.sha256`, `update.json` and
   `changelog.json`

The updater's contract is `scripts/make-update-json.sh`, shared by both
tracks, so the two cannot drift.

## Publishing a nightly

By hand, when there is a reason to:

```bash
gh workflow run publish.yml
```

There is deliberately **no schedule**. A nightly that lands on its own is a
nightly nobody chose to ship, and it burns a release slot for no reason.

Each publish increments the nightly's sequence within its minor line, so
successive nightlies are 8501, 8502, 8503… and each one outranks the last.
The sequence is capped at 499 by `scripts/version-code.sh`, which fails with
"bump the minor version" rather than colliding with the next release's band —
so a line of nightlies is 500 builds long before you have to think about it.

Two nightlies published in a row do not cancel each other: the workflow's
concurrency group has `cancel-in-progress: false`, so the second one queues
behind the first.

The nightly run marks the commit with a `nightly-<major>.<minor>.<seq>` git tag
for archaeology, and republishes the rolling `nightly` release with that
build's assets. The asset name carries the sequence, so the download URL
changes every time and cannot be served stale from a CDN cache.

A nightly carries notes too: the changes since the last nightly on the same
line, or since the release that line is built on. A nightly is a delta, not
a summary — a user who has been on the channel for a week has already seen
the rest.

## Nightlies and the release keystore

A nightly is signed with the **same key** as releases and carries the **same
applicationId**, so it replaces the release build in place. A different key
would force an uninstall and cost the user their library and settings; a
different applicationId would make it a separate app altogether.

The consequence is that a nightly carries your release signature, so only opt-in
users should run one.

## If a nightly breaks something

Nothing to do — stable users are unaffected. The nightly channel is opt-in,
and the next nightly replaces it. A nightly is a build of `main`, so it can
carry a rough edge that a release never would; that is what the opt-in is
for, and it is why the app asks before switching the channel on. To stop the
bleeding immediately, delete the rolling release:

```bash
gh release delete nightly -y
```

The updater then gets a 404 for the nightly feed and reports the failure
rather than offering a broken build. Publish a new nightly and it resumes.

## Verifying a release

The `Android` workflow runs on every push and pull request and has three
required checks: `APK` (build + lint), `Boot emulator + drive app` and
`Release notes`. The second one installs the debug APK on an API 34 emulator,
drives it through the adb control surface and uploads 31 screenshots — see
[adb-control.md](adb-control.md).

It is a **separate job in the same workflow**, not a separate workflow, so it
takes the APK straight from the artifact the build just produced: no second
build, and no downloading across workflow runs.

`Release notes` is the third job: it checks the pull request description on
a pull request, and the commits themselves on a push to main. It runs in
parallel with the build and takes seconds.

Neither runs against a *release* build, so R8 and resource shrinking are not
exercised before shipping. The release keystore and `minifyEnabled` are only
turned on in the release build type, and `app/proguard-rules.pro` keeps the app,
the Go core and NewPipe wholesale, so the risk is low — but it is not zero, and
it is not covered.

## Where things live

| | |
| --- | --- |
| `.github/workflows/publish.yml` | releases and nightlies |
| `.github/workflows/android.yml` | build + lint + emulator tour + release notes (all required checks) |
| `scripts/version-code.sh` | the version code formula |
| `scripts/make-update-json.sh` | the updater contract |
| `scripts/check-notes.sh` | the release-notes check |
| `scripts/check-range-notes.sh` | the same check, over a range of commits |
| `scripts/merge-pr.sh` | merges a pull request with its description as the commit body |
| `scripts/collect-notes.sh` | assembles the notes for a range of commits |
| `scripts/make-changelog.sh` | builds `changelog.json` from the tags |
| `scripts/notes-lib.sh` | the notes format, shared by the three above |
| `scripts/emulator-smoke.sh` | the tour script |
| `app/src/main/.../data/update/Changelog.kt` | the history the app reads |
| `app/src/main/.../feature/update/WhatsNew.kt` | the "what's new" screen |
| `app/src/debug/` | the adb control surface (debug builds only) |
