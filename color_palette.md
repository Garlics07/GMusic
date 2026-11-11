# 🎨 GMusic - Sistema di Configurazione Tema & Color Palette (Material 3)

Questo documento rappresenta la **Single Source of Truth (SSOT)** per i colori dell'applicazione **GMusic**.
Modificando i codici esadecimali (HEX) in questa tabella, puoi personalizzare istantaneamente l'aspetto sia del tema **Light** che del tema **Dark**.

---

## 1. Tabella Palette Colori Configurabile

I valori sono calibrati con il **Verde Brand unificato (`#1B6B3A`)** sia in modalità chiara che scura, eliminando la variante pastello a tonalità 80:
- **Verde Primario Unificato:** `#1B6B3A` è il colore del brand sia per `md_theme_light_primary` sia per `md_theme_dark_primary`.
- **Contrasto WCAG:** Su entrambi i temi, il testo/icona su primario (`onPrimary`) è impostato a `#FFFFFF` (bianco puro), garantendo un contrasto superiore a **4.5:1** rispetto al verde `#1B6B3A`.
- **Contenitori coerenti:** `primaryContainer` scuro profondo (`#0A4220`) in Dark Theme e chiaro luminoso (`#A3F5B8`) in Light Theme.

### ☀️ Light Theme (Modalità Chiara)

| Token M3 | Ruolo nel Design System | HEX Code | Descrizione UX & Componenti Coinvolti |
|---|---|---|---|
| `md_theme_light_primary` | Primario | `#1B6B3A` | Verde Brand originale: FAB, bottoni riempiti, slider attivi, indicatori selezionati. |
| `md_theme_light_onPrimary` | Testo su Primario | `#FFFFFF` | Testo e icone sovrapposti al colore primario (es. icona play su bottone primario). |
| `md_theme_light_primaryContainer` | Contenitore Primario | `#A3F5B8` | Sfondi evidenziati a basso contrasto, pillole filtro attive in modalità secondaria. |
| `md_theme_light_onPrimaryContainer` | Testo su Contenitore Primario | `#00210C` | Testo scuro ad alto contrasto sopra `primaryContainer`. |
| `md_theme_light_secondary` | Secondario | `#506352` | Accenti secondari desaturati, badge, icone informative. |
| `md_theme_light_onSecondary` | Testo su Secondario | `#FFFFFF` | Testo su colore secondario. |
| `md_theme_light_secondaryContainer` | Contenitore Secondario | `#D3E8D3` | Chips inattive evidenziate, card di supporto. |
| `md_theme_light_onSecondaryContainer` | Testo su Contenitore Secondario | `#0E1F12` | Testo su card secondarie. |
| `md_theme_light_tertiary` | Terziario | `#3A656E` | Sfumature per copertine mosaico, accenti complementari teal/ciano. |
| `md_theme_light_onTertiary` | Testo su Terziario | `#FFFFFF` | Testo su sfondi terziari. |
| `md_theme_light_tertiaryContainer` | Contenitore Terziario | `#BEEAF5` | Contenitori terziari per grafiche o gradienti. |
| `md_theme_light_onTertiaryContainer` | Testo su Cont. Terziario | `#001F25` | Testo su contenitori terziari. |
| `md_theme_light_background` | Sfondo Pagina | `#F6FBF3` | Sfondo generale dell'applicazione (Root, HomeScreen, SearchScreen). |
| `md_theme_light_onBackground` | Testo su Sfondo | `#181D18` | Testo principale titoli e intestazioni di pagina. |
| `md_theme_light_surface` | Superficie Base | `#F6FBF3` | Superficie base per card piatte e fogli modali. |
| `md_theme_light_onSurface` | Testo su Superficie | `#181D18` | Titoli dei brani, nomi artisti nelle righe. |
| `md_theme_light_surfaceVariant` | Superficie Variante | `#DCE5DB` | Placeholder copertine senza immagine, traccia inattiva slider. |
| `md_theme_light_onSurfaceVariant` | Testo Secondario | `#404942` | Sottotitoli, durata brani, bitrate, icone non selezionate. |
| `md_theme_light_surfaceContainerLowest` | Contenitore Minimo | `#FFFFFF` | Card bianche staccate con elevazione. |
| `md_theme_light_surfaceContainerLow` | Contenitore Basso | `#F0F5ED` | Card recenti e campi di testo non a fuoco. |
| `md_theme_light_surfaceContainer` | Contenitore Standard | `#EAEFE7` | Floating Navigation Bar, SearchBar container. |
| `md_theme_light_surfaceContainerHigh` | Contenitore Elevato | `#E4EAE2` | MiniPlayer bar, Bottom Sheet container. |
| `md_theme_light_surfaceContainerHighest` | Contenitore Massimo | `#DFE4DC` | Menu popup contestuali, dialoghi di conferma. |
| `md_theme_light_outline` | Bordi e Separatori | `#717971` | Bordi di bottoni outlined e divider attivi. |
| `md_theme_light_outlineVariant` | Bordi Secondari | `#C0C9BF` | Separatori sottili tra sezioni e brani. |
| `md_theme_light_error` | Errore | `#BA1A1A` | Notifica stream fallito, errori di rete, icona elimina. |
| `md_theme_light_onError` | Testo su Errore | `#FFFFFF` | Testo su banner o bottoni di errore. |
| `md_theme_light_errorContainer` | Contenitore Errore | `#FFDAD6` | Sfondo toast o snackbar di errore. |
| `md_theme_light_onErrorContainer` | Testo su Cont. Errore | `#410002` | Testo leggibile su contenitore errore. |

