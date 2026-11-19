# GMusic v3.0.5 - Piano di Intervento: Coda Correlata da Ricerca & Controlli Shuffle/Repeat su Mobile e Android Auto

> **Stato:** Implementazione completata e APK Release v3.0.5 compilato con successo.

---

## 📋 Obiettivi della Versione 3.0.5 (`versionCode = 40`, `versionName = "3.0.5"`)

1. **Coda basata sui brani correlati (Radio) quando si riproduce dalla Ricerca (Mobile & Android Auto):**
   - Quando l'utente riproduce una canzone dai risultati di ricerca (sia da smartphone che da Android Auto o ricerca vocale), la coda **non** deve essere popolata con gli altri risultati della ricerca, ma con i brani correlati al brano scelto (mix/radio YouTube Music del brano).
2. **Controlli Shuffle e Cicla Playlist / Cicla Canzone su Android Auto:**
   - Aggiunta dei comandi rapidi interattivi **Shuffle** e **Cicla (Playlist / Canzone)** nella schermata "In riproduzione" di Android Auto tramite `MediaSession` `CommandButton` (`setCustomLayout` + `SessionCommand`), sincronizzati in tempo reale con il player.
3. **Cicla Playlist disponibile SOLO nei contesti Playlist/Raccolta (su Mobile e Android Auto):**
   - Se il brano in riproduzione **fa parte di una playlist / raccolta**, le opzioni di ripetizione sono: **OFF → Cicla Playlist (`REPEAT_MODE_ALL`) → Cicla Canzone (`REPEAT_MODE_ONE`) → OFF**.
   - Se il brano **non fa parte di una playlist** (es. riproduzione da Ricerca, singolo brano, radio correlati, deep link video singolo), l'opzione *Cicla Playlist* non ha senso e viene esclusa: il pulsante consente **solo OFF ↔ Cicla Canzone (`REPEAT_MODE_ONE`)**.

---

## 🔍 Analisi Tecnica dello Stato Attuale (v3.0.4)

### 1. Perché oggi la coda da Ricerca contiene i risultati di ricerca?
- **Mobile (`SearchScreen.kt`):**
  - Alla riga 348, il click su un brano in `state.songResults` invoca `onPlayTrack(track, state.songResults)`, passando l'intera lista dei risultati di ricerca come coda.
  - Inoltre in `PlayerViewModel.kt` (`playFromSearchQuery`, righe 367-383), usato per i comandi vocali / `MEDIA_PLAY_FROM_SEARCH` in `MainActivity.kt`, viene chiamato `playTrack(tracks.first(), tracks)` passando tutti i risultati della ricerca brani.
- **Android Auto (`GMusicMediaLibraryService.kt`):**
  - `executeGroupedAutoSearch(query)` (riga 1628) chiama `toMediaItemsFast(full.songs.take(15), ...)`, il quale **sovrascrive `lastBrowsedTracks`** (righe 504-506) con i 15 risultati di ricerca.
  - Quando l'utente tocca un brano nella ricerca Android Auto, `onSetMediaItems` (righe 1328-1334) trova `clickedId` in `lastBrowsedTracks` e costruisce l'intera coda usando proprio i risultati della ricerca (`lastBrowsedTracks`).
  - Lo stesso accade in `onSearch` con `isVoicePlay` (righe 1089-1098, dove peraltro vengono inclusi tutti gli item `isPlayable`, compresi eventuali artisti), in `onSetMediaItems` con `voiceQuery` (righe 1233-1254), in `onAddMediaItems` con `voiceQuery` (righe 1382-1389) e in `handleVoicePlayQuery` (righe 673-693).

### 2. Perché oggi manca Shuffle/Repeat su Android Auto e perché Mobile permette sempre "Cicla Playlist"?
- **Android Auto (`GMusicMediaLibraryService.kt`):**
  - Attualmente `updateLikeCustomButton` (righe 459-467) imposta `mediaLibrarySession?.setCustomLayout(ImmutableList.of(likeButton))` con **solo il pulsante Mi Piace**. Non sono registrati comandi né pulsanti `CommandButton` per Shuffle o Repeat, e `Player.Listener` non ascolta `onShuffleModeEnabledChanged` né `onRepeatModeChanged`.
