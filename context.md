# GMusic — Context & Architecture Guide per AI e Sviluppatori

> Questo documento fornisce una panoramica tecnica esaustiva, chiara e strutturata dell'architettura, delle convenzioni e dell'ambiente di sviluppo di **GMusic**, consentendo a qualsiasi agente AI o nuovo sviluppatore di comprendere immediatamente il progetto e contribuire efficacemente al codice.

---

## 1. Identità e Obiettivo del Progetto

- **Nome**: GMusic
- **Natura**: Client Android nativo per lo streaming musicale da **YouTube Music** (**solo audio**, senza video).
- **Esperienza Utente**: Interfaccia raffinata ispirata a Spotify, basata su **Material 3 (Material You)**, con palette dinamiche estratte dalla copertina in tempo reale, micro-interazioni fluide e navigazione edge-to-edge.
- **Lingua**: **100% Italiano** per tutte le stringhe utente, etichette, descrizioni e messaggi di errore (centralizzate in `res/values/strings.xml`).
- **Modalità d'uso**:
  - **Ospite (Default)**: Funzionamento completamente autonomo e offline-first senza account Google (tramite NewPipeExtractor, InnerTube guest, e istanze Piped/Invidious).
  - **Autenticato (Opzionale)**: Login tramite WebView, memorizzazione cookie protetta con Android Keystore (`EncryptedSharedPreferences`), sincronizzazione automatica di Mi Piace e playlist con YouTube Music via WorkManager in background.

---

## 2. Stack Tecnologico e Ambiente di Build

| Componente | Versione / Dettaglio | Note |
|---|---|---|
| **Piattaforma** | Android (Min SDK 26 / Android 8.0, Target & Compile SDK 35 / Android 15) | Single Activity, orientamento bloccato in `portrait` |
| **Linguaggio** | Kotlin 2.0.21 | Target JVM 17 |
| **Build Tool** | Gradle 8.10.2 + AGP 8.7.2 | Version Catalog centralizzato in `gradle/libs.versions.toml` |
| **Annotation Processing** | **KSP 2.0.21-1.0.28** | Nessun uso di `kapt` nel progetto |
| **UI Framework** | Jetpack Compose BOM 2024.10.01 + Material 3 | Edge-to-edge con `WindowInsets`, animazioni `SharedTransitionLayout` |
| **Dependency Injection** | Dagger Hilt 2.52 | `@HiltAndroidApp`, `@AndroidEntryPoint`, moduli in `di/` |
| **Audio Engine** | AndroidX Media3 1.5.0 (`ExoPlayer`, `MediaSession`, `MediaLibraryService`) | Caching LRU 512 MB, `ResolvingDataSource` con schema custom `gmusic://` |
| **Database Locale** | Room 2.6.1 + Coroutines/Flow | Tabelle: traccie, cronologia, playlist, cache stream, testi LRCLIB |
| **Preferenze** | Jetpack DataStore Preferences 1.1.1 | Impostazioni audio, equalizzatore, tema, istanze fallback |
| **Sicurezza** | AndroidX Security Crypto 1.1.0-alpha06 | `EncryptedSharedPreferences` con Keystore (`AES256_SIV` / `AES256_GCM`) |
| **Background Sync** | WorkManager 2.10.0 + Hilt Work 1.2.0 | `YtMusicSyncWorker` per sincronizzazione modifiche offline verso YTM |
| **Widget Home** | Jetpack Glance 1.1.1 (Material 3) | Formati 4x1 e 4x2 |
| **Immagini** | Coil 3.0.3 + AndroidX Palette 1.0.0 | Caricamento asincrono, upgrade HD delle miniature, palette WCAG AA |
| **JDK di Sviluppo** | Eclipse Adoptium Temurin OpenJDK 17 | Installato in `D:\Java\jdk-17` |
| **Android SDK** | SDK 35 (Android 15) + Build-Tools 35.0.0 / 34.0.0 | Installato in `D:\Android\Sdk` |

---

## 3. Struttura dei File e Pacchetti (`com.gmusic.app`)

