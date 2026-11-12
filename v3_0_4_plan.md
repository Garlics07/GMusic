# GMusic v3.0.4 - Piano di Risoluzione Privacy & Cronologia YouTube

## 📋 Diagnosi del Problema: Perché questi elementi compaiono su YouTube?

L'utente ha riscontrato nella propria cronologia ricerche e attività di YouTube / YouTube Music elementi anomali come:
* Query cercate manualmente: `misty mountains`, `misty mount`, `misty mountain`, `merluzzo`, `visionqueat`
* ID e nodi interni di navigazione dell'app: `root`, `tab_home`, `tab_library`, `tab_search`, `lib_liked`
* Query tematiche di generi, mood e classifiche: `musica allenamento carica`, `tendenze italia`

---

## 🔍 Root Cause Analysis (Analisi Tecnica Approfondita)

### 1. Invio incondizionato dei Cookie alle API di Ricerca (`InnerTubeClient.kt`)
* **Meccanismo:** Nel metodo `executeWebRemixPost(endpoint, payload)`, se l'utente ha effettuato l'accesso all'account YouTube Music, vengono iniettati incondizionatamente gli header:
  ```http
  Cookie: <raw_cookies>
  Authorization: SAPISIDHASH <hash>
  X-Goog-AuthUser: 0
  ```
