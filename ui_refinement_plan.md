# 🎨 GMusic - Piano di Raffinamento UI/UX & Bugfix (Dark & Light Theme)

Questo documento formalizza e descrive nel dettaglio tutti gli interventi di miglioramento estetico, correzione logico-visiva e ottimizzazione dell'esperienza utente richiesti per la prossima iterazione dell'applicazione, calibrati prioritariamente per la **modalità Scura (Dark)** e armonizzati per la **modalità Chiara (Light)**.

---

## 📌 Indice degli Interventi

1. [Rimozione del Gradiente Superiore nella Home](#1-rimozione-del-gradiente-superiore-nella-home)
2. [Sfondi Categorie della Libreria a Colore Pieno](#2-sfondi-categorie-della-libreria-a-colore-pieno)
3. [Risoluzione Bug Navigazione Categorie (Apertura/Chiusura Rapida)](#3-risoluzione-bug-navigazione-categorie-aperturachiusura-rapida)
4. [Impostazioni: Rimozione Testi Verde Scuro & Bottoni ad Alto Contrasto](#4-impostazioni-rimozione-testi-verde-scuro--bottoni-ad-alto-contrasto)
5. [Snellimento Tasto Logout: "Esci dall'account" $\rightarrow$ "Esci"](#5-snellimento-tasto-logout-esci-dallaccount--esci)
6. [Bottone Secondario (Album, Artisti, Playlist): Bordo & Testo Bianco](#6-bottone-secondario-album-artisti-playlist-bordo--testo-bianco)
7. [Matrice di Impatto sui File](#7-matrice-di-impatto-sui-file)

---

## 1. Rimozione del Gradiente Superiore nella Home

### 🔍 Il Problema
Attualmente, nella sezione superiore di `HomeScreen`, è presente un box con sfumatura verticale generata dinamicamente da `Brush.verticalGradient`:
- In **Dark Mode**, la sfumatura introduce una transizione verso il verde scuro (`#1B6B3A` ad alpha variabile) che appesantisce la testata e crea una divisione visiva artificiosa con la griglia 3x2.
- In **Light Mode**, il gradiente crea un contrasto variabile che altera la leggibilità dell'icona profilo Google e del testo di benvenuto.

### 💡 La Soluzione
Rimuovere il `Brush.verticalGradient` e l'animazione `animateColorAsState` legata a `homeTopTint`. La testata adotterà lo sfondo solido nativo dell'app (`MaterialTheme.colorScheme.background`), rendendo l'interfaccia minimale, pulita e a filo con il resto della schermata.

### 🛠️ Modifiche al Codice (`HomeScreen.kt`)

**File:** `app/src/main/java/com/gmusic/app/ui/home/HomeScreen.kt`

#### Prima:
```kotlin
// Righe 140-146
val defaultTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
val targetTint = defaultTint
val animatedTopTint by animateColorAsState(
    targetValue = targetTint,
    animationSpec = tween(durationMillis = 650),
    label = "homeTopTint"
)

// Righe 198-209
Box(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = minHeaderHeight)
        .background(
            Brush.verticalGradient(
                0.0f to animatedTopTint.copy(alpha = 0.60f),
                0.35f to animatedTopTint.copy(alpha = 0.28f),
                0.70f to animatedTopTint.copy(alpha = 0.08f),
                1.0f to Color.Transparent
            )
        )
        .padding(bottom = 12.dp)
)
```

#### Dopo:
```kotlin
// Rimosse le variabili di animazione colore non più necessarie (animatedTopTint, targetTint, defaultTint)

// Box superiore pulito con sfondo a colore solido di sistema
Box(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = minHeaderHeight)
        .background(MaterialTheme.colorScheme.background)
        .padding(bottom = 12.dp)
)
```

---

## 2. Sfondi Categorie della Libreria a Colore Pieno

### 🔍 Il Problema
Attualmente, nel componente `LibraryHubCard` di `LibraryScreen.kt`, il riquadro dell'icona (56dp) utilizza un gradiente lineare bicolore (`Brush.linearGradient` con `primary` e `tertiary`). Inoltre la superficie usa `surfaceContainer`. L'effetto gradiente appare disallineato rispetto alla filosofia minimalista e con l'uso del verde brand unificato `#1B6B3A`.

### 💡 Proposta di Soluzione (Due Alternative a Colore Pieno)

#### Opzione A: Verde Brand Unificato Pieno (Consigliata per Coerenza)
Tutti i riquadri delle 5 categorie (Preferiti, Playlist, Artisti, Album, Scaricate) adottano il **Verde Brand Pieno** (`MaterialTheme.colorScheme.primary`, ovvero `#1B6B3A` sia in Dark che in Light) con l'icona in **Bianco Puro** (`MaterialTheme.colorScheme.onPrimary`).

- **Dark Mode:** Riquadro verde profondo `#1B6B3A`, icona bianca ad alto contrasto.
- **Light Mode:** Riquadro verde `#1B6B3A`, icona bianca ad alto contrasto.

#### Opzione B: Riquadri a Colore Pieno Tematico (Stile Spotify Hub)
Ogni categoria possiede un colore pieno identitario senza alcuna sfumatura:
- **Canzoni preferite:** Viola/Porpora solido (`#6200EE` in Dark, `#7C4DFF` in Light) con icona cuore bianco.
- **Playlist:** Verde Brand pieno (`#1B6B3A`) con icona note/coda bianca.
- **Artisti:** Blu Zaffiro solido (`#1976D2`) con icona persona bianca.
- **Album:** Ambra/Arancio bruciato solido (`#D84315`) con icona vinile/disco bianca.
- **Canzoni scaricate:** Teal/Ciano scuro solido (`#00695C`) con icona freccia download bianca.

### 🛠️ Modifiche al Codice (`LibraryScreen.kt`)

**File:** `app/src/main/java/com/gmusic/app/ui/library/LibraryScreen.kt`

#### Modifica a `LibraryHubCard`:
```kotlin
@Composable
fun LibraryHubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.primary, // Colore pieno anziché gradientColors
    iconTint: Color = MaterialTheme.colorScheme.onPrimary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(containerColor), // Colore PIENO senza gradiente
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
```

---

## 3. Risoluzione Bug Navigazione Categorie (Apertura/Chiusura Rapida)

### 🔍 Il Problema: Analisi della Race Condition
L'utente segnala: *"se apro e chiudo velocemente le categorie mi mostra la precedente"*.

#### Causa Tecnica:
Nel file `GMusicRoot.kt`:
1. Quando l'utente tocca rapidamente una categoria (es. *"Canzoni preferite"*), viene eseguito `navController.navigate("liked_songs")`.
2. Se l'utente esegue un doppio tocco rapido, o tocca velocemente un'altra card (es. *"Playlist"*) prima del completamento dell'animazione di transizione, `NavHost` accoda entrambe le destinazioni nello stack di navigazione:
   `[TopLevelDestination.LIBRARY, liked_songs, library_playlists]`
3. Quando l'utente preme il pulsante Indietro da `library_playlists`, il listener `onBack = { navController.popBackStack() }` effettua un pop generico dell'ultimo elemento.
4. **Risultato del bug:** Il pop rimuove `library_playlists`, scoprendo immediatamente `liked_songs` (la categoria precedente toccata per errore o in rapida sequenza), invece di tornare alla radice della Libreria!

### 💡 La Soluzione Architetturale
1. **Navigazione con `launchSingleTop = true` e Route Guard:** Assicurarsi che da `LibraryScreen` si possa navigare solo se la destinazione corrente è effettivamente la radice della libreria (`TopLevelDestination.LIBRARY.route`), prevenendo aperture multiple simultanee.
2. **Deterministic Pop-To-Root:** Nelle sotto-schermate della libreria (`LikedSongsScreen`, `LibraryPlaylistsScreen`, `LibraryArtistsScreen`, `LibraryAlbumsScreen`, `DownloadedSongsScreen`), il tasto indietro non deve fare un generico `popBackStack()`, ma `navController.popBackStack(TopLevelDestination.LIBRARY.route, inclusive = false)`. In questo modo, l'app torna **SEMPRE e direttamente alla root della libreria**, svuotando qualsiasi sotto-pagina intermedia rimasta accodata.

### 🛠️ Modifiche al Codice (`GMusicRoot.kt`)

**File:** `app/src/main/java/com/gmusic/app/ui/root/GMusicRoot.kt`

#### Helper di Navigazione Sicura:
```kotlin
fun NavHostController.navigateFromLibrary(route: String) {
    if (currentBackStackEntry?.destination?.route == TopLevelDestination.LIBRARY.route) {
        navigate(route) {
            launchSingleTop = true
        }
    }
}

fun NavHostController.popBackToLibrary() {
    popBackStack(TopLevelDestination.LIBRARY.route, inclusive = false)
}
```

#### Applicazione nei composable:
```kotlin
composable(TopLevelDestination.LIBRARY.route) {
    LibraryScreen(
        onOpenLikedSongs = { navController.navigateFromLibrary("liked_songs") },
        onOpenPlaylists = { navController.navigateFromLibrary("library_playlists") },
        onOpenArtists = { navController.navigateFromLibrary("library_artists") },
        onOpenAlbums = { navController.navigateFromLibrary("library_albums") },
        onOpenDownloadedSongs = { navController.navigateFromLibrary("downloaded_songs") },
        onOpenPlaylist = { playlistId ->
            navController.navigateFromLibrary("playlist/${Uri.encode(playlistId)}")
        },
        onOpenAlbum = navigateToAlbum,
        onPlayTrack = { track, queue -> playerViewModel.playTrack(track, queue) },
        onOpenArtist = navigateToArtist,
        onOpenHistory = { navController.navigateFromLibrary("history") },
        // ...
    )
}

composable("library_playlists") {
    LibraryPlaylistsScreen(
        onBack = { navController.popBackToLibrary() }, // Torna direttamente alla libreria senza residui
        onOpenPlaylist = { playlistId ->
            navController.navigate("playlist/${Uri.encode(playlistId)}")
        },
        // ...
    )
}

composable("liked_songs") {
    LikedSongsScreen(
        onBack = { navController.popBackToLibrary() },
        // ...
    )
}

composable("library_artists") {
    LibraryArtistsScreen(
        onBack = { navController.popBackToLibrary() },
        // ...
    )
}

composable("library_albums") {
    LibraryAlbumsScreen(
        onBack = { navController.popBackToLibrary() },
        // ...
    )
}

composable("downloaded_songs") {
    DownloadedSongsScreen(
        onBack = { navController.popBackToLibrary() },
        // ...
    )
}
```

---

## 4. Impostazioni: Rimozione Testi Verde Scuro & Bottoni ad Alto Contrasto

### 🔍 Il Problema
In `SettingsModalBottomSheet.kt`:
1. Molti titoli di sezione, etichette numeriche e intestazioni sono stilizzati con `color = MaterialTheme.colorScheme.primary`.
2. Poiché la nostra palette Material 3 unificata imposta `primary = #1B6B3A` (un verde scuro e saturo):
   - In **Dark Mode** (su sfondo grigio/nero `#101411` o `#1B201C`), il testo verde scuro ha un contrasto insufficiente (< 2:1), risultando poco leggibile e "fangoso".
   - Nei bottoni con outline (`OutlinedButton`), il testo e il bordo prendono di default `primary`, rendendo sia il bordo che il testo verde scuro.
3. In **Light Mode**, pur essendo leggibile, crea una frammentazione visiva con troppe sfumature di verde tra testi, switch e slider.

### 💡 La Soluzione
1. **Titoli e Label:** Sostituire `color = MaterialTheme.colorScheme.primary` con `color = MaterialTheme.colorScheme.onSurface` (Bianco in Dark, Grafite scuro in Light) o con il colore di accento luminoso `MaterialTheme.colorScheme.secondary` (in Dark è il verde salvia chiaro `#8CB897`, ad altissimo contrasto).
2. **Bottoni:**
   - Negli `OutlinedButton` delle impostazioni (es. *Istanze personalizzate*, *Elimina download*), forzare `contentColor = MaterialTheme.colorScheme.onSurface` e `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`.
   - Nei bottoni primari riempiti (`Button`), il contenitore è verde `#1B6B3A` e il testo/icona è forzato a **Bianco puro** (`Color.White`).

### 🛠️ Modifiche al Codice (`SettingsModalBottomSheet.kt`)

**File:** `app/src/main/java/com/gmusic/app/ui/settings/SettingsModalBottomSheet.kt`

#### Prima:
```kotlin
// Righe 477, 532, 595, 642, 697, 720
Text(text = "...", color = MaterialTheme.colorScheme.primary)

// Righe 492-496, 664-668
OutlinedButton(
    onClick = { showInstancesDialog = true },
    modifier = Modifier.fillMaxWidth()
) {
    Text(stringResource(R.string.settings_instances_configure)) // Testo verde scuro!
}
```

#### Dopo:
```kotlin
// Titoli e indicatori a contrasto ideale
Text(
    text = "...",
    color = MaterialTheme.colorScheme.onSurface, // Leggibile, netto e pulito sia in Dark che in Light
    fontWeight = FontWeight.Bold
)

// Bottoni Outlined con bordo e testo chiari (nessun verde scuro)
OutlinedButton(
    onClick = { showInstancesDialog = true },
    modifier = Modifier.fillMaxWidth(),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    colors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface
    )
) {
    Text(stringResource(R.string.settings_instances_configure))
}
```

---

## 5. Snellimento Tasto Logout: "Esci dall'account" $\rightarrow$ "Esci"

### 🔍 Il Problema
Nella testata dell'account Google (`SettingsModalBottomSheet.kt`), il bottone di logout attualmente visualizza la stringa `"Esci dall'account"` (italiano), che occupa uno spazio eccessivo orizzontalmente costringendo il nome utente e l'avatar a comprimersi.

### 💡 La Soluzione
1. Cambiare la stringa in `"Esci"` in italiano.
2. Adattare in modo sintetico anche le altre lingue supportate (`"Sign out"`, `"Salir"`, `"Quitter"`).
3. Dare al bottone uno stile moderno: `OutlinedButton` con testo e bordo coerenti (`onSurface` / `outlineVariant`).

### 🛠️ Modifiche alle Risorse Stringhe

#### `res/values-it/strings.xml`:
```xml
<!-- Prima -->
<string name="settings_logout">Esci dall\'account</string>
<!-- Dopo -->
<string name="settings_logout">Esci</string>
```

#### `res/values/strings.xml` (Default / EN):
```xml
<!-- Prima -->
<string name="settings_logout">Sign Out</string>
<!-- Dopo -->
<string name="settings_logout">Sign out</string>
```

#### `res/values-es/strings.xml`:
```xml
<!-- Prima -->
<string name="settings_logout">Cerrar sesión</string>
<!-- Dopo -->
<string name="settings_logout">Salir</string>
```

#### `res/values-fr/strings.xml`:
```xml
<!-- Prima -->
<string name="settings_logout">Déconnexion</string>
<!-- Dopo -->
<string name="settings_logout">Quitter</string>
```

---

## 6. Bottone Secondario (Album, Artisti, Playlist): Bordo & Testo Bianco

### 🔍 Il Problema
Nelle schermate di dettaglio:
- `AlbumScreen.kt`: Bottone "Casuale" (Shuffle)
- `ArtistScreen.kt`: Bottone "Segui" / "Seguito" (Follow)
- `PlaylistDetailScreen` (in `LibraryScreen.kt`): Bottone "Scarica playlist" o "Casuale"
- `LikedSongsScreen` (in `LibraryScreen.kt`): Bottone "Casuale"
- `DownloadedSongsScreen` (in `LibraryScreen.kt`): Bottone "Casuale"
- `HistoryScreen` (in `LibraryScreen.kt`): Bottone "Casuale"

Attualmente tutti questi schermi usano `FilledTonalButton` per l'azione secondaria. In modalità scura, `FilledTonalButton` applica un contenitore verde-grigio scuro (`#2D4432`) che toglie risalto all'azione e non crea un vero stacco visivo rispetto allo sfondo.

### 💡 La Soluzione: Outlined Button con Bordo e Testo Bianco (Dark) e Adattivo (Light)
Sostituire `FilledTonalButton` con un `OutlinedButton` dedicato che offre:
- **In Dark Mode:** Bordo bianco solido a 1dp (`Color.White`) e testo/icona **Bianco puro** (`Color.White`). L'effetto su sfondo nero AMOLED o scuro profondo è estremamente elegante, nitido e in perfetto stile Spotify/Apple Music.
- **In Light Mode:** Bordo e testo adattati con contrasto garantito (`border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)`, testo `MaterialTheme.colorScheme.onSurface`), per non risultare invisibile su sfondo chiaro.

### 🛠️ Implementazione Tecnica

Possiamo definire un comodo helper o token riutilizzabile:

```kotlin
@Composable
fun SecondaryMediaActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val borderColor = if (isDark) Color.White else MaterialTheme.colorScheme.outline
    val contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = CircleShape, // oppure RoundedCornerShape(24.dp)
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor
        )
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}
```

#### Esempio Prima/Dopo in `AlbumScreen.kt`:
```kotlin
// Prima:
FilledTonalButton(
    onClick = {
        val shuffled = currentDetail.tracks.shuffled()
        onPlayTrack(shuffled.first(), shuffled)
    },
    modifier = Modifier.weight(1f)
) {
    Icon(Icons.Filled.Shuffle, contentDescription = null)
    Spacer(Modifier.width(8.dp))
    Text(stringResource(R.string.action_shuffle))
}

// Dopo:
val isDark = isSystemInDarkTheme()
OutlinedButton(
    onClick = {
        val shuffled = currentDetail.tracks.shuffled()
        onPlayTrack(shuffled.first(), shuffled)
    },
    modifier = Modifier.weight(1f),
    border = BorderStroke(1.dp, if (isDark) Color.White else MaterialTheme.colorScheme.outline),
    colors = ButtonDefaults.outlinedButtonColors(
        contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    )
) {
    Icon(
        imageVector = Icons.Filled.Shuffle,
        contentDescription = null,
        tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.width(8.dp))
    Text(
        text = stringResource(R.string.action_shuffle),
        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    )
}
```

#### Esempio in `ArtistScreen.kt`:
```kotlin
// Tasto Segui/Seguito:
val isDark = isSystemInDarkTheme()
OutlinedButton(
    onClick = { viewModel.toggleSaveArtist(current) },
    modifier = Modifier.weight(1f),
    border = BorderStroke(1.dp, if (isDark) Color.White else MaterialTheme.colorScheme.outline),
    colors = ButtonDefaults.outlinedButtonColors(
        contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    )
) {
    Icon(
        imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = null,
        tint = if (isSaved) MaterialTheme.colorScheme.primary else (if (isDark) Color.White else MaterialTheme.colorScheme.onSurface)
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
        text = stringResource(if (isSaved) R.string.artist_following else R.string.artist_follow),
        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    )
}
```

---

## 7. Matrice di Impatto sui File

| Componente / Schermata | File da Modificare | Intervento Principale |
|---|---|---|
| **Home Screen** | `ui/home/HomeScreen.kt` | Rimosso il gradiente verticale superiore, sfondo solido a `background`. |
| **Libreria (Hub)** | `ui/library/LibraryScreen.kt` | `LibraryHubCard`: riquadri icone a colore pieno (verde brand `#1B6B3A` o solido differenziato). |
| **Navigazione Libreria** | `ui/root/GMusicRoot.kt` | Implementati `navigateFromLibrary` (con `launchSingleTop = true`) e `popBackToLibrary` per eliminare il bug di navigazione rapida. |
| **Libreria (Sotto-schermate)** | `ui/library/LibraryScreen.kt` | Sostituiti i `FilledTonalButton` secondari con `OutlinedButton` (bordo/testo bianco in Dark). |
| **Dettaglio Album** | `ui/album/AlbumScreen.kt` | Bottone secondario "Casuale" convertito in `OutlinedButton` bianco. |
| **Dettaglio Artista** | `ui/artist/ArtistScreen.kt` | Bottone secondario "Segui" convertito in `OutlinedButton` bianco. |
| **Modal Impostazioni** | `ui/settings/SettingsModalBottomSheet.kt` | Rimossi tutti i testi verdi scuri (`#1B6B3A`), bottoni outlined con contrasto alto. |
| **Stringhe Multilingua** | `res/values*/strings.xml` | Stringa `settings_logout` aggiornata a *"Esci"* / *"Sign out"* / *"Salir"* / *"Quitter"*. |

---

## 🚀 Prossimi Passi

1. **Scelta preferita sulle categorie della libreria:** Confermare tra l'**Opzione A** (Tutte con Verde Brand Pieno `#1B6B3A` e icone bianche) o l'**Opzione B** (Colori solidi differenziati per categoria).
2. **Procedere all'applicazione diretta delle modifiche** nel codice Kotlin e nelle risorse XML quando desiderato.