```
app/src/main/
├── AndroidManifest.xml          # Permessi, dichiarazione Activity, Service Media3 e Android Auto
└── java/com/gmusic/app/
    ├── GMusicApp.kt             # Application class (@HiltAndroidApp)
    ├── MainActivity.kt          # Single Activity con Compose setContent, intent media search e edge-to-edge
    ├── core/
    │   └── designsystem/        # GMusicTheme, Palette dinamica con contrasto WCAG AA, Typography, Color, Shape
    ├── data/
    │   ├── auth/                # AuthCredentialStore (Keystore cifrato), YtMusicSyncWorker (WorkManager)
    │   ├── download/            # GMusicDownloadService, OfflineDownloadController (Media3 download in foreground)
    │   ├── innertube/           # InnerTubeClient (endpoint WEB_REMIX con SAPISIDHASH, ANDROID_VR, IOS)
    │   ├── local/               # GMusicDatabase (Room), Dao, Entità (StreamUrlCache, PlayHistory, Playlists, Lyrics), SettingsDataStore
    │   ├── lyrics/              # LyricsRepository (LRCLIB client REST + cache Room per testi karaoke sincronizzati)
    │   ├── repository/          # MusicRepository (punto unico di aggregazione dati per i ViewModel)
    │   └── stream/              # StreamResolver, ChainedStreamResolver, NewPipeOkHttpDownloader
    ├── di/
    │   └── AppModule.kt         # Binding Hilt per OkHttpClient, Room, DataStore, StreamResolver, ecc.
    ├── domain/
    │   └── model/
    │       └── Models.kt        # Modelli di dominio: Track, AlbumSummary, ArtistSummary, PlaylistSummary, HomeSection, ecc.
    ├── playback/
    │   ├── crossfade/           # CrossfadeController (rampe lineari volume 0-12s gestite sul Main thread)
    │   ├── equalizer/           # GMusicEqualizerManager (5 bande: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz + BassBoost + Virtualizer)
    │   ├── queue/               # TrackMediaItemMapper (conversione bidirezionale Track <-> MediaItem Media3)
    │   └── service/             # GMusicMediaLibraryService (ExoPlayer + MediaLibrarySession + albero Android Auto)
    ├── ui/
    │   ├── album/               # AlbumScreen (dettaglio album con lista tracce e riproduzione)
    │   ├── artist/              # ArtistScreen (dettaglio artista con top brani, album, singoli e radio)
    │   ├── auth/                # WebLoginScreen (WebView login Google/YouTube Music)
    │   ├── components/          # Componenti condivisi (TrackRow, MiniPlayerBar, Header, Dialog)
    │   ├── explore/             # ExploreScreen & ExploreViewModel (Classifiche IT, Novità, Generi/Mood con gl=IT)
    │   ├── home/                # HomeScreen & HomeViewModel (Griglia recenti, caroselli radio personalizzati)
    │   ├── library/             # LibraryScreen & LibraryViewModel (Preferiti, playlist locali con mosaico 2x2 e drag-and-drop)
    │   ├── player/              # PlayerViewModel, FullScreenPlayerSheet, SyncedLyricsSheet, QueueBottomSheet
    │   ├── root/                # GMusicRoot (Scaffold principale, NavigationBar 4 tab + player container)
    │   ├── search/              # SearchScreen & SearchViewModel (Ricerca con chip, debounce 350ms, cronologia)
    │   └── settings/            # SettingsScreen, SettingsViewModel, SettingsModalBottomSheet
    └── widget/
        └── GMusicGlanceWidget.kt # Widget Glance 4x1 e 4x2 in stile Spotify per schermata Home
```

---

## 4. Architettura dei Sottosistemi Principali

### A. Catena di Risoluzione Stream (`ChainedStreamResolver`)
Quando ExoPlayer richiede la riproduzione di un brano con schema `gmusic://track/{videoId}`:
1. **Verifica Cache**:
   - Cache L1 in memoria (`ConcurrentHashMap`) + Cache L2 in Room (`stream_url_cache`).
   - Verifica automatica del parametro di scadenza `expire` dell'URL Google (~5 ore).
2. **Fallback a 3 livelli** (eseguito rigorosamente su `Dispatchers.IO`):
   1. `InnerTubeClient` con client `ANDROID_VR` (1.62.27) / `IOS` (20.03.02) o `WEB_REMIX` autenticato con `SAPISIDHASH`.
   2. `NewPipeExtractor v0.26.5` (modalità ospite, estrae e isola unicamente tracce audio, privilegiando Opus `itag 251`/`250` o AAC `itag 140`).
   3. Istanze esterne `Piped` e `Invidious` configurabili dall'utente con round-robin e circuit breaker su errori.
3. **Validazione preventiva HTTP**:
   - Viene inviata una richiesta rapida Range (`bytes=0-1023`) per accertarsi che il server non risponda con `HTTP 403 Forbidden` prima di passare l'URL a ExoPlayer.
4. **Pre-risoluzione**:
   - La traccia immediatamente successiva in coda viene risolta preventivamente in background per garantire transizioni gapless e avvio istantaneo.

### B. Gestione Riproduzione Audio (`GMusicMediaLibraryService` & Media3)
- Eredita da `MediaLibraryService` di Media3 per offrire supporto unificato a:
  - App in foreground/background con notifica multimediale persistente.
  - **Android Auto** (tramite albero sfogliabile `NODE_HOME`, `NODE_HISTORY`, `NODE_PLAYLISTS`, `NODE_LIKED`).
  - **Comandi Vocali** ("*Ehi Google, riproduci X su GMusic*") via `MEDIA_PLAY_FROM_SEARCH`.
  - **Widget Glance** della Home screen Android.