---

### 🌙 Dark Theme (Modalità Scura)

> [!NOTE]
> **Verde Unificato Identico:** `md_theme_dark_primary` utilizza esattamente `#1B6B3A`, identico alla versione Light. È stata rimossa la tonalità 80 pastello (`#7ED49C`). Per garantire leggibilità e contrasto perfetto, `onPrimary` è fissato sul bianco puro (`#FFFFFF`).

| Token M3 | Ruolo nel Design System | HEX Code | Descrizione UX & Componenti Coinvolti |
|---|---|---|---|
| `md_theme_dark_primary` | Primario | `#1B6B3A` | **Stesso Verde Brand Identico:** FAB, bottoni riempiti, slider d'onda, indicatori attivi. |
| `md_theme_dark_onPrimary` | Testo su Primario | `#FFFFFF` | **Bianco puro ad alto contrasto** per icone e testi su bottone verde `#1B6B3A`. |
| `md_theme_dark_primaryContainer` | Contenitore Primario | `#0A4220` | Verde scuro profondo per pillole, bottoni tonali e sfondi attivi. |
| `md_theme_dark_onPrimaryContainer` | Testo su Cont. Primario | `#A3F5B8` | Verde chiaro ad alta leggibilità sopra `primaryContainer`. |
| `md_theme_dark_secondary` | Secondario | `#8CB897` | Accento secondario salvia/verde chiaro per badge e dettagli secondari. |
| `md_theme_dark_onSecondary` | Testo su Secondario | `#00391A` | Testo su elementi secondari. |
| `md_theme_dark_secondaryContainer` | Contenitore Secondario | `#2D4432` | Sfondo chip secondarie selezionate. |
| `md_theme_dark_onSecondaryContainer` | Testo su Cont. Secondario | `#CCE7D0` | Testo chiaro su chip secondarie. |
| `md_theme_dark_tertiary` | Terziario | `#85CDDC` | Terzo accento armonico teal/azzurro per gradienti e copertine. |
| `md_theme_dark_onTertiary` | Testo su Terziario | `#00363F` | Testo su accento terziario. |
| `md_theme_dark_tertiaryContainer` | Contenitore Terziario | `#1A4750` | Contenitori scuri per card speciali e gradienti. |
| `md_theme_dark_onTertiaryContainer` | Testo su Cont. Terziario | `#BCEAF4` | Testo chiaro su contenitori terziari. |
| `md_theme_dark_background` | Sfondo Pagina | `#101411` | Sfondo scuro profondo per risparmio energetico e immersione visiva. |
| `md_theme_dark_onBackground` | Testo su Sfondo | `#E1E4DF` | Testo principale chiaro e riposante per titoli e intestazioni. |
| `md_theme_dark_surface` | Superficie Base | `#101411` | Superficie principale per le schermate e liste. |
| `md_theme_dark_onSurface` | Testo su Superficie | `#E1E4DF` | Titoli dei brani, testi elenchi. |
| `md_theme_dark_surfaceVariant` | Superficie Variante | `#374138` | Placeholder, traccia inattiva del player, divider. |
| `md_theme_dark_onSurfaceVariant` | Testo Secondario | `#B8C2B7` | Nomi artisti, durata brani, icone secondarie inattive. |
| `md_theme_dark_surfaceContainerLowest` | Contenitore Minimo | `#0A0D0B` | Livello più scuro per footer o sfondi speciali. |
| `md_theme_dark_surfaceContainerLow` | Contenitore Basso | `#171B18` | Sfondo TextField e SearchBar inattiva. |
| `md_theme_dark_surfaceContainer` | Contenitore Standard | `#1B201C` | Floating Navigation Bar, SearchBar attiva. |
| `md_theme_dark_surfaceContainerHigh` | Contenitore Elevato | `#252B26` | MiniPlayer bar, BottomSheet container. |
| `md_theme_dark_surfaceContainerHighest` | Contenitore Massimo | `#303631` | Dropdown menu contestuali, Dialoghi alert. |
| `md_theme_dark_outline` | Bordi e Separatori | `#818C81` | Bordi definiti per componenti interattivi. |
| `md_theme_dark_outlineVariant` | Bordi Secondari | `#374138` | Bordi leggeri e separatori tra brani. |
| `md_theme_dark_error` | Errore | `#FFB4AB` | Rosso chiaro ad alto contrasto per testi o icone d'errore. |
| `md_theme_dark_onError` | Testo su Errore | `#690005` | Testo scuro su bottone rosso errore. |
| `md_theme_dark_errorContainer` | Contenitore Errore | `#93000A` | Sfondo card di errore. |
| `md_theme_dark_onErrorContainer` | Testo su Cont. Errore | `#FFDAD6` | Testo chiaro su contenitore errore. |

