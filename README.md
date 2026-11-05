<div align="center">

# GMusic

### *The Modern YouTube Music & Android Auto Client*

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android_8.0%2B_(API_26%2B)-green.svg?style=flat-square&logo=android)](https://www.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-4285F4.svg?style=flat-square&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Playback-AndroidX_Media3_1.5.1-red.svg?style=flat-square)](https://developer.android.com/media/media3)
[![Android Auto](https://img.shields.io/badge/Integration-Android_Auto_Native-00bcd4.svg?style=flat-square&logo=androidauto)](https://www.android.com/auto)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=flat-square)](LICENSE)

[🇮🇹 Italiano](#-italiano) • [🇬🇧 English](#-english)

</div>

---

<a name="-italiano"></a>
## 🇮🇹 Italiano

**GMusic** è un client open-source leggero, moderno e veloce per **YouTube Music**, sviluppato in Kotlin nativo e Jetpack Compose (Material 3). Offre un'esperienza utente raffinata in stile Spotify, **pieno supporto nativo ad Android Auto** e una **sincronizzazione reale a doppia via** con il tuo account YouTube Music.

### ✨ Caratteristiche Principali

* **🔄 Sincronizzazione Reale a Doppia Via (YouTube Music $\leftrightarrow$ GMusic)**
  * **Registrazione ufficiale degli ascolti**: Gli ascolti effettuati nell'app vengono registrati all'istante nella cronologia ufficiale del tuo account Google tramite protocollo InnerTube ufficiale (Playback & Watch-Time tracking HTTP 204).
  * **Cronologia account bidirezionale**: Visualizza e aggiorna la cronologia in tempo reale con pull-to-refresh (`FEmusic_history`).
  * **Libreria completa**: Sincronizzazione di Brani preferiti (Like/Unlike bidirezionale), Playlist personali, Album salvati e Artisti seguiti.
* **🚗 Pieno Supporto Nativo ad Android Auto**
  * Sviluppato su standard **AndroidX Media3 `MediaLibraryService`**.
  * Struttura a 3 tab dedicate per il cruscotto: **Home**, **Cerca**, **La tua libreria**.
  * Griglia rapida 3x2 (Preferiti + mix dinamico tra playlist, artisti e recenti).
  * Carosello *"Riproduzioni recenti"* con gli ultimi 5 brani per una guida sicura.
* **🎨 Interfaccia Moderna Material 3 (Spotify-Style)**
  * Palette cromatica dinamica estratta automaticamente dalla copertina del brano in riproduzione.
  * Home feed intelligente: 6 mix personalizzati da YouTube Music, scaffale *Riproduzioni recenti*, consigliati per te e nuove uscite.
  * Navigazione fluida con mini-player a comparsa ed expandable full-screen player.
* **🎤 Testi Sincronizzati (LRC)**
  * Visualizzazione testi in tempo reale con scorrimento automatico stile karaoke.
  * Supporto a testi sincronizzati e non sincronizzati con fallback e romanizzazione fonetica (Romaji per brani giapponesi).
* **📴 Ascolto Offline & Cache Intelligente**
  * Download brani con storage locale e persistenza metadati su database Room.
  * Modalità offline automatica in assenza di connessione di rete.
* **🎚️ Audio di Qualità Superiore & Equalizzatore**
  * Equalizzatore a 5 bande con preset dedicati (Rock, Pop, Jazz, Electronic, Vocal, ecc.).
  * Dissolvenza incrociata configurabile (**Crossfade** con curve logaritmiche e lineari).
  * Audio Offload per il minimo consumo di batteria in background.

---

### 🛠️ Architettura e Stack Tecnologico

GMusic adotta una rigorosa **Clean Architecture** (Presentation, Domain, Data) orientata al reactive programming:

* **Linguaggio**: [Kotlin 2.0.21](https://kotlinlang.org/)
* **Interfaccia Utente**: [Jetpack Compose](https://developer.android.com/jetpack/compose) con [Material 3](https://m3.material.io/)
* **Media & Audio Engine**: [AndroidX Media3 1.5.1](https://developer.android.com/media/media3) (ExoPlayer + MediaLibraryService)
* **Asincronia**: Kotlin Coroutines & `StateFlow` reattivi
* **Database Locale**: [Room Database 2.6.1](https://developer.android.com/training/data-storage/room) con KSP
* **Preferences & Sessione**: AndroidX DataStore (Preferences)
* **Dependency Injection**: [Dagger Hilt 2.52](https://dagger.dev/hilt/)
* **Networking**: [OkHttp 4.12.0](https://square.github.io/okhttp/) & Kotlinx Serialization

---

### 🚀 Compilazione ed Installazione

#### Prerequisiti
* **Java Development Kit (JDK)**: Versione 17 o superiore.
* **Android SDK**: Build tools 34.0.0+, Android API 26 (minSdk) fino a 35 (targetSdk).

#### Compilazione da Terminale
```bash
# Clona il repository
git clone https://github.com/<username>/GMusic.git
cd GMusic

# Su Linux / macOS:
./gradlew assembleDebug

# Su Windows (PowerShell):
.\gradlew.bat assembleDebug
```

L'APK compilato sarà disponibile in:
`app/build/outputs/apk/debug/app-debug.apk`

#### Installazione rapida via ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

<a name="-english"></a>
## 🇬🇧 English

**GMusic** is a lightweight, modern, and high-performance open-source **YouTube Music** client built natively with Kotlin and Jetpack Compose (Material 3). It delivers a polished Spotify-like experience, **native Android Auto support**, and a **genuine two-way synchronization** with your official YouTube Music account.

### ✨ Key Features

* **🔄 Genuine Two-Way Sync (YouTube Music $\leftrightarrow$ GMusic)**
  * **Official Playback Tracking**: Listened songs in GMusic are immediately reported to your Google account's official history using standard InnerTube endpoints (HTTP 204 Playback & Watch-Time beacons).
  * **Live History Sync**: Browse and refresh your YouTube Music listening history seamlessly (`FEmusic_history`).
  * **Library Synchronization**: Two-way Likes, personal Playlists, Subscribed Artists, and Liked Albums.
* **🚗 Native Android Auto Support**
  * Built on **AndroidX Media3 `MediaLibraryService`**.
  * 3 dashboard-optimized tabs: **Home**, **Search**, **Your Library**.
  * 3x2 Quick Access Grid (Liked Songs + random mix of playlists, artists, and recent tracks).
  * Safe-driving recent plays shelf (last 5 items on in-car display).
* **🎨 Modern Material 3 UI (Spotify-Style)**
  * Adaptive ambient background tint extracted dynamically from the playing album cover.
  * Smart home shelves: 6 custom YouTube Music mixes, recent plays carousel, recommended tracks, and Italian/global trends.
  * Expandable full-screen player with gestures and seamless mini-player transition.
* **🎤 Synchronized Lyrics (LRC)**
  * Real-time karaoke-style auto-scrolling lyrics.
  * Support for synchronized and plain lyrics with phonetic romanization (Romaji for Japanese titles).
* **📴 Offline Caching & Downloads**
  * One-tap track downloading stored locally via Room database.
  * Resilient offline mode: playback continues gracefully without network connection.
* **🎚️ Advanced Audio Controls & Equalizer**
  * 5-band equalizer with presets (Rock, Pop, Jazz, Electronic, Vocal, Bass Boost, etc.).
  * Configurable **Crossfade** with linear and logarithmic volume curves.
  * Audio Offloading support for low-power background playback.

---

### 🛠️ Architecture & Tech Stack

GMusic follows **Clean Architecture** patterns with unidirectional data flow (UDF):

* **Language**: [Kotlin 2.0.21](https://kotlinlang.org/)
* **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/)
* **Audio Engine**: [AndroidX Media3 1.5.1](https://developer.android.com/media/media3) (ExoPlayer + MediaLibraryService)
* **Concurrency**: Kotlin Coroutines & `StateFlow`
* **Local Persistence**: [Room 2.6.1](https://developer.android.com/training/data-storage/room) with KSP
* **Preferences**: AndroidX DataStore
* **Dependency Injection**: [Dagger Hilt 2.52](https://dagger.dev/hilt/)
* **Networking**: [OkHttp 4.12.0](https://square.github.io/okhttp/) & Kotlinx Serialization

---

### 🚀 Building from Source

#### Prerequisites
* **Java Development Kit (JDK)**: JDK 17 or higher.
* **Android SDK**: Build tools 34.0.0+, minSdk 26, targetSdk 35.

#### Build Commands
```bash
# Clone the repository
git clone https://github.com/<username>/GMusic.git
cd GMusic

# On Linux / macOS:
./gradlew assembleDebug

# On Windows (PowerShell):
.\gradlew.bat assembleDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

#### Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚖️ Disclaimer / Avvertenza Legale

* **Italiano**: GMusic è un progetto open-source sviluppato unicamente a scopo educativo e d'uso personale. Questo software non è affiliato, associato, autorizzato, sponsorizzato o in alcun modo ufficialmente collegato a Google LLC o YouTube. "YouTube" e "YouTube Music" sono marchi registrati di Google LLC.
* **English**: GMusic is an open-source project developed strictly for educational and personal use. This project is not affiliated, associated, authorized, endorsed by, or in any way officially connected with Google LLC or YouTube. "YouTube" and "YouTube Music" are registered trademarks of Google LLC.

---

## 📄 License / Licenza

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for more details.
