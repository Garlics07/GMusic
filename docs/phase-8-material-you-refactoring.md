# Fase 8 — Refactoring UI/UX Material Design 3 (Material You)

> Questo documento specifica l'architettura, le linee guida di design e le convenzioni implementative per la transizione di **GMusic** a un'esperienza **100% conforme a Material Design 3 (Material You)**, con palette dinamiche, tipografia moderna (Poppins + JetBrains Mono) e controlli avanzati.

---

## 1. Visione e Obiettivi del Refactoring

- **Abbandono dell'ibrido proprietario / Spotify-dark**: eliminazione dei neri assoluti (`#000000`, `#0E0E0E`) e del verde Spotify hardcoded (`#1DB954`).
- **Adozione dei Surface Tiers nativi M3**: utilizzo rigoroso dei ruoli tonali `surface`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`.
- **Tipografia di Carattere**:
  - **Poppins**: font principale geometrico sans-serif per titoli, intestazioni, bottoni e testi di lettura.
  - **JetBrains Mono**: font monospaziato per timestamp di riproduzione, codici di tempo, tag di qualità audio (es. `320 KBPS`, `FLAC`, frequenze) ed elementi numerici, prevenendo il layout jitter orizzontale durante il playback.
- **Micro-interazioni da Pixel Experience**:
  - `WavySlider` (seek bar sinusoidale animata in riproduzione, che si appiattisce in linea retta in pausa).
  - Forme `ExtraLarge` a 28dp per copertine e schede principali, e `CircleShape` per i controlli primari a pillola.

---

## 2. Architettura del Tema a Due Livelli

```
┌────────────────────────────────────────────────────────┐
│ 1. App Scope (MainActivity -> GMusicTheme)             │
│    • Android 12+ (API 31+): dynamicDarkColorScheme()   │
│      o dynamicLightColorScheme() dallo sfondo sistema  │
│    • Pre-Android 12: M3 DarkColorScheme bilanciata     │
│    • Zero interferenze dal colore della cover          │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│ 2. Expanded Player Scope (Sub-MaterialTheme Locale)    │
│    • Estrazione Seed Color dalla cover (AndroidX       │
│      Palette: vibrantSwatch / dominantSwatch)          │
│    • M3 Tonal Mapping (Tone 4..90): genera uno schema  │
│      scuro armonizzato con HSL/ColorUtils              │
│    • Applicazione locale:                              │
│      MaterialTheme(colorScheme = playerColorScheme)    │
│    • Isola i colori del brano all'interno del player   │
└────────────────────────────────────────────────────────┘
```

---

## 3. Specifiche Tipografiche (Poppins + JetBrains Mono)

Configurato in `core/designsystem/Type.kt` tramite Downloadable Fonts (`androidx.compose.ui:ui-text-google-fonts`):

| Ruolo M3 | Font Family | Peso | Utilizzo in GMusic |
|---|---|---|---|
| `headlineLarge` / `Medium` | **Poppins** | ExtraBold / Bold | Intestazioni principali di Home, Libreria, Esplora |
| `headlineSmall` | **Poppins** | Bold | Titolo del brano nell'Expanded Player |
| `titleLarge` / `Medium` | **Poppins** | Bold / SemiBold | Intestazioni sezioni, nome artista, schede album |
| `bodyLarge` / `Medium` | **Poppins** | Regular / Medium | Testi di lettura, biografie, descrizioni playlist |
| `labelLarge` | **Poppins** | Medium | Bottoni primari, chip di categoria, schede d'azione |
| `labelMedium` | **JetBrains Mono** | Medium | Timestamp correnti e totali (`02:45 / 04:12`) |
| `labelSmall` | **JetBrains Mono** | Normal | Badge bitrate/formato (`320 KBPS AAC`, `HQ`), metriche |

---

## 4. Specifiche Componenti Chiave

### A. WavySlider (Seek Bar Ondulata)
- **File**: `ui/player/components/WavySlider.kt`
- **Comportamento Dinamico**:
  - Quando `isPlaying == true`: fase orizzontale sinusoidale calcolata con `rememberInfiniteTransition` ($y = y_0 + A \cdot \sin(kx - \omega t)$) con lunghezza d'onda $\lambda \approx 26\,\text{dp}$.
  - Quando `isPlaying == false`: ampiezza $A$ animata a $0$ via `animateFloatAsState` in 450ms, distendendo l'onda in una retta.
  - Cursore/Thumb a cerchio centrato con supporto fluido a tocco e `detectDragGestures`.
  - Colori: `activeColor = MaterialTheme.colorScheme.primary`, `inactiveColor = MaterialTheme.colorScheme.surfaceVariant`.

### B. Mini-Player Elevato
- **Container**: `Surface` M3 con `shape = MaterialTheme.shapes.large` (16dp) e `color = MaterialTheme.colorScheme.surfaceContainer`.
- **Controlli**: `FilledIconButton` per Play/Pause con tonalità M3, swipe orizzontale per traccia precedente/successiva e swipe verso il basso per chiusura/stop.

### C. Expanded Full-Screen Player
- **Forma Copertina**: `ElevatedCard` con `shape = MaterialTheme.shapes.extraLarge` (28dp).
- **Pulsante Play/Pause**: `FilledIconButton` grande a cerchio (`CircleShape`, 72dp) su `MaterialTheme.colorScheme.primary` e icona `onPrimary`.
- **Pulsanti Secondari**: `FilledTonalIconButton` a cerchio per Next e Previous (52dp).
- **Azioni Rapide**: `AssistChip` a pillola per "Testo" (Lyrics) e "In coda" (Queue) con `surfaceContainerHigh`.

---

## 5. Regole per il Codice (Checklist M3)

1. **Mai colori hardcoded**: vietati `Color.White`, `Color.Black`, `0xFF1DB954` nei componenti UI; impiegare unicamente `MaterialTheme.colorScheme.*`.
2. **Nessun font hardcoded**: usare unicamente `MaterialTheme.typography.*` o le famiglie centralizzate `PoppinsFamily` e `JetBrainsMonoFamily`.
3. **Forme M3 semantiche**: ricorrere a `MaterialTheme.shapes.*` (`medium` a 12dp, `large` a 16dp, `extraLarge` a 28dp).
4. **Isolamento del tema Player**: non passare `seedPrimaryColor` a tutta l'app; circoscrivere la tavolozza generata dall'album a `ExpandedPlayerScreen` tramite un `sub-MaterialTheme`.
