# Mediyo

**Mediyo is an experimental Android music player for exploring and listening to YouTube Music.**

Built with Kotlin and Jetpack Compose, Mediyo focuses on a native Android experience, playback controls, and flexible local caching.

> **Status:** In development. Features and setup may change.

[![Android CI](https://github.com/TeamShryne/Mediyo/actions/workflows/android.yml/badge.svg)](https://github.com/TeamShryne/Mediyo/actions/workflows/android.yml)
[![Core library](https://img.shields.io/badge/core-mediyo--core-blue)](https://github.com/TeamShryne/mediyo-core)

## Features

- Browse and search music metadata.
- Play audio with Media3 ExoPlayer.
- Use Android media controls and background playback.
- Cache search, browse, media, lyrics, comments, and library data locally.
- Manage cache size, expiration, and network preferences in **Settings → Storage & Cache**.
- Manage downloads, choose quality and storage location, and configure automatic cleanup.
- Use the app without creating a Mediyo account.

Mediyo is a work in progress, so not every feature or workflow should be considered stable.

## Technology

- **Language and UI:** Kotlin, Jetpack Compose, Material 3
- **Architecture:** MVVM, Hilt, Coroutines, Flow
- **Local storage:** Room, DataStore, Coil
- **Playback:** AndroidX Media3 / ExoPlayer, MediaSession, foreground service
- **Native core:** Rust via mediyo-core / mediyo-ffi
- **Audio stream extraction:** NewPipe Extractor

## Get the app

There is currently no documented public release or local installation package. The GitHub Actions workflow builds a debug APK and publishes it as a workflow artifact when its configured trigger runs.

1. Open [GitHub Actions](https://github.com/TeamShryne/Mediyo/actions/workflows/android.yml).
2. Select a successful workflow run.
3. Download the APK from its **Artifacts** section, if an artifact is available.

Debug builds are intended for testing and may not be suitable for everyday use.

## Build from source

The project currently relies on its GitHub Actions workflow for builds. The workflow builds the Rust native library for Android and then runs the Android build and lint tasks.

To build locally, you will need a compatible Android development environment, the Rust toolchain, and the Android NDK. The exact local setup is not yet documented.

For the native core, see [TeamShryne/mediyo-core](https://github.com/TeamShryne/mediyo-core).

## Storage and cache

Open **Settings → Storage & Cache** to manage local data. Available controls include:

- Cache size limit
- Cache expiration
- Wi-Fi-only and offline-related preferences
- Per-category cache clearing or clearing all cached data
- Wi-Fi prefetching
- Download location, audio quality, and automatic cleanup

Options may change as development continues.

## Project structure

```text
Mediyo/
├── app/                     # Android application
├── rust/                    # Native Rust core and FFI
├── gradle/                  # Gradle wrapper configuration
└── .github/workflows/       # CI build workflow
```

## Privacy and account

Mediyo supports anonymous use and does not require a Mediyo account. Depending on the current build and configuration, it may use YouTube Music session data for access to certain features. Review the code and your local settings before using any account-linked functionality.

## Disclaimer

Mediyo is an independent, experimental project and is not affiliated with, endorsed by, or sponsored by YouTube or Google. YouTube and YouTube Music are trademarks of their respective owners.

Use the app in accordance with applicable laws and the relevant service terms. Availability and playback behavior may change when upstream services change.

## Contributing

Issues and pull requests are welcome. Before opening a pull request, describe the change clearly and include steps to reproduce or test it where relevant.

## License

No license is specified in this repository yet. Unless a license is added, you should not assume that the source code is available for reuse, modification, or redistribution.
