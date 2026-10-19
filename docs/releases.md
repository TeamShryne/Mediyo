# Releases and nightlies

Mediyo has two update tracks. Both are published as GitHub Releases and both
are read by the in-app updater, which fetches an `update.json` and compares
`versionCode` against the installed build.

| | Stable | Nightly |
| --- | --- | --- |
| What | a tagged release (`v1.2.3`) | a daily build of `main` |
| Guarantee | supported | none — it can be broken at any time |
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

## Cutting a release

### Write the notes first

Stable release notes come from `CHANGELOG.md`. Each version gets a section:

```markdown
## 0.9.0

**Something a user would notice.** What changed, and why they would care.
```

The release reads that section and uses it as the release notes. **A release
without a section fails** rather than shipping the commit log to users — so
this cannot be forgotten at tag time.

Write them for someone using the app, not for someone reading the git log: no
commit hashes, no file names, no "refactored". The generated list of technical
changes is still attached, collapsed under a "Technical changes" heading.

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
5. publishes the GitHub Release with the APK, a `.sha256` and `update.json`

The updater's contract is `scripts/make-update-json.sh`, shared by both
tracks, so the two cannot drift.

## Publishing a nightly

By hand:

```bash
gh workflow run publish.yml
```

Or let the cron do it — daily at 03:17 UTC, deliberately off the hour because
GitHub's scheduler is oversubscribed on the hour.

The nightly run marks the commit with a `nightly-<major>.<minor>.<seq>` git tag
for archaeology, and republishes the rolling `nightly` release with that
build's assets. The asset name carries the sequence, so the download URL
changes every night and cannot be served stale from a CDN cache.

## Nightlies and the release keystore

A nightly is signed with the **same key** as releases and carries the **same
applicationId**, so it replaces the release build in place. A different key
would force an uninstall and cost the user their library and settings; a
different applicationId would make it a separate app altogether.

The consequence is that a nightly carries your release signature, so only opt-in
users should run one.

## If a nightly breaks something

Nothing to do — stable users are unaffected. The nightly channel is opt-in,
and the next nightly replaces it. To stop the bleeding immediately, delete the
rolling release:

```bash
gh release delete nightly -y
```

The updater then gets a 404 for the nightly feed and reports the failure
rather than offering a broken build. Publish a new nightly and it resumes.

## Verifying a release

The `Android` workflow runs on every push and pull request and has two required
checks: `APK` (build + lint) and `Boot emulator + drive app`. The second one
installs the debug APK on an API 34 emulator, drives it through the adb control
surface and uploads 31 screenshots — see [adb-control.md](adb-control.md).

It is a **separate job in the same workflow**, not a separate workflow, so it
takes the APK straight from the artifact the build just produced: no second
build, and no downloading across workflow runs.

Neither runs against a *release* build, so R8 and resource shrinking are not
exercised before shipping. The release keystore and `minifyEnabled` are only
turned on in the release build type, and `app/proguard-rules.pro` keeps the app,
the Go core and NewPipe wholesale, so the risk is low — but it is not zero, and
it is not covered.

## Where things live

| | |
| --- | --- |
| `.github/workflows/publish.yml` | releases and nightlies |
| `.github/workflows/android.yml` | build + lint + emulator tour (both required checks) |
| `scripts/version-code.sh` | the version code formula |
| `scripts/make-update-json.sh` | the updater contract |
| `scripts/emulator-smoke.sh` | the tour script |
| `app/src/debug/` | the adb control surface (debug builds only) |