- **Mobile (`PlayerViewModel.kt` & `PlayerComponents.kt`):**
  - `PlayerViewModel.cycleRepeatMode()` (righe 539-546) cicla incondizionatamente `REPEAT_MODE_OFF -> REPEAT_MODE_ALL -> REPEAT_MODE_ONE -> REPEAT_MODE_OFF`, anche quando l'utente sta ascoltando un brano singolo o una radio generata dalla ricerca.
  - Non esiste alcun flag di contesto (`isPlaylistContext`) propagato nei `MediaItem` condivisi tra app Mobile e `GMusicMediaLibraryService`.
  - Inoltre, in `GMusicMediaLibraryService.kt` (riga 350), l'Autoplay Infinito (`remainingInQueue <= 3`) si attiva anche quando `player.repeatMode == Player.REPEAT_MODE_ALL`, rischiando di appendere brani radio casuali in coda alla playlist invece di lasciarla ciclare.

---

## 🛠️ Piano Dettagliato delle Modifiche

### 1. Modello di Contesto Coda condiviso (`TrackMediaItemMapper.kt` & `SettingsDataStore.kt`)
- **`TrackMediaItemMapper.kt`:**
  - Aggiunta delle costanti extras:
    - `EXTRA_IS_PLAYLIST_CONTEXT = "is_playlist_context"` (`Boolean`)
    - `EXTRA_IS_SEARCH_RESULT = "is_search_result"` (`Boolean`)
  - Aggiornamento di `toMediaItem(...)` con parametri opzionali `isPlaylistContext: Boolean = false` e `isSearchResult: Boolean = false`.
  - Aggiunta dei helper `isPlaylistContext(item: MediaItem?): Boolean` e `isSearchResult(item: MediaItem?): Boolean`.
- **`SettingsDataStore.kt`:**
  - Aggiunta della preferenza `LAST_IS_PLAYLIST_CONTEXT` in `UserSettings` e `savePlaybackState(...)` in modo che al ripristino della coda dopo il riavvio dell'app venga preservato correttamente anche il contesto (Playlist vs Brano singolo/Radio).

---

