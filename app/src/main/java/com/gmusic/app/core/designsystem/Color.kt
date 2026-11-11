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
val md_theme_dark_primary = Color(0xFF1B6B3A) // Stesso colore identico al Light (#1B6B3A)
val md_theme_dark_onPrimary = Color(0xFFFFFFFF) // Contrasto bianco puro ad alta leggibilità
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
