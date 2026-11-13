# NuvioTV-MT

[🇺🇸 English description (README in English)](README.md)

---

**A [NuvioTV](https://github.com/NuvioMedia/NuvioTV) forkja a [MediaTrail](https://github.com/aicon/MediaTrail) lehető legszorosabb együttműködésére — „Most nézem”, könyvtári jelölés, watchlist szinkron —, plusz néhány kisebb lejátszó- és UX-javítás.**

[![Verzió](https://img.shields.io/badge/verzió-legfrissebb-blue.svg)](https://github.com/aicon/NuvioTV-MT/releases)
[![Licenc: GPL v3](https://img.shields.io/badge/Licenc-GPL_v3-green.svg)](LICENSE)
[![MediaTrail kiegészítő](https://img.shields.io/badge/kiegészítő-MediaTrail-orange.svg)](https://github.com/aicon/MediaTrail)
[![Upstream](https://img.shields.io/badge/upstream-NuvioTV-purple.svg)](https://github.com/NuvioMedia/NuvioTV)
[![Támogatás Ko-fi-n](https://img.shields.io/badge/Ko--fi-Támogatás-ff5e5b?logo=ko-fi)](https://ko-fi.com/aiconwg)

---

## Áttekintés

Ez a fork azért készült, hogy az Android TV kliens a lehető legszorosabban működjön együtt a **[MediaTrail](https://github.com/aicon/MediaTrail)**-lel: élő „Most nézem”, opcionális könyvtári megjelölés-szinkron, és MediaTrail-listák a főképernyő polcain. Megtartja az upstream streamelést és katalógusokat, a hivatalos NuvioTV (`com.nuvio.tv`) mellé települ, és a [NuvioMobile-MT](https://github.com/aicon/NuvioMobile-MT)-vel ugyanazt a MediaTrail-történetet adja a telefonon. Kisebb pluszok: Auto ExoPlayer/libmpv választás és saját frissítő ehhez a tárolóhoz.

---

## ✨ Főbb funkciók és eltérések

### 1. Párhuzamos telepíthetőség
- **Csomagnév:** `com.aicon.nuviotv`.
- Teljesen függetlenül telepíthető a hivatalos NuvioTV (`com.nuvio.tv`) mellé, nem írja felül azt.
- **Indítóikon neve:** **Nuvio YT** (alapértelmezett), **Nuvio** (magyar)

### 2. MediaTrail lejátszás-szinkronizáció
- Lejátszás közben valós időben továbbítja a lejátszó eseményeit (`start`, `pause`, `progress`, `stop`) a `player` erőforrást hirdető addonoknak.
- A **[MediaTrail](https://github.com/aicon/MediaTrail)** addon címét (`…/nuvio/manifest.json`) add hozzá a Beállítások → Bővítmények alatt. Külön scrobbler konténer nem kell.
- Minden telepítés stabil `deviceId`-t és automatikus nevet küld (pl. `TV · Shield`), így több tévé és a telefon a kezdőlapon külön „Most nézem” sávot kap (átnevezhető).
- Seek után is megy `progress`. A befejezett nézés a MediaTrailben **90%-nál** mentődik.
- Aktív lejátszás alatt 30 másodpercenként küld állapotjelzést (szüneteltetés alatt nem terhel).
- A videó lejátszása és dekódolása helyben történik a tévén; a MediaTrail csak az állapotinformációkat kapja meg.
- A funkció a **Lejátszási beállításokban** tetszés szerint ki- és bekapcsolható (alapértelmezetten aktív).

### 3. Intelligens lejátszómotor-választás (Auto)
Az alapértelmezett „Auto” motor intelligensen választja ki a legmegfelelőbb dekódolót:

| Tartalom / Formátum | Választott lejátszómotor |
| :--- | :---: |
| **Normál filmek és sorozatok** | **ExoPlayer** |
| **Anime** *(MAL, Kitsu, AniList azonosító, anime műfaj alapján)* | **libmpv** |
| **Hagyományos kodekek** *(AVI formátum, XviD kódolás)* | **libmpv** |
| **HDR és Dolby Vision** *(Elsőbbséget élvez még AVI név esetén is)* | **ExoPlayer** |

*(A címeknél manuálisan megadott egyedi motorbeállítás mindig felülbírálja az automatikus választást).*

### 4. Saját alkalmazáson belüli frissítő
Beépített frissítőmodul, amely közvetlenül az `aicon/NuvioTV-MT` GitHub kiadásait figyeli:
- A legutóbbi stabil kiadást használja (`releases/latest` a `main` ágról).
- Automatikusan a tévé processzortípusához (ARM64, ARMv7 stb.) illeszkedő APK csomagot tölti le.
- Az egyszerű frissítéshez azonos csomagnév és azonos aláírókulcs szükséges.

---

## 📥 Letöltés és Telepítés

1. Nyisd meg a **[legfrissebb kiadást](https://github.com/aicon/NuvioTV-MT/releases/latest)**, és töltsd le a tévéhez illő APK-t.

   <a href="https://github.com/aicon/NuvioTV-MT/releases/latest"><img src="badges/get-it-on-github.png" alt="Get it on GitHub" height="80"></a>
2. Töltsd le a készülékednek megfelelő APK fájlt (Android TV esetén tipikusan `arm64-v8a` vagy `armeabi-v7a`).
3. Telepítsd fel az APK-t az Android TV-re a megszokott módszerrel (ADB, Downloader alkalmazás, Send Files to TV, vagy pendrive segítségével).

---

## 🛠️ Fordítás forráskódból

A NuvioTV-MT Kotlin, Jetpack Compose, TV Material 3 és Media3 alapokra épül. A fordításhoz Android Studio, JDK és Android SDK szükséges:

```bash
git clone https://github.com/aicon/NuvioTV-MT.git
cd NuvioTV-MT
./gradlew :app:assembleFullDebug
```

---

## 🌿 Ágkezelés és Upstream frissítések

- **`main`:** az egyetlen ág, amin ez a termékfork dolgozik. Az aláírt stabil kiadások innen jelennek meg.
- Az upstream címkék a `main`-be olvadnak be (nem reset), így a fork csomagneve, aláírása és a MediaTrail funkciók megmaradnak.

---

## ☕ Támogatás

Amennyiben hasznosnak találod a NuvioTV-MT-t és szeretnéd támogatni a fejlesztést és karbantartást:

[![Ko-fi támogatás](https://img.shields.io/badge/Ko--fi-Támogatás-ff5e5b?style=for-the-badge&logo=ko-fi)](https://ko-fi.com/aiconwg)

---

## 📜 Licenc és Szerzői Jogok

A projekt a **[GNU General Public License v3.0 (GPL-3.0)](LICENSE)** feltételei szerint használható és módosítható.  
Az eredeti alkalmazás szerzői joga a [NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV) csapatát illeti.