---

## 2. Codice Kotlin Pronto all'Uso

### File: `Color.kt`
> **Percorso:** `app/src/main/java/com/gmusic/app/core/designsystem/Color.kt`

```kotlin
package com.gmusic.app.core.designsystem

import androidx.compose.ui.graphics.Color

// ==========================================
// 🎨 COLOR TOKENS DEFINITI DA COLOR_PALETTE.MD
// Brand Base: #1B6B3A (Unificato Light & Dark)
// ==========================================

// --- Light Theme Palette Tokens ---
val md_theme_light_primary = Color(0xFF1B6B3A)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFA3F5B8)
val md_theme_light_onPrimaryContainer = Color(0xFF00210C)

val md_theme_light_secondary = Color(0xFF506352)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFD3E8D3)
val md_theme_light_onSecondaryContainer = Color(0xFF0E1F12)

val md_theme_light_tertiary = Color(0xFF3A656E)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFBEEAF5)
val md_theme_light_onTertiaryContainer = Color(0xFF001F25)

val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)

val md_theme_light_background = Color(0xFFF6FBF3)
val md_theme_light_onBackground = Color(0xFF181D18)
val md_theme_light_surface = Color(0xFFF6FBF3)
val md_theme_light_onSurface = Color(0xFF181D18)
val md_theme_light_surfaceVariant = Color(0xFFDCE5DB)
val md_theme_light_onSurfaceVariant = Color(0xFF404942)

val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFF0F5ED)
val md_theme_light_surfaceContainer = Color(0xFFEAEFE7)
val md_theme_light_surfaceContainerHigh = Color(0xFFE4EAE2)
val md_theme_light_surfaceContainerHighest = Color(0xFFDFE4DC)

val md_theme_light_outline = Color(0xFF717971)
val md_theme_light_outlineVariant = Color(0xFFC0C9BF)

// --- Dark Theme Palette Tokens ---
val md_theme_dark_primary = Color(0xFF1B6B3A) // Stesso colore identico al Light
val md_theme_dark_onPrimary = Color(0xFFFFFFFF) // Contrasto bianco puro
val md_theme_dark_primaryContainer = Color(0xFF0A4220)
val md_theme_dark_onPrimaryContainer = Color(0xFFA3F5B8)

val md_theme_dark_secondary = Color(0xFF8CB897)
val md_theme_dark_onSecondary = Color(0xFF00391A)
val md_theme_dark_secondaryContainer = Color(0xFF2D4432)
val md_theme_dark_onSecondaryContainer = Color(0xFFCCE7D0)

val md_theme_dark_tertiary = Color(0xFF85CDDC)
val md_theme_dark_onTertiary = Color(0xFF00363F)
val md_theme_dark_tertiaryContainer = Color(0xFF1A4750)
val md_theme_dark_onTertiaryContainer = Color(0xFFBCEAF4)

val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_theme_dark_background = Color(0xFF101411)
val md_theme_dark_onBackground = Color(0xFFE1E4DF)
val md_theme_dark_surface = Color(0xFF101411)
val md_theme_dark_onSurface = Color(0xFFE1E4DF)
val md_theme_dark_surfaceVariant = Color(0xFF374138)
val md_theme_dark_onSurfaceVariant = Color(0xFFB8C2B7)

val md_theme_dark_surfaceContainerLowest = Color(0xFF0A0D0B)
val md_theme_dark_surfaceContainerLow = Color(0xFF171B18)
val md_theme_dark_surfaceContainer = Color(0xFF1B201C)
val md_theme_dark_surfaceContainerHigh = Color(0xFF252B26)
val md_theme_dark_surfaceContainerHighest = Color(0xFF303631)

val md_theme_dark_outline = Color(0xFF818C81)
val md_theme_dark_outlineVariant = Color(0xFF374138)

// --- Backward Compatibility Aliases ---
val BrandGreen = md_theme_light_primary
val BrandGreenBright = md_theme_dark_secondary
val BrandGreenContainer = md_theme_dark_primaryContainer
val OnBrandGreenContainer = md_theme_dark_onPrimaryContainer
val DarkBackground = md_theme_dark_background
val DarkSurface = md_theme_dark_surface
val DarkSurfaceVariant = md_theme_dark_surfaceVariant
val SpotifyGreen = Color(0xFF1DB954)
val SpotifyGreenBright = Color(0xFF1ED760)
val LightTextPrimary = Color(0xFFFFFFFF)
val LightTextSecondary = Color(0xFFB3B3B3)
val ErrorRed = Color(0xFFCF6679)
```

