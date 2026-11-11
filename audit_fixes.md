# 🔍 GMusic - Audit Approfondito del Codice & Piano di Correzione

**Ruolo:** Senior Android Developer & UI/UX Specialist  
**Stack Tecnologico:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.01), Material Design 3, Media3 1.5.0, Hilt, Room, KSP, Coroutines & Flow.  
**Data Revisione:** Novembre 2026

---

## Indice dei Contenuti
1. [Gradle & Dipendenze](#1-gradle--dipendenze)
2. [Bug Logici & State Management](#2-bug-logici--state-management)
3. [UI / UX & Jetpack Compose](#3-ui--ux--jetpack-compose)
4. [Piano di Correzione Operativo (Diff & Snippet di Codice)](#4-piano-di-correzione-operativo)

---

# 1. Gradle & Dipendenze

### 1.1 Path di Build Assoluto Hardcoded nel Root `build.gradle.kts`
- **Gravità:** 🚨 **Critica**
- **File:** `build.gradle.kts` (Righe 10-12)
- **Problema:** 
  ```kotlin
  allprojects {
      layout.buildDirectory.set(file("C:/AndroidBuilds/GMusic/${project.name}"))
  }
  ```
  La directory di output della build è fissata su un percorso Windows locale (`C:/AndroidBuilds/...`). Qualsiasi pipeline di CI/CD (GitHub Actions, Docker Linux) o qualsiasi altro sviluppatore che cloni il repository su Mac, Linux o una diversa partizione Windows fallirà immediatamente con errore di IO o permessi negati.
- **Risoluzione:** Rimuovere l'override globale o vincolarlo a una proprietà locale facoltativa in `local.properties`.

---

### 1.2 Blocco `kotlinOptions` Deprecato in Kotlin 2.0+
- **Gravità:** 🟡 **Media**
- **File:** `app/build.gradle.kts` (Righe 57-59)
- **Problema:** 
  ```kotlin
  kotlinOptions {
      jvmTarget = "17"
  }
  ```
  Con Kotlin 2.0+ e Android Gradle Plugin 8.7+, la sintassi `kotlinOptions` all'interno del blocco `android {}` è deprecata e genera warning a ogni sync di Gradle.
- **Risoluzione:** Migrare al blocco nativo `kotlin.compilerOptions`:
  ```kotlin
  kotlin {
      compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
      }
  }
  ```

---

### 1.3 Dipendenza Mastodontica `compose-material-icons-extended`
- **Gravità:** 🟡 **Media (Prestazioni Build & DEX)**
- **File:** `gradle/libs.versions.toml` (Riga 44) e `app/build.gradle.kts` (Riga 95)
- **Problema:** 
  `androidx-compose-material-icons-extended` include oltre 30.000 classi vettoriali. Anche se R8/ProGuard in release esegue il tree-shaking, durante la fase di sviluppo (debug build) rallenta drasticamente il dexing incrementale e appesantisce il classpath dell'IDE.
- **Risoluzione:** Verificare le sole icone realmente necessarie e preferire drawable XML vettoriali locali in `res/drawable/` per le icone non presenti nel set base `Icons.Default`.

---

### 1.4 File e Schermate "Morte" (Dead Code)
- **Gravità:** 🟢 **Bassa (Pulizia architetturale)**
- **File coinvolti:**
  1. `app/src/main/java/com/gmusic/app/ui/explore/ExploreScreen.kt` & `ExploreViewModel.kt`: importato in `GMusicRoot.kt` ma non presente in alcuna rotta del `NavHost` (le sezioni esplora sono state migrate in `SearchScreen`).
  2. `app/src/main/java/com/gmusic/app/ui/settings/SettingsScreen.kt`: presente ma mai mostrato, poiché la rotta `"settings"` in `GMusicRoot.kt` (riga 430) esegue un `LaunchedEffect` che apre `SettingsModalBottomSheet` ed esegue subito il `popBackStack()`.
- **Risoluzione:** Rimuovere o deprecare esplicitamente i file inutilizzati per non confondere la manutenzione del codice.

---

# 2. Bug Logici & State Management

### 2.1 Recomposition Disaster: Monolithic `PlayerUiState` ricompone la Root dell'App ogni 300ms
- **Gravità:** 🚨 **Critica (Prestazioni CPU & Consumo Batteria)**
- **File:** `PlayerViewModel.kt` (Righe 342-364) & `GMusicRoot.kt` (Righe 116-218)
- **Analisi Tecnica:**
  `PlayerViewModel` esegue un loop `startProgressTicker()` che ogni 300 millisecondi aggiorna `_uiState`:
  ```kotlin
  _uiState.update { it.copy(currentPositionMs = pos, durationMs = dur) }
  ```
  In `GMusicRoot.kt`, il Composable root osserva:
  ```kotlin
  val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
  ```
  Poiché `PlayerUiState` è un'unica `data class` monolitica, ogni 300ms viene emessa una **nuova istanza di stato**. Di conseguenza, l'intero albero di `GMusicRoot` (Scaffold, calcoli di layout, NavHost container, NavigationBarItem) viene **rieseguito e ricomposto 3-4 volte al secondo** anche quando l'utente sta solo guardando la schermata Home!
- **Risoluzione:**
  Separare lo stato ad alta frequenza (`currentPositionMs`, `durationMs`) in un `StateFlow<PlaybackProgress>` distinto oppure utilizzare letture atomiche tramite lambda (Deferred State Reads) all'interno dei soli componenti che disegnano la timeline (`WavySlider` e `LinearProgressIndicator`).

---

### 2.2 Memory Churn & Thread Contention nel Disegno di `WavySlider.kt`
- **Gravità:** 🚨 **Critica (Frame Drop & Garbage Collection Stutter)**
- **File:** `app/src/main/java/com/gmusic/app/ui/player/components/WavySlider.kt` (Righe 131-154)
- **Analisi Tecnica:**
  All'interno della lambda `Canvas(modifier = Modifier.matchParentSize()) { ... }`:
  ```kotlin
  if (ampPx > 0.05f) {
      val path = Path() // ALLOCAZIONE AD OGNI SINGOLO FRAME A 60/120 FPS!
      path.moveTo(0f, centerY)
      var x = 0f
      val stepPx = 2f
      while (x <= thumbX) {
          val angle = (2f * PI.toFloat() * (x / waveLengthPx)) - wavePhase
          val y = centerY + (ampPx * sin(angle))
          path.lineTo(x, y)
          x += stepPx
      }
      path.lineTo(thumbX, centerY)
      drawPath(path = path, ...)
  }
  ```
  La fase dell'onda `wavePhase` cambia continuamente tramite `infiniteTransition`. Ad ogni frame (120 volte al secondo su display moderni) viene istanziato un nuovo oggetto `Path()`, allocando centinaia di punti in memoria. Questo genera un massiccio lavoro per il Garbage Collector Android, con micro-blocchi (jank) evidenti durante l'ascolto e lo scorrimento del player.
- **Risoluzione:**
  Istanziere e ricordare l'oggetto `Path` con `remember { Path() }` e fare `path.rewind()` o `path.reset()` prima del calcolo dei vertici. Inoltre, campionare a passi proporzionali a `waveLengthPx / 16` anziché a passo fisso di `2px` per ridurre drasticamente i calcoli trigonometrici `sin()`.

---

### 2.3 Conflitto di Gestures: Touch Tap bloccato da Drag in `WavySlider.kt`
- **Gravità:** 🔴 **Alta (Bug UX)**
- **File:** `WavySlider.kt` (Righe 77-106)
- **Analisi Tecnica:**
  Il componente concatena due modificatori `pointerInput` consecutivi sullo stesso box:
  ```kotlin
  .pointerInput(enabled) { detectTapGestures { ... } }
  .pointerInput(enabled) { detectDragGestures { ... } }
  ```
  In Compose, il primo `pointerInput` consuma l'evento di `PointerEventType.Press` o `Down`. Di conseguenza, `detectDragGestures` spesso perde l'evento iniziale di trascinamento prima del superamento dello slop, oppure il tap viene ignorato perché il drag gesture intercetta il tocco.
- **Risoluzione:** Gestire tocco e trascinamento in un unico modificatore unificato o utilizzare `awaitEachGesture`.

---

### 2.4 Anti-Pattern di Ricomposizione continua in `SyncedLyricsSheetContent`
- **Gravità:** 🔴 **Alta (Prestazioni)**
- **File:** `app/src/main/java/com/gmusic/app/ui/player/PlayerComponents.kt` (Righe 1181-1190)
- **Analisi Tecnica:**
  ```kotlin
  val activeLineIndex = remember(lyrics, currentPositionMs) {
      if (!lyrics.isSynced) 0
      else lyrics.lines.indexOfLast { it.timeMs <= currentPositionMs + 250L }.coerceAtLeast(0)
  }
  LaunchedEffect(activeLineIndex) {
      if (lyrics.isSynced && activeLineIndex in lyrics.lines.indices) {
          listState.animateScrollToItem((activeLineIndex - 2).coerceAtLeast(0))
      }
  }
  ```
  Poiché `currentPositionMs` cambia continuamente, `remember(lyrics, currentPositionMs)` riesegue il blocco e produce un nuovo valore a ogni tick di avanzamento, invalidando lo scope del Composable anche se l'indice della riga è rimasto identico (es. per tutta la durata di una strofa di 5 secondi).
- **Risoluzione:**
  Utilizzare `remember(lyrics) { derivedStateOf { ... } }`. In questo modo la ricomposizione e lo scroll automatico scattano **esclusivamente quando l'indice della riga attiva cambia realmente**.

---

### 2.5 Assenza di Try-Catch / Infinite Loading nei ViewModel
- **Gravità:** 🔴 **Alta (Stato Bloccato / Crash)**
- **File coinvolti:**
  1. `HomeViewModel.kt` (Righe 164-203): `refresh()` e `loadNextPage()` chiamano `musicRepository.buildHomeFeedSections()`. Se non c'è rete o InnerTube fallisce con `IOException`, non c'è alcun `try-catch`. `isLoading` e `isRefreshing` rimangono `true` per sempre; lo skeleton shimmer non scompare mai.
  2. `SearchViewModel.kt` (Righe 129-144): `executeSearch()` chiama `musicRepository.searchAllCategories()`. In caso di errore di rete, `isLoading` rimane `true` indefinitamente.
  3. `AlbumViewModel.kt` (Righe 82-91) & `ArtistViewModel.kt` (Righe 89-101): `loadAlbum` e `loadArtist` non gestiscono eccezioni. `detail` resta `null` e l'utente visualizza un `CircularProgressIndicator` infinito.
- **Risoluzione:** Introdurre blocchi `try-catch-finally` con aggiornamento di un campo `errorMessage` nell'UiState e reset garantito di `isLoading = false`.

---

### 2.6 Data Loss Grave: Eliminazione Playlist senza Dialogo di Conferma
- **Gravità:** 🔴 **Alta (Perdita Dati Utente)**
- **File:** `app/src/main/java/com/gmusic/app/ui/library/LibraryScreen.kt` (Righe 1080-1091)
- **Analisi Tecnica:**
  Nel dettaglio della playlist:
  ```kotlin
  IconButton(
      onClick = {
          viewModel.deletePlaylist(playlistId)
          onBack()
      }
  ) {
      Icon(imageVector = Icons.Filled.Delete, ...)
  }
  ```
  Premendo accidentalmente l'icona del cestino nella barra superiore, la playlist viene **eliminata istantaneamente dal database locale** e la schermata viene chiusa senza alcuna richiesta di conferma.
- **Risoluzione:** Aggiungere uno stato `var showDeleteConfirmation by remember { mutableStateOf(false) }` e un `AlertDialog` di conferma prima di procedere con l'eliminazione.

---

### 2.7 Memory Leak da `WebView` e Navigazione Non Protetta in `WebLoginScreen.kt`
- **Gravità:** 🔴 **Alta (Memory Leak & Crash NavController)**
- **File:** `app/src/main/java/com/gmusic/app/ui/auth/WebLoginScreen.kt` (Righe 86-126)
- **Analisi Tecnica:**
  - `AndroidView` alloca un `WebView` senza implementare `onRelease = { it.destroy() }`. Poiché la WebView mantiene riferimenti interni a ViewRootImpl e Context, quando l'utente chiude la schermata si verifica un memory leak rilevante.
  - In `onPageFinished`, il callback può scattare ripetutamente (redirect Google, caricamento iframe). `onCookiesExtracted(cookies, cleanVisitor)` viene invocato più volte consecutive. In `GMusicRoot.kt` questo esegue `navController.popBackStack()` multipli, rischiando di far uscire l'utente dall'applicazione o di generare un crash sul backstack.
- **Risoluzione:** Aggiungere un flag di guardia `var isExtracted by remember { mutableStateOf(false) }` e implementare `onRelease` per distruggere la WebView.

---

# 3. UI / UX & Jetpack Compose

### 3.1 Sovrapposizione Visiva: Elementi Nascosti sotto MiniPlayer e Navigation Bar
- **Gravità:** 🚨 **Critica (Difetto Visivo & Usabilità)**
- **File coinvolti:**
  - `GMusicRoot.kt` (Righe 212-217 e 455-530)
  - `SearchScreen.kt` (Riga 291)
  - `AlbumScreen.kt` (Riga 169)
  - `ArtistScreen.kt` (Riga 178)
- **Analisi Tecnica:**
  In `GMusicRoot.kt`, il `Scaffold` ha `contentWindowInsets = WindowInsets(0, 0, 0, 0)` e ignora completamente il padding interno (`{ _ -> NavHost(...) }`).
  Sopra i contenuti galleggiano (ancorati a `Alignment.BottomCenter`):
  - Il `MiniPlayerBar`: altezza 68dp + 12dp padding = 80dp.
  - La `NavigationBar` a pillola: altezza 62dp + 12dp padding + `navigationBarsPadding` (~48dp) = ~122dp.
  - **Spazio totale occupato in basso: oltre 180-200dp!**
  
  Tuttavia:
  - In `SearchScreen.kt`, i risultati di ricerca usano `contentPadding = PaddingValues(bottom = 24.dp)`.
  - In `AlbumScreen.kt`, la lista tracce usa `contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)` (solo **12dp** in fondo!).
  - In `ArtistScreen.kt`, la lista discografia usa `contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)`.
  
  **Risultato visivo:** Gli ultimi 3 o 4 brani di qualsiasi album, artista o ricerca risultano **totalmente coperti e invisibili sotto il MiniPlayer e la Navigation Bar**, rendendo impossibile per l'utente toccarli o visualizzarli!
- **Risoluzione:** Configurare un `bottomPadding` calcolato dinamicamente (es. `140.dp` senza player, `200.dp` con player attivo) oppure utilizzare un `CompositionLocal` / WindowInsets padding dedicato.

---

### 3.2 Overflow Verticale in `FullScreenPlayerSheet` su Schermi Compatti
- **Gravità:** 🔴 **Alta (Glitch Visivo)**
- **File:** `PlayerComponents.kt` (Righe 726-800)
- **Analisi Tecnica:**
  Nel layout verticale del player a schermo intero:
  ```kotlin
  Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.SpaceBetween
  ) {
      // Header (~48dp)
      // AsyncImage(Modifier.fillMaxWidth().aspectRatio(1f)) (~360dp)
      // Titolo + Artista + Azioni (~64dp)
      // WavySlider + Timestamp (~60dp)
      // Controlli M3 Play/Pause (~80dp)
      // Barra Lyrics/Queue (~48dp)
  }
  ```
  Su dispositivi con diagonale ridotta, form-factor 16:9, display esterni di foldable o con impostazioni di accessibilità con "Dimensione carattere / display ingrandita", l'altezza complessiva richiesta supera gli 800dp disponibili. Essendo una `Column` rigida con `fillMaxWidth().aspectRatio(1f)`, gli elementi inferiori vengono spinti fuori dallo schermo o tagliati dietro la barra di navigazione del sistema.
- **Risoluzione:** Modificare la copertina dell'album affinché sia flessibile: `.weight(1f, fill = false)` oppure racchiusa con `.sizeIn(maxHeight = 320.dp)`.

---

### 3.3 Mancanza di Stati Vuoti (Empty States) e di Errore
- **Gravità:** 🟡 **Media (Qualità UX)**
- **File:** `SearchScreen.kt`, `LibraryScreen.kt`
- **Problema:**
  - Quando una ricerca non produce risultati (`songResults.isEmpty()` ecc.), `SearchScreen` mostra una schermata vuota grigia/nera senza alcun messaggio informativo ("Nessun risultato trovato per 'xyz'").
  - In `LibraryPlaylistsScreen`, `LibraryArtistsScreen` e `LibraryAlbumsScreen`, se l'utente non ha ancora salvato elementi o la ricerca locale fallisce, la schermata appare interrotta e vuota.
- **Risoluzione:** Introdurre un componente standard `EmptyStateView(icon, title, message)` mostrato quando le liste filtrate sono vuote.

---

### 3.4 Accessibilità: Mancanza di `contentDescription` su Elementi Interattivi
- **Gravità:** 🟡 **Media (WCAG & TalkBack)**
- **File coinvolti:**
  - `CommonComponents.kt` (Riga 213): Nel tasto menu a tre puntini del brano: `Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null)`. TalkBack legge solo "Pulsante non etichettato".
  - `PlayerComponents.kt` (Righe 944, 967, 979): Nei controlli del player `SkipPrevious`, `Play/Pause`, `SkipNext`, le icone hanno `contentDescription = null`! Un utente non vedente non riceve alcun feedback vocale su quale sia il tasto di riproduzione.
- **Risoluzione:** Assegnare sempre le stringhe localizzate corrispondenti (`R.string.player_play`, `R.string.player_pause`, `R.string.action_more_options`).

---

### 3.5 Visual Bug & Window Leak: Logo dell'app visibile sullo sfondo durante apertura/chiusura o drag del Player
- **Gravità:** 🚨 **Critica (Glitch Visivo & Window Leak)**
- **File coinvolti:**
  - `res/values/themes.xml` (Riga 4)
  - `res/drawable/splash_background.xml`
  - `MainActivity.kt` (Riga 27)
  - `GMusicRoot.kt` (Righe 171-175)
- **Analisi Tecnica:**
  Nel tema `Theme.GMusic` in `themes.xml`, `android:windowBackground` era impostato su `@drawable/splash_background`, che posiziona al centro dello schermo la bitmap con testo `@drawable/splash_logo_with_text`.
  In `GMusicRoot.kt`, il player a schermo intero (`FullScreenPlayerSheet`) e l'interfaccia principale sono commutati tramite `SharedTransitionLayout` e `AnimatedContent`.
  Quando l'utente alza/abbassa il player o lo trascina verso il basso (`drag/swipe down`), Compose anima la transizione con scale e crossfade. Durante l'animazione o il trascinamento gestuale, la gerarchia Compose diventa parzialmente traslucida o si sposta geometricamente, **esponendo la Window nativa sottostante**. Poiché la Window manteneva perennemente il background splash, l'utente vedeva comparire e lampeggiare il logo dell'app da sotto il player.
- **Risoluzione:**
  1. In `themes.xml`, impostare `android:windowBackground` di `Theme.GMusic` su un colore solido (`@color/black`), isolando lo splash drawable solo in un tema splash dedicato.
  2. In `MainActivity.kt`, azzerare il window background a runtime con `window.setBackgroundDrawableResource(android.R.color.black)` in `onCreate()`.
  3. In `GMusicRoot.kt`, racchiudere il layout di transizione in una `Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background)` per garantire opacità solida totale durante qualsiasi transizione animata.

---

# 4. Piano di Correzione Operativo

In questa sezione sono riportate le correzioni concrete pronte da applicare nel codice, con snippet **Prima / Dopo**.

---

### Correzione 4.1: Rimozione Path Assoluto Locale in `build.gradle.kts`
- **File:** `build.gradle.kts` (Righe 10-12)

#### ❌ PRIMA:
```kotlin
allprojects {
    layout.buildDirectory.set(file("C:/AndroidBuilds/GMusic/${project.name}"))
}
```

#### ✅ DOPO:
```kotlin
// Consente alle build locali e di CI/CD di compilare nella cartella standard di progetto
// senza dipendere da un disco Windows locale C:/
allprojects {
    // Lasciato al default di Gradle per garantire portabilità su Linux/Mac/CI
}
```

---

### Correzione 4.2: Migrazione Sintassi `kotlinOptions` in `app/build.gradle.kts`
- **File:** `app/build.gradle.kts` (Righe 57-59)

#### ❌ PRIMA:
```kotlin
    kotlinOptions {
        jvmTarget = "17"
    }
```

#### ✅ DOPO:
```kotlin
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
```

---

### Correzione 4.3: Risoluzione Memory Churn & Crash di Disegno in `WavySlider.kt`
- **File:** `app/src/main/java/com/gmusic/app/ui/player/components/WavySlider.kt` (Righe 131-155)

#### ❌ PRIMA:
```kotlin
            // 2. Traccia Attiva: Sinusoide fluida se c'è ampiezza, altrimenti retta
            if (thumbX > 0f) {
                if (ampPx > 0.05f) {
                    val path = Path() // ALLOCAZIONE CONTINUA AD OGNI FRAME
                    path.moveTo(0f, centerY)

                    var x = 0f
                    val stepPx = 2f
                    while (x <= thumbX) {
                        val angle = (2f * PI.toFloat() * (x / waveLengthPx)) - wavePhase
                        val y = centerY + (ampPx * sin(angle))
                        path.lineTo(x, y)
                        x += stepPx
                    }
                    path.lineTo(thumbX, centerY)

                    drawPath(
                        path = path,
                        color = activeColor,
                        style = Stroke(
                            width = trackStrokePx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                } else {
                    drawLine(
                        color = activeColor,
                        start = Offset(0f, centerY),
                        end = Offset(thumbX, centerY),
                        strokeWidth = trackStrokePx,
                        cap = StrokeCap.Round
                    )
                }
            }
```

#### ✅ DOPO:
```kotlin
    // Ricorda l'oggetto Path evitando allocazioni nello scope di disegno (Zero GC churn)
    val wavePath = remember { Path() }

    // Nel Canvas DrawScope:
            if (thumbX > 0f) {
                if (ampPx > 0.05f) {
                    wavePath.rewind()
                    wavePath.moveTo(0f, centerY)

                    // Campionamento ottimizzato proporzionale alla lunghezza d'onda
                    val stepPx = (waveLengthPx / 16f).coerceIn(2f, 6f)
                    var x = 0f
                    while (x <= thumbX) {
                        val angle = (2f * PI.toFloat() * (x / waveLengthPx)) - wavePhase
                        val y = centerY + (ampPx * sin(angle))
                        wavePath.lineTo(x, y)
                        x += stepPx
                    }
                    wavePath.lineTo(thumbX, centerY)

                    drawPath(
                        path = wavePath,
                        color = activeColor,
                        style = Stroke(
                            width = trackStrokePx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                } else {
                    drawLine(
                        color = activeColor,
                        start = Offset(0f, centerY),
                        end = Offset(thumbX, centerY),
                        strokeWidth = trackStrokePx,
                        cap = StrokeCap.Round
                    )
                }
            }
```

---

### Correzione 4.4: Risoluzione Ricomposizioni Infinite in `SyncedLyricsSheetContent`
- **File:** `app/src/main/java/com/gmusic/app/ui/player/PlayerComponents.kt` (Righe 1180-1191)

#### ❌ PRIMA:
```kotlin
    val listState = rememberLazyListState()
    val activeLineIndex = remember(lyrics, currentPositionMs) {
        if (!lyrics.isSynced) 0
        else lyrics.lines.indexOfLast { it.timeMs <= currentPositionMs + 250L }.coerceAtLeast(0)
    }

    LaunchedEffect(activeLineIndex) {
        if (lyrics.isSynced && activeLineIndex in lyrics.lines.indices) {
            listState.animateScrollToItem((activeLineIndex - 2).coerceAtLeast(0))
        }
    }
```

#### ✅ DOPO:
```kotlin
    val listState = rememberLazyListState()
    // Utilizzo di derivedStateOf: si ricompone SOLO quando l'indice della riga cambia effettivamente!
    val activeLineIndex by remember(lyrics) {
        derivedStateOf {
            if (!lyrics.isSynced) 0
            else lyrics.lines.indexOfLast { it.timeMs <= currentPositionMs + 250L }.coerceAtLeast(0)
        }
    }

    LaunchedEffect(activeLineIndex) {
        if (lyrics.isSynced && activeLineIndex in lyrics.lines.indices) {
            listState.animateScrollToItem((activeLineIndex - 2).coerceAtLeast(0))
        }
    }
```

---

### Correzione 4.5: Risoluzione Sovrapposizione Contenuti Liste (Padding Insets)
- **File:** `app/src/main/java/com/gmusic/app/ui/album/AlbumScreen.kt` (Riga 169) & `ArtistScreen.kt` (Riga 178) & `SearchScreen.kt` (Riga 291)

#### ❌ PRIMA (`AlbumScreen.kt`):
```kotlin
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    )
```

#### ✅ DOPO:
```kotlin
    // Margine inferiore calcolato per lasciare spazio completo sia al MiniPlayer che alla NavigationBar
    val bottomContentPadding = 180.dp

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    )
```

---

### Correzione 4.6: Dialogo di Conferma Eliminazione Playlist
- **File:** `app/src/main/java/com/gmusic/app/ui/library/LibraryScreen.kt` (Righe 1080-1092)

#### ❌ PRIMA:
```kotlin
                    IconButton(
                        onClick = {
                            viewModel.deletePlaylist(playlistId)
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.library_delete_playlist),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
```

#### ✅ DOPO:
```kotlin
                    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.library_delete_playlist),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    if (showDeleteConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirmDialog = false },
                            title = { Text(stringResource(R.string.library_delete_playlist)) },
                            text = { Text(stringResource(R.string.library_delete_playlist_confirm_message)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showDeleteConfirmDialog = false
                                        viewModel.deletePlaylist(playlistId)
                                        onBack()
                                    }
                                ) {
                                    Text(
                                        stringResource(R.string.action_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                            }
                        )
                    }
```

---

### Correzione 4.7: Gestione Errori e Reset Stato di Caricamento nei ViewModel
- **File:** `app/src/main/java/com/gmusic/app/ui/home/HomeViewModel.kt` (Righe 164-203)

#### ❌ PRIMA:
```kotlin
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            musicRepository.syncRemoteLibraryIfLoggedIn()
            val freshSections = musicRepository.buildHomeFeedSections(page = 0)
            _uiState.update {
                it.copy(
                    sections = freshSections,
                    isRefreshing = false,
                    isLoading = false,
                    currentPage = 1,
                    canLoadMore = true
                )
            }
        }
    }
```

#### ✅ DOPO:
```kotlin
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            try {
                musicRepository.syncRemoteLibraryIfLoggedIn()
                val freshSections = musicRepository.buildHomeFeedSections(page = 0)
                _uiState.update {
                    it.copy(
                        sections = freshSections,
                        currentPage = 1,
                        canLoadMore = freshSections.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                // Previene lo stallo dell'animazione di refresh su errore di connessione
            } finally {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isLoading = false
                    )
                }
            }
        }
    }
```

---

### Correzione 4.8: Accessibilità e Controlli Player Etichettati per TalkBack
- **File:** `app/src/main/java/com/gmusic/app/ui/player/PlayerComponents.kt` (Righe 944, 967, 979)

#### ❌ PRIMA:
```kotlin
    FilledTonalIconButton(onClick = onSkipPrevious) {
        Icon(imageVector = Icons.Filled.SkipPrevious, contentDescription = null)
    }
    FilledIconButton(onClick = onTogglePlayPause) {
        Icon(
            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null
        )
    }
    FilledTonalIconButton(onClick = onSkipNext) {
        Icon(imageVector = Icons.Filled.SkipNext, contentDescription = null)
    }
```

#### ✅ DOPO:
```kotlin
    FilledTonalIconButton(onClick = onSkipPrevious) {
        Icon(
            imageVector = Icons.Filled.SkipPrevious,
            contentDescription = stringResource(R.string.player_skip_previous)
        )
    }
    FilledIconButton(onClick = onTogglePlayPause) {
        Icon(
            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(
                if (state.isPlaying) R.string.player_pause else R.string.player_play
            )
        )
    }
    FilledTonalIconButton(onClick = onSkipNext) {
        Icon(
            imageVector = Icons.Filled.SkipNext,
            contentDescription = stringResource(R.string.player_skip_next)
        )
    }
```

---

### Correzione 4.9: Risoluzione del Logo Splash Visibile sotto al Player
- **File:** `app/src/main/res/values/themes.xml`, `MainActivity.kt`, `GMusicRoot.kt`

#### ❌ PRIMA (`themes.xml`):
```xml
    <style name="Theme.GMusic" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@drawable/splash_background</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
```

#### ✅ DOPO (`themes.xml`):
```xml
    <style name="Theme.GMusic" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/black</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>

    <style name="Theme.GMusic.Splash" parent="Theme.GMusic">
        <item name="android:windowBackground">@drawable/splash_background</item>
    </style>
```

#### ✅ DOPO (`MainActivity.kt`):
```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.black)
        ...
```

#### ✅ DOPO (`GMusicRoot.kt`):
```kotlin
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        SharedTransitionLayout {
            AnimatedContent(...) { ... }
        }
    }
```


