# Mediyo

**Mediyo is an Android music player for exploring and listening to YouTube Music.**

Mediyo provides a native Android experience with music discovery, background playback, and flexible local caching.

> **Status:** In development. Features and setup may change.

[![Android CI](https://github.com/TeamShryne/Mediyo/actions/workflows/android.yml/badge.svg)](https://github.com/TeamShryne/Mediyo/actions/workflows/android.yml)
[![Core library](https://img.shields.io/badge/core-mediyo--core-blue)](https://github.com/TeamShryne/mediyo-core)

## Features

- Browse and search music metadata.
- Play audio with Android media controls and background playback.
- Cache search, browse, media, lyrics, comments, and library data locally.
- Manage cache size, expiration, and network preferences in **Settings → Storage & Cache**.
- Manage downloads, choose quality and storage location, and configure automatic cleanup.
- Use the app without creating a Mediyo account.

Mediyo is a work in progress, so not every feature or workflow should be considered stable.

## Download

Get the latest version from the **[GitHub Releases page](https://github.com/TeamShryne/Mediyo/releases/latest)**. Open the latest release and download the APK from its **Assets** section.

## Build from source

The GitHub Actions workflow builds the Go-based native core for Android and runs the Android build and lint tasks.

To build locally, you will need a compatible Android development environment, the Go toolchain, and the Android NDK. The exact local setup is not yet documented.

For the native core, see [TeamShryne/mediyo-core](https://github.com/TeamShryne/mediyo-core).

## Storage and cache

Open **Settings → Storage & Cache** to manage local data. Available controls include:

- Cache size limit and expiration
- Wi-Fi-only and offline-related preferences
- Clearing individual cache categories or all cached data
- Wi-Fi prefetching
- Download location, audio quality, and automatic cleanup

Options may change as development continues.

## Privacy and account

Mediyo supports anonymous use and does not require a Mediyo account. Depending on the current build and configuration, it may use YouTube Music session data for access to certain features. Review the code and your local settings before using any account-linked functionality.

## Disclaimer

Mediyo is an independent project and is not affiliated with, endorsed by, or sponsored by YouTube or Google. YouTube and YouTube Music are trademarks of their respective owners.

Use the app in accordance with applicable laws and relevant service terms. Availability and playback behavior may change when upstream services change.

## Contributing

Issues and pull requests are welcome. Please describe changes clearly and include steps to reproduce or test them where relevant.

## License

No license is specified in this repository yet. Unless a license is added, do not assume that the source code is available for reuse, modification, or redistribution.