### 2. Generazione Coda Correlata da Ricerca (`MusicRepository.kt`)
- Aggiunta del metodo `fetchRelatedQueueForTrack(track: Track, limit: Int = 20): List<Track>`:
  1. Recupera i brani correlati ufficiali tramite `innerTubeClient.fetchRadioTracks(track.id)` (radio `RDAMVM<videoId>` su YouTube Music).
  2. Se la radio restituisce lista vuota (es. tracce molto rare), esegue un fallback controllato con `searchSongs("${track.artistName} brani")`.
  3. Filtra eventuali duplicati e `track.id`, restituendo `listOf(track) + related.take(limit)`.
  - *Nota:* a differenza di `fetchAutoplayRadioAddition` (usato per l'autoplay infinito continuo), questo metodo iniziale non esclude i brani nella cronologia recente, così da garantire sempre una coda correlata ricca e pertinente per la canzone scelta.

---

### 3. Ricerca & Gestione Ripetizione su Mobile (`PlayerViewModel.kt`, `GMusicRoot.kt`, `SearchScreen.kt`, `PlayerComponents.kt`, altre Screen)

#### A. `PlayerViewModel.kt`
- Aggiunta di `val isPlaylistContext: Boolean = false` in `PlayerUiState`.
- Aggiornamento di `playTrack(...)`:
  - Nuovo parametro `isPlaylistContext: Boolean = false`.
  - Passa `isPlaylistContext` a `TrackMediaItemMapper.toMediaItem(...)` per ogni brano della coda.
  - Se `!isPlaylistContext` e `controller.repeatMode == Player.REPEAT_MODE_ALL`, reimposta automaticamente `controller.repeatMode = Player.REPEAT_MODE_OFF` (evitando di ereditare "Cicla playlist" fuori da una playlist).
- Nuovo metodo **`playTrackFromSearch(track: Track)`**:
  - Avvia subito la riproduzione di `track` con `isPlaylistContext = false` (partenza istantanea senza attendere la rete), cancellando eventuali richieste precedenti di coda ricerca.
  - In parallelo (su `Dispatchers.IO`), chiama `musicRepository.fetchRelatedQueueForTrack(track)` e, se il brano corrente è ancora `track.id`, sostituisce/aggiorna i brani successivi in coda (`controller.addMediaItems(...)` / aggiornamento coda) con i soli brani correlati (mai con i risultati della ricerca).
- Aggiornamento di `playFromSearchQuery(query: String)` (usato da `MEDIA_PLAY_FROM_SEARCH`):
  - Se `musicRepository.searchSongs(query)` trova un brano, avvia `playTrackFromSearch(direct.first())` invece di `playTrack(tracks.first(), tracks)`.
  - Se invece ripiega sull'artista (`full.artists.first()`), avvia la riproduzione contesto artista (`isArtistPlayback = true, isPlaylistContext = true`).
- Aggiornamento di `playFromVideoId(videoId: String)`: `isPlaylistContext = false` (e popolamento correlati come brano singolo).
- Aggiornamento di `playFromPlaylistId(playlistId: String)`: `isPlaylistContext = true`.
- Aggiornamento di `cycleRepeatMode()`:
  ```kotlin
  controller.repeatMode = if (_uiState.value.isPlaylistContext) {
      when (controller.repeatMode) {
          Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
          Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
          else -> Player.REPEAT_MODE_OFF
      }
  } else {
      when (controller.repeatMode) {
          Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
          else -> Player.REPEAT_MODE_OFF
      }
  }
  ```
- In `syncStateFromController(controller)`: legge `TrackMediaItemMapper.isPlaylistContext(currentItem)` e aggiorna `PlayerUiState.isPlaylistContext`.

#### B. `SearchScreen.kt` & `GMusicRoot.kt` & Altre Schermate
- **`SearchScreen.kt`:**
  - Introduzione di `onPlaySearchTrack: (Track) -> Unit` (o instradamento dedicato) sul click di un brano in `state.songResults` (riga 348) verso `playerViewModel.playTrackFromSearch(track)`.
- **Propagazione `isPlaylistContext` da `GMusicRoot.kt`:**
  - `PlaylistDetailScreen` (playlist locali e YouTube Music): `isPlaylistContext = true`
  - `AlbumScreen` (album e playlist pubbliche aperte via `album/{id}`): `isPlaylistContext = true`
  - `LikedSongsScreen` ("Canzoni preferite") e `DownloadedSongsScreen` ("Brani scaricati"): `isPlaylistContext = true`
  - `ArtistScreen`: discografia / brani popolari artista (`songsToPlay`) -> `isPlaylistContext = true`; singolo brano (`listOf(match)`) -> `isPlaylistContext = false`
  - `HomeScreen`: `HomeItemType.MIX` -> `isPlaylistContext = true`; menu contestuale "Riproduci" su singolo brano (`listOf(track)`) -> `isPlaylistContext = false`

#### C. `PlayerComponents.kt` (UI Mobile Portrait e Landscape)
- Nei controlli del `FullScreenPlayerSheet` (sia portrait che landscape):
  - Se `state.isPlaylistContext == true`: il pulsante Ripeti mostra `Icons.Filled.Repeat` (per `OFF` e `ALL`) e `Icons.Filled.RepeatOne` (per `ONE`), con descrizione dinamica (`Ripeti` / `Cicla playlist` / `Cicla brano`).
  - Se `state.isPlaylistContext == false`: il pulsante Ripeti mostra `Icons.Filled.RepeatOne` (spento `onSurfaceVariant` se `OFF`, acceso `primary` se `REPEAT_MODE_ONE`) con descrizione `R.string.player_repeat_one` ("Cicla brano"), rendendo esplicito che è attiva solo la ripetizione del singolo brano.

---

### 4. Android Auto: Coda Correlata da Ricerca + Pulsanti Shuffle & Repeat (`GMusicMediaLibraryService.kt`)

#### A. Ricerca su Android Auto (Niente più coda da risultati ricerca)
- In `executeGroupedAutoSearch(query)`:
  - Non sovrascrive più `lastBrowsedTracks` con `full.songs`.
  - Marca i brani restituiti dalla ricerca con `isSearchResult = true` (`EXTRA_IS_SEARCH_RESULT = true`) e `isPlaylistContext = false` (mantenendo un set/cache `lastSearchTrackIds` per sicurezza).
- In `onSetMediaItems`:
  - Quando `mediaItems.size == 1` e il brano proviene da ricerca (`TrackMediaItemMapper.isSearchResult(firstItem)` oppure `clickedId in lastSearchTrackIds`):
    - Azzera `activeArtistPlaybackName` e `activeArtistPlaybackId`.
    - Risolve il brano e invoca `musicRepository.fetchRelatedQueueForTrack(track)` su `Dispatchers.IO`.
    - Costruisce la coda con `[track] + related` (tutti marcati `isPlaylistContext = false`) e se `player.repeatMode == Player.REPEAT_MODE_ALL`, lo reimposta a `Player.REPEAT_MODE_OFF`.
  - Nei rami `voiceQuery != null` (`onSetMediaItems`, `onAddMediaItems`, `onSearch` con `isVoicePlay == true`, `handleVoicePlayQuery`):
    - Se la ricerca restituisce brani (`directSongs`), seleziona `directSongs.first()` e costruisce la coda tramite `musicRepository.fetchRelatedQueueForTrack(firstTrack)` con `isPlaylistContext = false`.
    - Se la ricerca vocale su `onSearch` (`isVoicePlay`) trova invece solo un artista, avvia la discografia dell'artista (`isPlaylistContext = true`).
- In `buildContainerChildrenWithPlayAndShuffle` e `onSetMediaItems` per container (`PREFIX_PLAYLIST`, `PREFIX_REMOTE_PLAYLIST`, `PREFIX_ALBUM`, `PREFIX_MIX`, `PREFIX_ARTIST`, `NODE_LIB_LIKED`, `NODE_LIB_DOWNLOADS`, `PREFIX_ACTION_PLAY_ALL`, `PREFIX_ACTION_SHUFFLE`):
  - Tutti i `MediaItem` di questi contenitori ricevono esplicitamente `isPlaylistContext = true`.

#### B. Pulsanti Shuffle & Cicla Playlist / Cicla Canzone su Android Auto
- Nuove costanti in `GMusicMediaLibraryService`:
  - `ACTION_TOGGLE_SHUFFLE = "com.gmusic.app.ACTION_TOGGLE_SHUFFLE"`
  - `ACTION_CYCLE_REPEAT = "com.gmusic.app.ACTION_CYCLE_REPEAT"`
- In `LibrarySessionCallback.onConnect(...)`:
  - Aggiunta di `SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)` e `SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY)` a `availableSessionCommands`.
- In `LibrarySessionCallback.onPostConnect(...)`:
  - Invocazione di `updateSessionCustomLayout()` per inviare immediatamente i pulsanti ad Android Auto appena si connette.
- In `LibrarySessionCallback.onCustomCommand(...)`:
  - `ACTION_TOGGLE_SHUFFLE`: inverte `player.shuffleModeEnabled = !player.shuffleModeEnabled` e aggiorna il layout.
  - `ACTION_CYCLE_REPEAT`: legge `isPlaylistContext` dal `player.currentMediaItem`:
    - Se `true`: cicla `OFF -> ALL -> ONE -> OFF`.
    - Se `false`: cicla solo `OFF <-> ONE`.
    - Aggiorna il layout.
- Nuovo metodo unificato `updateSessionCustomLayout(isLiked: Boolean)` (sostituisce `updateLikeCustomButton`):
  Costruisce la lista dei `CommandButton` usando le icone ufficiali Media3 1.5.0:
  1. **Mi Piace**: `CommandButton.ICON_HEART_FILLED` / `CommandButton.ICON_HEART_UNFILLED`
  2. **Shuffle**: `CommandButton.ICON_SHUFFLE_ON` / `CommandButton.ICON_SHUFFLE_OFF` (display name: "Casuale: ON" / "Casuale: OFF")
  3. **Ripeti**:
     - Se `isPlaylistContext == true`:
       - `REPEAT_MODE_OFF` → `CommandButton.ICON_REPEAT_OFF` ("Ripeti: OFF")
       - `REPEAT_MODE_ALL` → `CommandButton.ICON_REPEAT_ALL` ("Cicla playlist")
       - `REPEAT_MODE_ONE` → `CommandButton.ICON_REPEAT_ONE` ("Cicla canzone")
     - Se `isPlaylistContext == false`:
       - `REPEAT_MODE_OFF` → `CommandButton.ICON_REPEAT_OFF` ("Cicla canzone: OFF")
       - `REPEAT_MODE_ONE` → `CommandButton.ICON_REPEAT_ONE` ("Cicla canzone: ON")
- In `setupPlayerListeners()`:
  - Ascolto di `onShuffleModeEnabledChanged(shuffleModeEnabled)` e `onRepeatModeChanged(repeatMode)` per aggiornare subito Android Auto anche quando l'utente preme Shuffle/Repeat dal telefono.
  - In `onMediaItemTransition`:
    - Se `!isPlaylistContext && player.repeatMode == Player.REPEAT_MODE_ALL`, reimposta `player.repeatMode = Player.REPEAT_MODE_OFF`.
    - Aggiorna subito `updateSessionCustomLayout(...)`.
    - **Autoplay Infinito**: si attiva solo se `player.repeatMode == Player.REPEAT_MODE_OFF`, così quando l'utente attiva "Cicla playlist" (`REPEAT_MODE_ALL`) o "Cicla canzone" (`REPEAT_MODE_ONE`), la coda non viene allungata con brani radio casuali e cicla esattamente sulla playlist/canzone.

---

### 5. Versioning, Localizzazione & Changelog
- **`app/build.gradle.kts`**: aggiornamento `versionCode = 40`, `versionName = "3.0.5"`.
- **`res/values/strings.xml`, `values-it/strings.xml`, `values-es/strings.xml`, `values-fr/strings.xml`**:
  - Nuove stringhe localizzate per i pulsanti Shuffle/Cicla playlist/Cicla canzone (Mobile e Android Auto).
  - Aggiunta voci changelog `settings_changelog_v305_title` e `settings_changelog_v305_desc` (rimuovendo "(Attuale)"/"(Current)" dalla v3.0.4).
- **`SettingsModalBottomSheet.kt`**: aggiunta card changelog v3.0.5 in cima alla sezione Informazioni.

---

## ✅ Decisioni Confermate & Stato Implementazione

### Decisioni Confermate
1. **Classificazione `isPlaylistContext` (Opzione A):**
   - `true`: Playlist locali/YouTube Music, Album, Mix ("Fatto per te"), Canzoni preferite, Brani scaricati, Discografia Artista.
   - `false`: Ricerca (Mobile, Android Auto, comandi vocali), Brani singoli, Tendenze, Cronologia ascolti, scaffali Home non-Mix.
2. **Sezione Tendenze nella Ricerca (barra vuota):** mantiene la coda della classifica Tendenze (`isPlaylistContext = false`).

### Checklist Modifiche & Build
- [x] **`TrackMediaItemMapper.kt` & `SettingsDataStore.kt`**: aggiunti `EXTRA_IS_PLAYLIST_CONTEXT`, `EXTRA_IS_SEARCH_RESULT` e persistenza `lastIsPlaylistContext`.
- [x] **`MusicRepository.kt`**: aggiunto `fetchRelatedQueueForTrack` basato su `fetchRadioTracks` (con fallback controllato).
- [x] **`PlayerViewModel.kt`**: aggiunto `playTrackFromSearch`, aggiornamento `playFromSearchQuery`/`playTrack`/`cycleRepeatMode` e campo `isPlaylistContext` in `PlayerUiState`.
- [x] **`SearchScreen.kt`, `HomeScreen.kt`, `ArtistScreen.kt`, `GMusicRoot.kt`**: propagazione corretta di `onPlaySearchTrack`, `onPlayMix`, `onPlaySingleTrack` e `isPlaylistContext`.
- [x] **`PlayerComponents.kt`**: estratto `RepeatModeIconButton` con icona `RepeatOne` quando `!isPlaylistContext` (solo *Cicla canzone*).
- [x] **`GMusicMediaLibraryService.kt`**: coda correlata da ricerca/voce, `lastSearchTrackIds` isolato da `lastBrowsedTracks`, `ACTION_TOGGLE_SHUFFLE` e `ACTION_CYCLE_REPEAT` in `setCustomLayout` per Android Auto, ascolto `onShuffleModeEnabledChanged`/`onRepeatModeChanged` e blocco autoplay quando `repeatMode != Player.REPEAT_MODE_OFF`.
- [x] **`app/build.gradle.kts`, `strings.xml` (EN/IT/ES/FR), `SettingsModalBottomSheet.kt`**: aggiornati `versionCode = 40`, `versionName = "3.0.5"` e changelog v3.0.5.
- [x] **Build Release**: `assembleRelease` completato con successo (`GMusic 3.0.5.apk` in root e in `versioni/GMusic 3.0.5.apk`).
