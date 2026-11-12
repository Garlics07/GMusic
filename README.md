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

* **🔄 Orientamento Dinamico & Layout Tablet/Landscape (v2.9.2)**
  * **Rotazione libera sensoriale**: orientamento dinamico e fluido sia in Portrait che in Landscape per smartphone e tablet.
  * **Barra di navigazione ancorata al lato fisico inferiore del device**: in orizzontale la barra resta ancorata al bordo del connettore/altoparlanti (destra o sinistra in base a come viene ruotato il dispositivo), trasformandosi in un dock verticale ergonomico per il pollice, garantendo che fotocamere e notch non siano mai d'intralcio.
  * **Player a 2 colonne in Landscape**: colonna sinistra con copertina, slider ondoso e controlli di riproduzione; colonna destra con selettore rapido a schede (*tab switcher*) per alternare tra **Testi sincronizzati (Karaoke LRC)** e **Coda di riproduzione**.
  * **Home Screen adattiva**: griglia di accesso rapido che passa automaticamente da **2x3** in verticale a **3x2** in orizzontale, con caroselli e card distribuiti armoniosamente.
* **🛡️ Privacy Avanzata & Zero Inquinamento Cronologia YouTube (v3.0.4)**
  * **Ricerche 100% anonime**: ricerche, suggerimenti di digitazione ed esplorazione catalogo avvengono senza cookie dell'account Google, lasciando intatta e pulita la cronologia ricerche su YouTube e Google.
  * **Controllo sincronizzazione ascolti**: nuovo selettore dedicato nelle Impostazioni per scegliere se registrare i brani ascoltati nella cronologia visualizzazioni di YouTube (disattivato per impostazione predefinita per la massima privacy).
  * **Protezione ID & Android Auto**: validazione rigorosa dei codici video (11 caratteri) e gestione diretta dei nodi di sistema che previene chiamate errate.
* **🔄 Sincronizzazione Reale a Doppia Via (YouTube Music $\leftrightarrow$ GMusic)**
  * **Registrazione ufficiale degli ascolti**: Gli ascolti effettuati nell'app vengono registrati all'istante nella cronologia ufficiale del tuo account Google tramite protocollo InnerTube ufficiale (Playback & Watch-Time tracking HTTP 204).
  * **Cronologia account bidirezionale**: Visualizza e aggiorna la cronologia in tempo reale con pull-to-refresh (`FEmusic_history`).
  * **Libreria completa**: Sincronizzazione di Brani preferiti (Like/Unlike bidirezionale), Playlist personali, Album salvati e Artisti seguiti.
* **🚗 Pieno Supporto Nativo ad Android Auto**
  * Sviluppato su standard **AndroidX Media3 `MediaLibraryService`**.
  * Struttura a 3 tab dedicate per il cruscotto: **Home**, **Cerca**, **La tua libreria**.
  * Griglia rapida 3x2 (Preferiti + mix dinamico tra playlist, artisti e recenti).
  * Carosello *"Riproduzioni recenti"* con gli ultimi 5 brani per una guida sicura.
* **🎨 Interfaccia 100% Material Design 3 (Material You)**
  * Palette cromatica dinamica e adattiva estratta dalla copertina del brano in riproduzione.
  * WavySlider sinusoidale interattivo nel player a schermo intero in stile Google Pixel.
  * Floating bottom bar solida con ritorno immediato alla radice.
  * Libreria organizzata in 5 card tematiche (Preferiti, Playlist, Artisti, Album, Scaricati) con ricerca locale in tempo reale.
  * Pre-caching intelligente a 3 tracce per riproduzione senza interruzioni (*gapless playback*).
* **🎤 Testi Sincronizzati (LRC)**
  * Visualizzazione testi in tempo reale con scorrimento automatico stile karaoke.
  * Supporto a testi sincronizzati e non sincronizzati con fallback e romanizzazione fonetica (Romaji per brani giapponesi).
* **📴 Ascolto Offline, Cache & Esperienza Download Avanzata**
  * Download brani con storage locale e persistenza metadati su database Room.
  * **Feedback interattivo sul player**: animazione a rimbalzo (*spring bounce*) e anello di avanzamento circolare in tempo reale (`CircularProgressIndicator`).
  * **Sezione Canzoni scaricate reattiva**: comparsa istantanea dei brani in corso di scaricamento con barra lineare (`LinearProgressIndicator`) e percentuale in tempo reale.
  * **Tasto Scarica nel menu a 3 puntini**: opzione *"Scarica brano"* / *"Elimina download"* presente ovunque (Libreria, Ricerca per tendenze e risultati, Album, Artisti e Playlist).
  * **Long-press in Home**: pressione prolungata sulle singole canzoni per aprire il menu contestuale rapido.
  * Modalità offline automatica in assenza di connessione di rete.