- **Cache su Disco Condivisa**: `SimpleCache` da 512 MB LRU (`gmusic_audio_cache`). I download offline popolano la medesima cache; se un brano è scaricato, la riproduzione è locale al 100% senza richiesta di rete.
- **Autoplay Infinito & Riproduzione Artista**: 
  - Quando la coda scende a `<= 3` brani rimanenti, il servizio recupera in background nuovi brani sul Main thread.
  - **Modalità Artista**: se la riproduzione è stata avviata per un Artista (da `ArtistScreen`, Libreria o Android Auto), l'Autoplay invoca `fetchArtistAutoplayAddition` ed estrae **esclusivamente brani dello stesso artista** (attingendo alla discografia completa e riciclandola rimescolata se esaurita), garantendo che l'app **non cambi mai artista**.
  - In modalità standard, recupera tracce correlate dalla radio YouTube Music (`fetchAutoplayRadioAddition`).
- **Android Auto**:
  - Toccando un artista nell'albero multimediale di Android Auto, parte istantaneamente l'intera discografia dell'artista in riproduzione casuale (`shuffleModeEnabled = true`).

### C. UI & Player Experience
- **Single Activity Edge-to-Edge**: `MainActivity` imposta trasparenza totale su barre di stato e navigazione.
- **Transizione MiniPlayer -> FullScreen**:
  - `MiniPlayerBar` ancorata sopra la barra a 4 tab con controlli rapidi e swipe gesture orizzontali (traccia precedente/successiva) e verso il basso (stop).
  - Espansione in `FullScreenPlayerSheet` con animazione fluida container-transform / `SharedTransitionLayout`.
- **Palette Adattiva**:
  - `Palette` estrae i colori dominanti dalla copertina album, con correzione HSL per soddisfare sempre i requisiti di contrasto e leggibilità WCAG AA.
  - I colori si animano armoniosamente tra i cambi traccia tramite `animateColorAsState`.
- **Testi Karaoke**:
  - Query a LRCLIB (`get` o `search`) con cache Room. Visualizzazione riga per riga sincronizzata con scorrimento automatico e seeking al tocco.

---

## 5. Regole e Convenzioni di Codice per l'AI

1. **Lingua delle Stringhe**:
   - **TUTTE le stringhe visibili all'utente DEVONO risiedere in `res/values/strings.xml` in italiano**.
   - Nessuna stringa UI hardcoded nei file Kotlin Compose.
2. **Threading e Coroutine**:
   - Tutte le interazioni con `MediaController` o ExoPlayer devono avvenire sul **Main thread** (`Dispatchers.Main`).
   - Tutte le operazioni di rete, parsing InnerTube, NewPipeExtractor e accessi a Room devono avvenire su **`Dispatchers.IO`**.
3. **Nessun Kapt**:
   - Usare sempre **KSP** per annotazioni Room, Hilt, ecc.
4. **Sicurezza e Credenziali**:
   - Non salvare mai cookie, token o credenziali in chiaro in SharedPreferences o logcat. Usare sempre `AuthCredentialStore` (`EncryptedSharedPreferences`).
5. **Orientamento**:
   - L'applicazione per smartphone è rigorosamente orientata in verticale (`portrait`).

---

## 6. Comandi Utili per l'Ambiente Locale (Windows PowerShell)

Le variabili e i percorsi configurati sul computer di sviluppo sono:
- **JDK 17**: `D:\Java\jdk-17` (`$env:JAVA_HOME = "D:\Java\jdk-17"`)
- **Android SDK**: `D:\Android\Sdk` (`$env:ANDROID_HOME = "D:\Android\Sdk"`, configurato in `local.properties`)
- **ADB**: `D:\Android\Sdk\platform-tools\adb.exe`

```powershell
# 1. Compilazione dell'APK di debug
.\gradlew.bat assembleDebug

# 2. Esecuzione dei controlli di lint e formattazione
.\gradlew.bat lintDebug

# 3. Installazione su dispositivo Android / Emulatore collegato
& "D:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk

# 4. Monitoraggio log di riproduzione audio e risoluzione stream
& "D:\Android\Sdk\platform-tools\adb.exe" logcat -s GMusicAudio:V ExoPlayer:V InnerTube:D ChainedStreamResolver:D AndroidRuntime:E

# 5. Test Android Auto tramite Desktop Head Unit (DHU)
& "D:\Android\Sdk\platform-tools\adb.exe" forward tcp:5277 tcp:5277
& "D:\Android\Sdk\extras\google\auto\desktop-head-unit.exe"
```
