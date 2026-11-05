# Fase 2 — Riproduzione Core, Stream Resolver, MediaLibraryService, Ricerca Brani e Cronologia

## Cosa è stato implementato
1. **`StreamResolver` (`ChainedStreamResolver`)**:
   - Catena a 3 livelli con fallback automatico su `Dispatchers.IO`:
     1. `NewPipeExtractor` (primario in modalità ospite, filtra esclusivamente `audioStreams`, preferendo Opus `itag 251`/`250`, fallback AAC `itag 140`).
     2. `InnerTubeClient` (`WEB_REMIX` con cookie + `SAPISIDHASH` quando autenticato, `ANDROID_VR` / `IOS` per estrazione stream audio).
     3. Istanze `Piped` / `Invidious` configurabili in `SettingsDataStore` con rotazione round-robin e contatore di salute.
   - Cache a due livelli (memoria + Room `stream_url_cache`) con scadenza basata sul parametro `expire` (~5 ore).
   - Pre-risoluzione automatica della traccia successiva in coda (`preResolve`).
2. **`GMusicMediaLibraryService` (Media3)**:
   - `ResolvingDataSource` con URI custom `gmusic://track/{videoId}` risolti pigramente al momento della riproduzione.
   - `SimpleCache` LRU da 512 MB (`gmusic_audio_cache`) tramite `CacheDataSource`.
   - Gestione resiliente degli errori: al primo errore su un brano invalida la cache, ri-risolve l'URL e riprova una volta; se fallisce nuovamente, salta al brano successivo ed emette un evento verso la UI (`Snackbar` in italiano).
   - Gestione focus audio e pausa automatica allo scollegamento delle cuffie (`setHandleAudioBecomingNoisy(true)`).
3. **`PlayerViewModel`, `MiniPlayerBar` e `FullScreenPlayerSheet`**:
   - Tutte le chiamate `MediaController` avvengono sul Main thread.
   - `MiniPlayerBar` ancorata sopra la barra di navigazione con gesti swipe orizzontali (avanti/indietro) e swipe verso il basso (stop e chiusura).
   - `FullScreenPlayerSheet` con copertina HD (`=w800-h800-l90-rj` / `maxresdefault`), seekbar, play/pausa/avanti/indietro, shuffle, repeat e like.
4. **Ricerca brani e Cronologia locale**:
   - `SearchScreen` con ricerca live (debounce 350ms), cronologia ricerche recenti in Room e avvio immediato della riproduzione.
   - `HomeScreen` con griglia 2x3 "Riproduzioni recenti" popolata dalla cronologia locale (`play_history`), nascosta se vuota.