* **🔗 Apertura Link YouTube Music & Condivisione (Deep Linking)**
  * **Intercettazione link**: apertura diretta di link `music.youtube.com`, `youtu.be` e `youtube.com`.
  * **Riproduzione istantanea**: avvio automatico dello streaming ed espansione a schermo intero per i brani condivisi.
  * **Supporto a playlist e album condivisi**: caricamento immediato della coda d'ascolto.
  * **Integrazione Share Sheet (`ACTION_SEND`)**: invio diretto di link da WhatsApp, Telegram, YouTube Music o browser verso GMusic.
* **🌐 Supporto Multilingua Nativo (IT, EN, FR, ES)**
  * Supporto nativo per **Italiano**, **Inglese**, **Francese** e **Spagnolo**.
  * Integrazione ufficiale **Per-App Language Android 13+** e rilevamento automatico della lingua di sistema del dispositivo.
  * Backend InnerTube localizzato dinamicamente con parametri `hl` e `gl` coerenti con la lingua selezionata per feed, mix e classifiche.
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

### 📦 Download ed Installazione (Release Ufficiali)

I file di release APK pronti all'installazione su smartphone e tablet Android sono disponibili direttamente nella sezione **[Releases](../../releases)** del repository (oltre che nella cartella principale del progetto, es. `GMusic 3.0.2.apk`).

> [!NOTE]
> **Tutte le build di rilascio vengono compilate, ottimizzate (con R8/ProGuard) e firmate manualmente dallo sviluppatore** (non tramite GitHub Actions o server CI esterni). Questo assicura che ogni aggiornamento sia testato, mantenga la stessa chiave crittografica e permetta l'**aggiornamento diretto (*in-place update*)** senza perdere playlist, preferiti o brani scaricati.

#### 🚀 Compilazione da Sorgente (per sviluppatori)

##### Prerequisiti
* **Java Development Kit (JDK)**: Versione 17 o superiore.
* **Android SDK**: Build tools 34.0.0+, Android API 26 (minSdk) fino a 35 (targetSdk).

##### Compilazione da Terminale
```bash
# Clona il repository
git clone https://github.com/<username>/GMusic.git
cd GMusic

# Su Linux / macOS:
./gradlew assembleRelease

# Su Windows (PowerShell):
.\gradlew.bat assembleRelease
```

L'APK compilato viene generato in:
`app/build/outputs/apk/release/app-release.apk`

##### Installazione rapida via ADB:
```bash
adb install -r "GMusic 3.0.3.apk"
```

---

<a name="-english"></a>
## 🇬🇧 English

**GMusic** is a lightweight, modern, and high-performance open-source **YouTube Music** client built natively with Kotlin and Jetpack Compose (Material 3). It delivers a polished Spotify-like experience, **native Android Auto support**, and a **genuine two-way synchronization** with your official YouTube Music account.

### ✨ Key Features

* **🔄 Dynamic Orientation & Tablet/Landscape Layout (v2.9.2)**
  * **Fluid sensor-based rotation**: seamless switching between Portrait and Landscape on phones and tablets.
  * **Navigation bar docked to physical bottom edge**: in landscape, the navigation bar anchors along the device's physical bottom (where charging port/speakers are located, right or left depending on rotation), morphing into an ergonomic vertical pill dock that keeps front cameras and notches unobstructed.
  * **Two-pane Full Player in Landscape**: left pane hosts artwork, track metadata, wavy slider, and playback controls; right pane includes an interactive tab switcher to toggle between **Synchronized Lyrics (Karaoke LRC)** and the **Playback Queue**.
  * **Adaptive Home Screen**: top quick access grid dynamically shifts from **2x3** in portrait to **3x2** in landscape, preserving elegant card proportions.
* **🛡️ Advanced Privacy & Zero YouTube History Pollution (v3.0.4)**
  * **100% Anonymous Searches**: song, artist, album, and suggestion queries run unauthenticated without account cookies, preserving clean Google & YouTube search histories.
  * **Watch History Sync Toggle**: dedicated privacy setting allowing users to decide whether played tracks appear in their YouTube watch history (disabled by default for maximum privacy).
  * **Strict ID Validation & Android Auto Nodes**: 11-character YouTube video ID regex validation preventing accidental internal navigation query lookups.
