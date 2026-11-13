# NuvioTV-MT

[🇭🇺 Magyar nyelvű leírás (README in Hungarian)](README_HU.md)

---

**A [NuvioTV](https://github.com/NuvioMedia/NuvioTV) fork built for the deepest possible cooperation with [MediaTrail](https://github.com/aicon/MediaTrail) — Now Watching, library marks, watchlist sync — plus a few smaller player and UX fixes.**

[![Release](https://img.shields.io/badge/release-latest-blue.svg)](https://github.com/aicon/NuvioTV-MT/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-green.svg)](LICENSE)
[![Companion of](https://img.shields.io/badge/companion-MediaTrail-orange.svg)](https://github.com/aicon/MediaTrail)
[![Upstream](https://img.shields.io/badge/upstream-NuvioTV-purple.svg)](https://github.com/NuvioMedia/NuvioTV)
[![Support on Ko-fi](https://img.shields.io/badge/Ko--fi-Support-ff5e5b?logo=ko-fi)](https://ko-fi.com/aiconwg)

---

## Overview

This fork exists so the Android TV client works as tightly as possible with **[MediaTrail](https://github.com/aicon/MediaTrail)**: live Now Watching, optional library mark sync, and home catalog shelves from MediaTrail lists. It keeps upstream streaming and catalogs, installs beside official NuvioTV (`com.nuvio.tv`), and pairs with [NuvioMobile-MT](https://github.com/aicon/NuvioMobile-MT) for the same MediaTrail story on the phone. Smaller extras include Auto ExoPlayer/libmpv routing and an in-app updater for this repository.

---

## ✨ Key Additions & Differences

### 1. Side-by-Side Coexistence
- **Package Identifier:** `com.aicon.nuviotv`.
- Installs completely independently alongside the official NuvioTV app (`com.nuvio.tv`) without overwriting it.
- **Launcher name:** **Nuvio YT** (default), **Nuvio** (Hungarian)

### 2. MediaTrail Playback Events
- Dispatches playback events (`start`, `pause`, `progress`, `stop`) to installed addons that declare the `player` resource.
- Install the **[MediaTrail](https://github.com/aicon/MediaTrail)** addon address (`…/nuvio/manifest.json`) under Settings → Addons. No separate scrobbler container.
- Every install sends a stable `deviceId` plus an automatic label such as `TV · Shield`, so multiple TVs and the phone each get their own Now Watching strip (renameable on the MediaTrail home page).
- Seek also emits `progress`. Finished watches are saved in MediaTrail at **90%**.
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
- Uses the latest stable release (`releases/latest` on `main`).
- Automatically selects the matching CPU architecture APK (falling back to universal APK if single-file).
- Requires matching package name and signing keys for seamless in-place updates.

---

## 📥 Download & Installation

1. Open the **[latest release](https://github.com/aicon/NuvioTV-MT/releases/latest)** and download the APK for the TV.

   <a href="https://github.com/aicon/NuvioTV-MT/releases/latest"><img src="badges/get-it-on-github.png" alt="Get it on GitHub" height="80"></a>
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

- **`main`:** the only branch used for this product fork. Signed stable releases are published from here.
- Upstream tags are merged into `main` (not reset), so fork package id, signing, and MediaTrail features stay.

---

## ☕ Support

If you enjoy NuvioTV-MT and want to support its maintenance and ongoing development:

[![Buy Me a Coffee](https://img.shields.io/badge/Ko--fi-Buy%20me%20a%20coffee-ff5e5b?style=for-the-badge&logo=ko-fi)](https://ko-fi.com/aiconwg)

---

## 📜 License & Acknowledgments

This software is licensed under the **[GNU General Public License v3.0 (GPL-3.0)](LICENSE)**.  
All upstream rights and authorship belong to [NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV).