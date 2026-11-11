# NuvioTV-MT

[🇭🇺 Magyar nyelvű leírás (README in Hungarian)](README_HU.md)

---

**An enhanced, independent Android TV client fork of [NuvioTV](https://github.com/NuvioMedia/NuvioTV) featuring native MediaTrail scrobbling, intelligent player engine switching, standalone coexistence, and dedicated in-app updates.**

[![Release](https://img.shields.io/badge/release-latest-blue.svg)](https://github.com/aicon/NuvioTV-MT/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-green.svg)](LICENSE)
[![Companion of](https://img.shields.io/badge/companion-MediaTrail-orange.svg)](https://github.com/aicon/MediaTrail)
[![Upstream](https://img.shields.io/badge/upstream-NuvioTV-purple.svg)](https://github.com/NuvioMedia/NuvioTV)
[![Support on Ko-fi](https://img.shields.io/badge/Ko--fi-Support-ff5e5b?logo=ko-fi)](https://ko-fi.com/aiconwg)

<a href="https://github.com/aicon/NuvioTV-MT/releases/latest"><img src="badges/get-it-on-github.png" alt="Get it on GitHub" height="80"></a>

---

## Overview

NuvioTV-MT is an independent community build of NuvioTV tailored specifically for Android TV. It retains all upstream streaming, catalog, and playback capabilities while introducing deep integration with the **[MediaTrail](https://github.com/aicon/MediaTrail)** ecosystem, a smarter playback engine selector, and the ability to run side-by-side with the official Nuvio app.

---

## ✨ Key Additions & Differences

### 1. Side-by-Side Coexistence
- **Package Identifier:** `com.aicon.nuviotv` (debug: `com.aicon.nuviotv.debug`).
- Installs completely independently alongside the official NuvioTV app (`com.nuvio.tv`) without overwriting it.
- **Launcher Names:** Displayed as **Nuvio YT** in English system locales and **Nuvio** in Hungarian locales.

### 2. MediaTrail Playback Events
- Dispatches playback events (`start`, `pause`, `progress`, `stop`) to installed addons that declare the `player` resource.
- Works seamlessly with **[MediaTrail-Scrobbler](https://github.com/aicon/MediaTrail-Scrobbler)** to drive the real-time **Now Watching** dashboard banner and record watch history once the threshold is reached.
- Sends progress pings every 30 seconds during active playback (paused playback stops pings).
- Video rendering and decoding remain local on your TV; only playback event metadata is dispatched.
- Enabled by default under **Playback Settings** (can be toggled at any time).

### 3. Smart Playback Engine Switching
The default "Auto" engine mode intelligently picks the optimal decoder per media format:

| Media / Format Condition | Selected Player Engine |
| :--- | :---: |
| **Standard Movies & TV Shows** | **ExoPlayer** |
| **Anime** *(Detected via MAL, Kitsu, AniList IDs, or Anime genres)* | **libmpv** |
| **Legacy Codecs** *(AVI container, XviD encoding)* | **libmpv** |
| **HDR & Dolby Vision** *(Prioritized even if filename contains AVI)* | **ExoPlayer** |

*(Manual engine selection per title always overrides automatic routing).*

### 4. Custom In-App Updater
Features an integrated updater that tracks releases from `aicon/NuvioTV-MT`:
- **Stable Channel:** Updates from the `main` branch (stable releases without pre-release tags).
- **Beta Channel:** Updates from the `dev` branch (evaluates pre-releases and newer builds).
- Automatically selects the matching CPU architecture APK (falling back to universal APK if single-file).
- Requires matching package name and signing keys for seamless in-place updates.

---

## 📥 Download & Installation

1. Open the **[latest release](https://github.com/aicon/NuvioTV-MT/releases/latest)** and download the APK for the TV.
2. Download the APK corresponding to your Android TV's architecture (typically `arm64-v8a` or `armeabi-v7a`).
3. Sideload the APK onto your Android TV box or Google TV device using your preferred tool (ADB, Downloader, Send Files to TV, or USB drive).

---

## 🛠️ Building from Source

NuvioTV-MT is built with Kotlin, Jetpack Compose, TV Material 3, and Media3. Building requires Android Studio, a JDK, and the Android SDK.

```bash
git clone https://github.com/aicon/NuvioTV-MT.git
cd NuvioTV-MT
./gradlew :app:assembleFullDebug
```

---

## 🌿 Branching Model & Upstream Sync

- **`main`:** Tracks upstream NuvioTV stable release tags.
- **`dev`:** Tracks upstream NuvioTV beta release tags.
- Upstream tags are cleanly merged into respective branches rather than reset, permanently preserving NuvioTV-MT custom features and branding.

---

## ☕ Support

If you enjoy NuvioTV-MT and want to support its maintenance and ongoing development:

[![Buy Me a Coffee](https://img.shields.io/badge/Ko--fi-Buy%20me%20a%20coffee-ff5e5b?style=for-the-badge&logo=ko-fi)](https://ko-fi.com/aiconwg)

---

## 📜 License & Acknowledgments

This software is licensed under the **[GNU General Public License v3.0 (GPL-3.0)](LICENSE)**.  
All upstream rights and authorship belong to [NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV).