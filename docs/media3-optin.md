# Media3 `@UnstableApi` opt-in rules (media3 1.2.1, Kotlin 1.9.22)

Burned ~6 CI rounds learning this. Follow these exactly and you get it
right the first time.

## The one rule

For media3's `UnstableApi`, **always** do this and nothing else:

```kotlin
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
fun myFunc() { /* media3 unstable APIs here */ }
```

Single marker, `androidx.annotation.OptIn`, positional. Every file using
this form passes both `compileDebugKotlin` and `lintDebug`. Verified
examples: `DownloadUtil`, `ExoDownloadService`, `StreamDataSource`,
`DownloadVm`, `AppModule` cache providers.

## Why the obvious alternatives fail

Verified empirically (Oct 2026), not from docs:

| What you write | Compiler | Lint |
|---|---|---|
| `androidx.OptIn` + **two** positional markers | ❌ "can only be used as an annotation" | — |
| `kotlin.OptIn` (no import) + two markers | ✅ | ❌ flags the annotation line |
| `kotlin.OptIn` + single `UnstableApi` marker | ✅ | ❌ flags the annotation line |
| `@UnstableApi` propagated onto the function | ✅ | ✅ inside… |

Root causes, verified via `javap` on
`annotation-experimental-1.4.1-api.jar`:

- `androidx.annotation.OptIn` declares **one** attribute,
  `markerClass()` (an array). Two positional args can never bind to it.
- This repo's lint (`UnsafeOptInUsageError`) only honors the **androidx**
  form for androidx markers. Bare `kotlin.OptIn(UnstableApi)` compiles
  but is flagged.
- `kotlin.OptIn` is **not `@Repeatable`** on Kotlin 1.9, so you cannot
  stack two single-marker `@OptIn`s either.

## Consequences

- **Never put two markers in one `@OptIn`, and never stack two
  `@OptIn`s.** If a function needs two markers (e.g. Material3
  experimental + `UnstableApi`), restructure so it needs one — see
  "Keep media3 out of the UI" below.
- **Never propagate `@UnstableApi` onto a screen/composable.** It
  infects every caller: we did it on `DownloadsScreen` and lint
  immediately demanded opt-in at the NavHost call site in
  `MainActivity`. `@OptIn` does not propagate — prefer it.
- **Keep media3 types out of the UI layer.** `DownloadsScreen` used to
  take a `Download?`; now `DownloadVm.uiStates` exposes
  `Map<String, DownloadUiState>` (the same sealed UI model the track-menu
  rows already used) and the screen has zero media3 imports. This deleted
  the whole class of problem instead of annotating around it.

## Other 1.2.1 API facts established the hard way

- `AudioSink` lives in `exoplayer.audio`, not `common.audio`.
- `BaseAudioProcessor.onFlush()` takes **no** args (the `StreamMetadata`
  overload is newer media3).
- Back-buffer goes on `DefaultLoadControl.Builder().setBackBuffer(...)`,
  **not** `ExoPlayer.Builder` (no `setBackBuffer` on 1.2.1).
- `DownloadService` 5-arg ctor `(id, intervalMs, channelId, nameRes,
  descRes)` exists; `getForegroundNotification` takes `List<Download>`
  (not `MutableList`).
- `PlatformScheduler` requires `RECEIVE_BOOT_COMPLETED` or lint fails
  (`MissingPermission`).
- `setCustomCacheKey` on `MediaItem.Builder`/`DownloadRequest.Builder`
  is `UnstableApi` — the two call sites (`PlayerViewModel.loadCurrent`,
  `WidgetPlaybackController.loadTrack`) carry the opt-in above.
- Material3 lambda `progress = { ... }` overloads exist on this BOM;
  the `Float` overloads are deprecated (warnings, not errors).