* **🔄 Genuine Two-Way Sync (YouTube Music $\leftrightarrow$ GMusic)**
  * **Official Playback Tracking**: Listened songs in GMusic are immediately reported to your Google account's official history using standard InnerTube endpoints (HTTP 204 Playback & Watch-Time beacons).
  * **Live History Sync**: Browse and refresh your YouTube Music listening history seamlessly (`FEmusic_history`).
  * **Library Synchronization**: Two-way Likes, personal Playlists, Subscribed Artists, and Liked Albums.
* **🚗 Native Android Auto Support**
  * Built on **AndroidX Media3 `MediaLibraryService`**.
  * 3 dashboard-optimized tabs: **Home**, **Search**, **Your Library**.
  * 3x2 Quick Access Grid (Liked Songs + random mix of playlists, artists, and recent tracks).
  * Safe-driving recent plays shelf (last 5 items on in-car display).
* **🎨 100% Material Design 3 (Material You) UI**
  * Dynamic adaptive palette extracted in real-time from album artworks.
  * Interactive sinusoidal WavySlider in full-screen player inspired by Google Pixel.
  * Solid floating bottom bar with instantaneous return to root destinations.
  * Library structured in 5 unified cards (Liked, Playlists, Artists, Albums, Downloads) with live search.
  * Intelligent 3-track pre-caching for gapless audio playback.
* **🎤 Synchronized Lyrics (LRC)**
  * Real-time karaoke-style auto-scrolling lyrics.
  * Support for synchronized and plain lyrics with phonetic romanization (Romaji for Japanese titles).
* **📴 Offline Caching, Storage & Advanced Download Experience**
  * One-tap track downloading stored locally via Room database.
  * **Interactive player feedback**: spring bounce animation and live circular progress indicator (`CircularProgressIndicator`) transitioning to `DownloadDone`.
  * **Real-time Downloaded Songs screen**: downloading tracks appear immediately at the top with linear progress bar and percentage.
  * **3-dots menu download action**: *"Download track"* / *"Delete download"* added across Library, Search (trending & results), Albums, Artists, and Playlists.
  * **Long-press on Home**: long press any individual song to reveal quick track options.
  * Resilient offline mode: playback continues gracefully without network connection.
* **🔗 YouTube Music Deep Linking & Direct Sharing**
  * **Link interception**: seamlessly open `music.youtube.com`, `youtu.be`, and `youtube.com` links.
  * **Instant playback**: automatically streams and opens the full-screen player upon receiving a shared song link.
  * **Shared playlists & albums**: loads full queue and begins playback.
  * **Android Share Sheet (`ACTION_SEND`)**: share songs directly from WhatsApp, Telegram, YouTube Music, or browsers directly into GMusic.
* **🌐 Native Multilingual Support (IT, EN, FR, ES)**
  * Full native support for **Italian**, **English**, **French**, and **Spanish**.
  * Official **Android 13+ Per-App Language** integration and automatic device system language detection.
  * Dynamic InnerTube backend parameters (`hl`, `gl`) matching the selected app language for feeds, mixes, and charts.
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

### 📦 Download & Installation (Official Releases)

Ready-to-install release APKs for Android phones and tablets are available under the **[Releases](../../releases)** section of this repository (as well as the project root directory, e.g., `GMusic 3.0.2.apk`).

> [!NOTE]
> **All release builds are compiled, optimized (via R8/ProGuard), and cryptographically signed manually by the developer** (not generated through GitHub Actions or external CI bots). This ensures verified performance, consistent signing keys, and **guaranteed in-place updates** without losing your downloaded songs, playlists, or favorite tracks.

#### 🚀 Building from Source (for developers)

##### Prerequisites
* **Java Development Kit (JDK)**: JDK 17 or higher.
* **Android SDK**: Build tools 34.0.0+, minSdk 26, targetSdk 35.

##### Build Commands
```bash
# Clone the repository
git clone https://github.com/<username>/GMusic.git
cd GMusic

# On Linux / macOS:
./gradlew assembleRelease

# On Windows (PowerShell):
.\gradlew.bat assembleRelease
```

The compiled APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`

##### Quick Install via ADB
```bash
adb install -r "GMusic 3.0.3.apk"
```

---

## ⚖️ Disclaimer / Avvertenza Legale

* **Italiano**: GMusic è un progetto open-source sviluppato unicamente a scopo educativo e d'uso personale. Questo software non è affiliato, associato, autorizzato, sponsorizzato o in alcun modo ufficialmente collegato a Google LLC o YouTube. "YouTube" e "YouTube Music" sono marchi registrati di Google LLC.
* **English**: GMusic is an open-source project developed strictly for educational and personal use. This project is not affiliated, associated, authorized, endorsed by, or in any way officially connected with Google LLC or YouTube. "YouTube" and "YouTube Music" are registered trademarks of Google LLC.

---

## 📄 License / Licenza

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for more details.
