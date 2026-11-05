# Fase 1 — Skeleton e Architettura di Base

## Cosa è stato implementato
1. Pulizia completa della directory di progetto e rigenerazione da zero con Gradle 8.10.2 e Version Catalog (`gradle/libs.versions.toml`).
2. Configurazione Kotlin + KSP (senza `kapt`), Hilt, Compose BOM + Material 3, Media3, Room, DataStore, WorkManager, Glance, Coil 3, Palette, OkHttp, Kotlinx Serialization e NewPipeExtractor.
3. Creazione del sistema di design (`core/designsystem`) con supporto Material You e animazione fluida dei colori (`animateColorAsState`), tipografia e forme Spotify-style.
4. Creazione delle schermate di base e della navigazione (`ui/root/GMusicRoot.kt`) con barra inferiore a 4 tab (`Home`, `Cerca`, `Esplora`, `Libreria`) + accesso a `Impostazioni`, rispettando `WindowInsets` ed edge-to-edge in orientamento verticale (`portrait`).
5. Tutte le stringhe UI centralizzate in `res/values/strings.xml` esclusivamente in italiano.
6. Generazione dell'icona adattiva del launcher con livello monocromatico (`ic_launcher.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`).