---

### File: `Theme.kt`
> **Percorso:** `app/src/main/java/com/gmusic/app/core/designsystem/Theme.kt`

```kotlin
package com.gmusic.app.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * ColorScheme Material 3 Light costruita sui token di color_palette.md
 */
val GMusicLightColorScheme: ColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_surfaceContainerLow,
    surfaceContainer = md_theme_light_surfaceContainer,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_surfaceContainerHighest,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer
)

/**
 * ColorScheme Material 3 Dark costruita sui token di color_palette.md
 */
val GMusicDarkColorScheme: ColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_surfaceContainerLow,
    surfaceContainer = md_theme_dark_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer
)

/**
 * Entry-point del tema Compose per l'intera app GMusic.
 * Supporta:
 * 1. Switch automatico o manuale tra Dark e Light mode.
 * 2. Dynamic Color (Material You) su Android 12+ (API 31+).
 */
@Composable
fun GMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GMusicDarkColorScheme
        else -> GMusicLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GMusicTypography,
        shapes = GMusicShapes,
        content = content
    )
}
```

---

## 3. Guida all'Integrazione & Aggiornamento dei Colori

1. **Come cambiare un colore:**
   Basta aprire `color_palette.md`, scegliere la riga del token desiderato, copiare il nuovo valore esadecimale e aggiornare la costante corrispondente in `Color.kt`.
2. **Supporto automatico Edge-to-Edge:**
   In `MainActivity.kt`, `enableEdgeToEdge()` sincronizza la status bar e la navigation bar con il tema attivo.