* **Conseguenza:** Questo metodo veniva utilizzato indistintamente sia per le funzioni private (libreria utente, brani piaciuti, playlist personali) sia per le chiamate pubbliche:
  * `music/get_search_suggestions` (invocato mentre l'utente digita ogni lettera nella barra di ricerca)
  * `search` (ricerca brani, artisti, album, playlist)
* **Effetto su Google:** Quando i server di YouTube ricevono una richiesta a `/youtubei/v1/search` o `/youtubei/v1/music/get_search_suggestions` firmata con i cookie dell'account, **Google registra automaticamente e istantaneamente la query nella Cronologia Ricerche globale dell'account Google dell'utente**.

---

### 2. Trapasso dei Nodi Browser in Ricerche Reali (`GMusicMediaLibraryService.kt` & `MusicRepository.kt`)
* **Meccanismo:** Quando Android Auto, Google Assistant o il framework di sistema Android MediaSession interrogano il servizio con `onGetItem(mediaId)` passando gli identificatori dei menu (`root`, `tab_home`, `tab_search`, `tab_library`, `lib_liked`):
  1. `onGetItem` non riconosceva questi ID come contenitori/cartelle e creava un `MediaItem.Builder().setMediaId(mediaId).build()`, passandolo a `resolveFullMediaItem(dummy)`.
  2. `resolveFullMediaItem` constatava che non c'erano titolo o artista e invocava `musicRepository.resolveTrackById(item.mediaId)`.
  3. In `MusicRepository.kt`, `resolveTrackById(trackId)` eseguiva questo fallback:
     ```kotlin
     val fromSearch = innerTubeClient.searchSongs(trackId).firstOrNull { it.id == trackId }
     ```
* **Conseguenza disastrosa:** Non essendoci alcun controllo di validità sintattica sull'ID, l'applicazione inviava a YouTube una vera richiesta HTTP di ricerca per le parole `"root"`, `"tab_home"`, `"tab_library"`, `"tab_search"` e `"lib_liked"`. Essendo la chiamata autenticata con i cookie dell'utente, YouTube registrava queste stringhe interne nella cronologia ricerche!

---

### 3. Query automatiche dei Mood e delle Classifiche
* **Meccanismo:** Quando l'utente apre la scheda Cerca o Esplora, vengono caricati i chip dei generi (es. "Musica allenamento carica") e le classifiche ("Tendenze Italia"). Per popolare i brani in caso di fallback o navigazione, `MusicRepository` invocava:
  ```kotlin
  searchSongs(chip.query)
  searchSongs("Tendenze Italia")
  ```
* **Conseguenza:** Anche queste query venivano eseguite con i cookie dell'utente, venendo salvate tra le ricerche recenti dell'account YouTube.

---

### 4. Tracciamento dell'ascolto (`reportPlayback`)
* **Meccanismo:** Durante la riproduzione di ogni brano, `MusicRepository` invoca `innerTubeClient.reportPlayback(videoId)`, che invia i ping ufficiali:
  * `https://s.youtube.com/api/stats/playback`
  * `https://s.youtube.com/api/stats/watchtime`
* **Conseguenza:** Questo aggiorna la cronologia di ascolto e influenza i consigli su YouTube. Per chi desidera la massima riservatezza (modalità incognito / zero tracciamento), questo invio deve poter essere controllato tramite un'apposita impostazione.

---

## 🛡️ Piano di Intervento Architetturale per v3.0.4

Il piano si articola su 4 pilastri fondamentali:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ARCHITETTURA PRIVACY v3.0.4                     │
├────────────────────────────────┬───────────────────────────────────────┤
│ 1. RICERCHE E SUGGERIMENTI     │ 100% ANONIMI (Nessun Cookie / Auth)   │
│    search / get_suggestions   │ → Risultati identici, ZERO cronologia │
├────────────────────────────────┼───────────────────────────────────────┤
│ 2. CATALOGO PUBBLICO           │ 100% ANONIMO (Nessun Cookie / Auth)   │
│    browse (Album, Artisti,     │ → Nessun salvataggio "visita pagina" │
│    Mood, Classifiche, Testi)   │   nell'attività Google                │
├────────────────────────────────┼───────────────────────────────────────┤
│ 3. PROTEZIONE NODI BROWSER     │ VALIDAZIONE REGEX ID 11 CARATTERI     │
│    onGetItem & resolveTrack    │ → Mai più ricerche per "root",        │
│                                │   "tab_home", "lib_liked", ecc.       │
├────────────────────────────────┼───────────────────────────────────────┤
│ 4. CONTROLLI UTENTE            │ NUOVE OPZIONI NELLE IMPOSTAZIONI      │
│    SettingsDataStore           │ • "Anonimizza ricerche YouTube" (ON)  │
│                                │ • "Salva ascolti su YouTube" (Toggle) │
└────────────────────────────────┴───────────────────────────────────────┘
```

---

## 🛠️ Dettaglio Operativo delle Modifiche al Codice

### 1. `InnerTubeClient.kt`
Aggiunta del parametro `requireAuth: Boolean = true` a `executeWebRemixPost`. Tutti gli endpoint di ricerca e catalogo pubblico utilizzeranno `requireAuth = false`.

#### Modifica A: Firma e gestione Auth in `executeWebRemixPost`
```kotlin
// PRIMA:
private fun executeWebRemixPost(endpoint: String, payload: JsonObject): JsonObject? {
    // ...
    if (authCredentialStore.hasValidCookie()) {
        authCredentialStore.getRawCookies()?.let { reqBuilder.header("Cookie", it) }
        authCredentialStore.buildSapisidHashHeader("https://music.youtube.com")?.let {
            reqBuilder.header("Authorization", it)
        }
    }
    // ...
}

// DOPO:
private fun executeWebRemixPost(
    endpoint: String,
    payload: JsonObject,
    requireAuth: Boolean = true
): JsonObject? {
    // ...
    // Invia i cookie e l'autorizzazione SOLO se l'endpoint richiede autenticazione
    if (requireAuth && authCredentialStore.hasValidCookie()) {
        authCredentialStore.getRawCookies()?.let { reqBuilder.header("Cookie", it) }
        authCredentialStore.buildSapisidHashHeader("https://music.youtube.com")?.let {
            reqBuilder.header("Authorization", it)
        }
    }
    // ...
}
```

#### Modifica B: Ricerche e Suggerimenti 100% Anonimi
```kotlin
// In getSearchSuggestions:
val root = executeWebRemixPost("music/get_search_suggestions", payload, requireAuth = false)

// In searchSongs:
val root = executeWebRemixPost("search", payload, requireAuth = false)
// e nel fallback:
executeWebRemixPost("search", fallbackPayload, requireAuth = false)

// In searchArtists, searchAlbums, searchPlaylists:
val root = executeWebRemixPost("search", payload, requireAuth = false)
```

#### Modifica C: Cataloghi Pubblici Anonimi (Album, Artisti, Classifiche, Generi)
```kotlin
// In fetchAlbumOrPlaylistDetail (per album pubblici MPREb_ o OLAK5uy_):
executeWebRemixPost("browse", payload, requireAuth = false)

// In fetchArtistBrowse:
executeWebRemixPost("browse", payload, requireAuth = false)

// In fetchExploreCharts, fetchExploreMoods:
executeWebRemixPost("browse", payload, requireAuth = false)

// RIMANGONO AUTENTICATI (requireAuth = true) ESCLUSIVAMENTE:
// - fetchAccountLikedTracks (VLLM / FEmusic_liked_videos)
// - fetchAccountPlaylists (FEmusic_liked_playlists / FEmusic_library_landing)
// - fetchAccountHistoryTracks (FEmusic_history)
// - fetchAccountInfo (account/account_menu)
// - likeTrack / unlikeTrack (like/like, like/removelike)
// - createPlaylist / addTrackToPlaylist / removeTrackFromPlaylist
```

---

### 2. `MusicRepository.kt`
Protezione assoluta contro ricerche spurie di identificatori non validi.

#### Modifica: Validazione Regex in `resolveTrackById` e rimozione fallback per stringa
```kotlin
// PRIMA:
suspend fun resolveTrackById(trackId: String): Track? = withContext(Dispatchers.IO) {
    val local = dao.getTrackById(trackId)?.toDomain()
    if (local != null && ...) return@withContext local
    val fromMeta = innerTubeClient.fetchTrackMetadata(trackId)
    if (fromMeta != null) { ... return@withContext fromMeta }
    val fromSearch = innerTubeClient.searchSongs(trackId).firstOrNull { it.id == trackId }
    if (fromSearch != null) { ... }
    local?.takeIf { ... }
}

// DOPO:
private val YOUTUBE_VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

suspend fun resolveTrackById(trackId: String): Track? = withContext(Dispatchers.IO) {
    // Se non è un ID brano YouTube valido di 11 caratteri (es. "root", "tab_home", ecc.),
    // non eseguire alcuna chiamata di rete e restituisci null immediatamente.
    if (!trackId.matches(YOUTUBE_VIDEO_ID_REGEX)) {
        return@withContext null
    }

    val local = dao.getTrackById(trackId)?.toDomain()
    if (local != null && local.title.isNotBlank() && local.title != "Brano sconosciuto") {
        return@withContext local
    }

    val fromMeta = innerTubeClient.fetchTrackMetadata(trackId)
    if (fromMeta != null) {
        dao.upsertTrackPreservingFlags(fromMeta)
        return@withContext dao.getTrackById(trackId)?.toDomain() ?: fromMeta
    }

    // ELIMINATO il fallback searchSongs(trackId) che cercava l'ID come testo su YouTube!
    local?.takeIf { it.title != "Brano sconosciuto" && it.title.isNotBlank() }
}
```

---

### 3. `GMusicMediaLibraryService.kt`
Gestione corretta dei nodi gerarchici di Android Auto in `onGetItem`.

#### Modifica: Intercettazione nodi browsable prima della risoluzione
```kotlin
// PRIMA:
override fun onGetItem(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    mediaId: String
): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
    if (mediaId.startsWith(PREFIX_ARTIST)) { ... }
    val dummy = MediaItem.Builder().setMediaId(mediaId).build()
    val resolved = resolveFullMediaItem(dummy)
    LibraryResult.ofItem(resolved, null)
}

