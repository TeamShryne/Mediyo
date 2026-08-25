<p align="center"><b>Mediyo</b> — Industry-grade YouTube Music client<br/>Native Kotlin • Compose M3 • mediyo-core (Rust) • NewPipe stream only</p>

<p align="center">
<a href="https://github.com/TeamShryne/Mediyo/actions/workflows/android.yml"><img src="https://github.com/TeamShryne/Mediyo/actions/workflows/android.yml/badge.svg"/></a>
<a href="https://github.com/TeamShryne/mediyo-core"><img src="https://img.shields.io/badge/core-mediyo--core-E91E63"/></a>
</p>

## Stack
- **UI:** Compose M3, Navigation Compose, Material 3 dynamic color, edge-to-edge, shimmer, Paging 3
- **Arch:** Single-activity, MVVM + Clean, Hilt, Coroutines Flow, Room + DataStore
- **Data:** `mediyo-core`/`mediyo-ffi` via `cargo-ndk` (`libmediyo_ffi.so` in `jniLibs`), NewPipeExtractor only for `audioStreams` URL → Media3 ExoPlayer + MediaSession + foreground service
- **Cache:** Per-type Room `kv_cache` (search/browse/media/lyrics/comments/library) + Coil disk + `CachePrefs` (maxBytes/TTL/wifiOnly/offline) — full control in Settings > Storage & Cache

## Build (CI only — no local builds)
Everything builds on GitHub Actions with heavy caches (Gradle + Rust registry/target). Workflow keeps newest 20 caches + at least one `rust-*` set and auto-deletes others.
- Push to `main` → `android.yml` → `cargo ndk` (arm64/x86_64, platform 24) → `assembleDebug` + `lint` + APK artifact

## Auth
Anonymous or WebView `music.youtube.com` → extract `Cookie`/`SAPISID`/`visitorData`/`pageId` → Encrypted DataStore → `MediyoBridge`

## Storage & Cache
`Settings > Storage & Cache`: total bar, per-type size/count, max slider (128MB–2GB), TTL 24h/7d/30d, Wi-Fi only, Offline only, per-type Clear, Clear all, prefetch on Wi-Fi, downloads manager (internal/SD, quality, auto-delete 30d).

## Project
```
Mediyo/
  rust/  (mediyo-core + mediyo-ffi, built via cargo-ndk)
  app/   (Compose app, jniLibs generated in CI)
  .github/workflows/android.yml
```



