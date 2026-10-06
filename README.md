<p align="center"><b>Mediyo</b> — Industry-grade YouTube Music client<br/>Native Kotlin • Compose M3 • mediyo-core (Go) • NewPipe stream only</p>

<p align="center">
<a href="https://github.com/TeamShryne/Mediyo/actions/workflows/android.yml"><img src="https://github.com/TeamShryne/Mediyo/actions/workflows/android.yml/badge.svg"/></a>
<a href="https://github.com/TeamShryne/mediyo-core"><img src="https://img.shields.io/badge/core-mediyo--core-E91E63"/></a>
</p>

## Stack
- **UI:** Compose M3, Navigation Compose, Material 3 dynamic color, edge-to-edge, shimmer, Paging 3
- **Arch:** Single-activity, MVVM + Clean, Hilt, Coroutines Flow, Room + DataStore
- **Data:** `mediyo-core` (Go, gomobile `app/libs/mediyo.aar`, anonymous visitor identity) for catalog/search/browse metadata, NewPipeExtractor only for `audioStreams` URL → Media3 ExoPlayer + MediaSession + foreground service
- **Cache:** Per-type Room `kv_cache` (search/browse/media/lyrics/comments/library) + Coil disk + `CachePrefs` (maxBytes/TTL/wifiOnly/offline) — full control in Settings > Storage & Cache

## Build
- `mediyo-core` ships as a vendored `app/libs/mediyo.aar` (built in [TeamShryne/mediyo-core](https://github.com/TeamShryne/mediyo-core) via `make aar`); the app consumes it through `MediyoBridge` (single process `Session`, plain Kotlin models in `data/mediyo/`). Rebuild + copy the AAR whenever the core changes.
- Push to `main` → `android.yml` → `assembleDebug` + `lint` + APK artifact

## Auth
Anonymous only: first launch bootstraps a `visitorData` (persisted in DataStore, shown in Profile); `MediyoBridge.rotateVisitorData()` recovers poisoned sessions. No login, no cookies.

## Storage & Cache
`Settings > Storage & Cache`: total bar, per-type size/count, max slider (128MB–2GB), TTL 24h/7d/30d, Wi-Fi only, Offline only, per-type Clear, Clear all, prefetch on Wi-Fi, downloads manager (internal/SD, quality, auto-delete 30d).

## Project
```
Mediyo/
  app/  (Compose app + data/mediyo bridge/models + libs/mediyo.aar)
  .github/workflows/android.yml  (builds the Go AAR, then the APK)
```