// DOPO:
override fun onGetItem(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    mediaId: String
): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
    // Intercetta tutti i nodi di root e cartelle browsable di Android Auto
    val containerItem = when (mediaId) {
        NODE_ROOT -> rootItem
        NODE_TAB_HOME -> rootTabFolder(NODE_TAB_HOME, getString(R.string.auto_root_home), R.drawable.ic_aa_home)
        NODE_TAB_SEARCH -> rootTabFolder(NODE_TAB_SEARCH, getString(R.string.auto_root_search), R.drawable.ic_aa_search)
        NODE_TAB_LIBRARY -> rootTabFolder(NODE_TAB_LIBRARY, getString(R.string.auto_root_library), R.drawable.ic_aa_library)
        NODE_LIB_LIKED -> browsableContainerItem(NODE_LIB_LIKED, getString(R.string.auto_root_liked), iconRes = R.drawable.ic_aa_heart)
        NODE_LIB_PLAYLISTS -> browsableContainerItem(NODE_LIB_PLAYLISTS, getString(R.string.auto_root_playlists), iconRes = R.drawable.ic_aa_library)
        NODE_LIB_ALBUMS -> browsableContainerItem(NODE_LIB_ALBUMS, getString(R.string.auto_lib_albums), iconRes = R.drawable.ic_aa_album)
        NODE_LIB_ARTISTS -> browsableContainerItem(NODE_LIB_ARTISTS, getString(R.string.auto_lib_artists), iconRes = R.drawable.ic_aa_artist)
        NODE_LIB_DOWNLOADS -> browsableContainerItem(NODE_LIB_DOWNLOADS, getString(R.string.auto_lib_downloads), iconRes = R.drawable.ic_aa_download)
        else -> null
    }
    if (containerItem != null) {
        return@future LibraryResult.ofItem(containerItem, null)
    }

    if (mediaId.startsWith(PREFIX_ARTIST)) {
        val artId = mediaId.removePrefix(PREFIX_ARTIST)
        val name = artistNameHints[artId] ?: artId
        return@future LibraryResult.ofItem(playableArtistItem(mediaId, name), null)
    }

    // Risolve solo se è un MediaItem reale con ID di 11 caratteri
    val dummy = MediaItem.Builder().setMediaId(mediaId).build()
    val resolved = resolveFullMediaItem(dummy)
    LibraryResult.ofItem(resolved, null)
}
```

---

### 4. `SettingsDataStore.kt` & `SettingsModalBottomSheet.kt`
Introduzione dei controlli di privacy per l'utente.

#### Modifiche alle impostazioni:
1. `anonymousSearch: Boolean = true` (Ricerche private e anonime senza cronologia YouTube)
2. `syncPlaybackHistoryToYouTube: Boolean = true` (Permette all'utente di disattivare l'invio dei ping `reportPlayback` se non vuole che i brani ascoltati appaiano nella cronologia visualizzazioni/ascolti di YouTube).
3. Nuova sezione **Privacy & Account YouTube** nel foglio delle Impostazioni con switch dedicati e descrizioni chiare.

---

## 🎯 Risultato Finale dopo l'Applicazione di v3.0.4

| Azione nell'App | Comportamento Attuale (3.0.3) | Comportamento con v3.0.4 |
| :--- | :--- | :--- |
| **Digitazione ricerca** | Invia cookie a Google $\rightarrow$ Salva suggerimenti nella cronologia | **100% Anonimo** $\rightarrow$ Nessuna traccia nella cronologia YouTube |
| **Ricerca brano/artista** | Invia cookie a Google $\rightarrow$ Registra la query nell'account | **100% Anonimo** $\rightarrow$ Nessuna traccia nella cronologia YouTube |
| **Navigazione Android Auto** | Cerca le parole `"root"`, `"tab_home"`, ecc. come canzoni | **Nodi intercettati** $\rightarrow$ Zero ricerche spurie verso YouTube |
| **Navigazione Generi / Mood** | Cerca `"musica allenamento"` con cookie utente | **Ricerca pubblica anonima** $\rightarrow$ Nessuna traccia su YouTube |
| **Cronologia Locale App** | Rimane perfetta e salvata in locale sul dispositivo | **Inalterata**: cronologia locale veloce e riservata |
| **Libreria Personale YT** | Funziona con login | **Inalterata**: preferiti e playlist si sincronizzano regolarmente |

---

## ✅ Stato Implementazione & Build
- [x] **`InnerTubeClient.kt`**: `requireAuth = false` per tutte le ricerche, suggerimenti ed esplorazione catalogo pubblico.
- [x] **`MusicRepository.kt`**: Validazione ID video YouTube a 11 caratteri (`^[a-zA-Z0-9_-]{11}$`), rimozione totale del fallback `searchSongs(trackId)` e tracciamento ascolti subordinato al toggle `syncPlaybackToYouTube`.
- [x] **`GMusicMediaLibraryService.kt`**: Intercettazione diretta in `onGetItem` dei nodi di sistema (`NODE_ROOT`, `NODE_TAB_*`, `NODE_LIB_*`, `PREFIX_*`) senza instradamento a `resolveFullMediaItem`.
- [x] **`SettingsDataStore.kt` & `SettingsViewModel.kt`**: Gestione persistente della preferenza `syncPlaybackToYouTube` (predefinito: `false` per la massima privacy).
- [x] **`SettingsModalBottomSheet.kt` & `SettingsScreen.kt`**: Card Privacy con toggle dedicato e nota esplicativa sulle ricerche anonime, più card changelog v3.0.4.
- [x] **Localizzazione**: Aggiornati i file `strings.xml` per IT, EN, ES, FR.
- [x] **Build Release**: Compilazione riuscita con `assembleRelease` (`versionCode = 39`, `versionName = "3.0.4"`).
- [x] **Artefatto**: APK generato e salvato in `versioni/GMusic 3.0.4.apk`.


