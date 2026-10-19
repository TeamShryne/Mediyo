# Driving Mediyo over adb

Debug builds expose a broadcast receiver so an agent (or a human) can search,
play and navigate with nothing but `adb`, and screenshot with stock `screencap`.

Release builds have none of this: the receiver is declared only in
`app/src/debug/AndroidManifest.xml`, so it is not part of a release APK.

## Setup

Debug builds install side-by-side with release, so the package is
`com.teamshryne.mediyo.debug`.

```bash
adb shell am start -n com.teamshryne.mediyo.debug/com.teamshryne.mediyo.MainActivity
```

Every command replies as the broadcast result — `result=0` accepted,
`result=1` rejected — and always logs to logcat under the `MediyoAdb` tag:

```bash
adb logcat -s MediyoAdb
```

## Commands

All four take the same action, `com.teamshryne.mediyo.adb`, and select on
`--es cmd`.

### Quoting multi-word values

`adb shell` hands the command line to a *device-side* shell, which re-splits
it. So the obvious form silently truncates:

```bash
--es query "daft punk one more time"   # arrives as query="daft"  ✗
--es query "'daft punk one more time'" # arrives intact        ✓
```

The inner single quotes survive one round of re-splitting. Every example below
that has spaces already uses this form.

### go — open any screen

```bash
adb shell am broadcast -p com.teamshryne.mediyo.debug -a com.teamshryne.mediyo.adb \
    --es cmd go --es route liked
```

`--es route` accepts a bare destination or a full route. When the destination
has an id, pass it with `--es arg` and it is joined for you, so
`--es route artist --es arg UCXkARuJI4MIDAfuBqh0gA` is the same as
`--es route artist/UCXkARuJI4MIDAfuBqh0gA`.

Destinations:

| Route | Screen |
| --- | --- |
| `home`, `search`, `library`, `settings` | tabs |
| `artist/{id}`, `album/{id}`, `playlist/{id}` | browse pages |
| `podcast/{id}`, `episode/{id}`, `channel/{id}` | podcasts |
| `localPlaylist/{id}` | a saved local playlist |
| `section/{kind}/{id}?params=…&title=…` | "see all" sub-lists |
| `liked`, `history`, `downloads`, `profile` | library pages |
| `settings/lyrics`, `settings/updates`, `settings/appearance`, `settings/appearance/tabs`, `settings/appearance/player`, `settings/equalizer`, `settings/storage` | settings |

An unknown route is reported rather than thrown:

```
error: no route 'nope': Navigation destination that matches request …
```

### search — run a query and show the results

```bash
adb shell am broadcast -p com.teamshryne.mediyo.debug -a com.teamshryne.mediyo.adb \
    --es cmd search --es query "'daft punk one more time'"
```

Switches to the search tab and submits the query. Results are visible on screen.

### play — start playback

By query — searches, queues every song it finds (so `next` has somewhere to go)
and starts the best match:

```bash
adb shell am broadcast -p com.teamshryne.mediyo.debug -a com.teamshryne.mediyo.adb \
    --es cmd play --es query "'daft punk one more time'"
```

By videoId — plays that id exactly:

```bash
adb shell am broadcast -p com.teamshryne.mediyo.debug -a com.teamshryne.mediyo.adb \
    --es cmd play --es videoId dQw4w9WgXcQ
```

The core has no id → metadata call, so without `--es title` the row is labelled
with the videoId:

```bash
adb shell am broadcast -p com.teamshryne.mediyo.debug -a com.teamshryne.mediyo.adb \
    --es cmd play --es videoId dQw4w9WgXcQ --es title "Never Gonna Give You Up"
```

### sheet — open a bottom sheet

Bottom sheets are invisible until something opens them, which makes them easy
to regress unnoticed. This opens each one so it can be screenshotted. Requires
something in the queue (a `play` or `search` first).

```bash
adb shell am broadcast -p com.teamshryne.mediyo.debug -a com.teamshryne.mediyo.adb \
    --es cmd sheet --es target menu
adb exec-out screencap -p > sheet.png
```

`--es target` is one of `menu` (track overflow menu), `comments`, `playlist`
(add-to-playlist) or `sleep` (sleep timer).

### dump — log what is playing

```bash
adb shell am broadcast -p com.teamshryne.mediyo.debug -a com.teamshryne.mediyo.adb --es cmd dump
adb logcat -s MediyoAdb
```

```
MediyoAdb: dump title=One More Time artist=Daft Punk playing=true queue=24 index=0 shuffle=false liked=false origin=Search
```

## Screenshots

Nothing to implement — this is stock Android:

```bash
adb exec-out screencap -p > shot.png
```

Playback transport is likewise already standard, since the app publishes a
real `MediaSession`:

```bash
adb shell media dispatch pause
adb shell media dispatch next
adb shell dumpsys media_session
```

These need the playback service alive, i.e. something already playing.

## Notes

- Commands buffer while the activity is not composed, so one sent before the
  app is up still lands. Anything older than 15 seconds is dropped rather than
  replayed, so a stale navigation cannot fire while you are looking at
  something else.
- **Playback will not start from a GitHub-hosted runner.** YouTube blocks
  datacenter IPs for anonymous streaming (`LOGIN_REQUIRED` / "Sign in to
  confirm that you're not a bot"), which surfaces in logcat as
  `SignInConfirmNotBotException`. Metadata, search, browsing and navigation all
  work normally — only audio start is affected. Verifying real playback needs a
  self-hosted runner on a residential IP.
- The receiver is `exported`, so any app on the device can reach it. That is
  accepted for a debug-only build since release has no receiver to find;
  nothing destructive is exposed. Do not move it to `src/main`.
- `-p com.teamshryne.mediyo.debug` in the examples pins the broadcast to the
  debug package. Drop it to target whichever build is installed.