package com.gmusic.app.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Schema colori Dark fisso 100% Material Design 3 con verde #1B6B3A primario.
 * Obbligato per tutte le pagine dell'applicazione (escluso il player a schermo intero che adotta
 * dinamicamente la palette dell'artwork del brano in riproduzione).
 */
val GMusicDarkColorScheme: ColorScheme = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreen,
    onPrimaryContainer = Color.White,
    secondary = BrandGreenBright,
    onSecondary = Color.White,
    secondaryContainer = BrandGreen,
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF7ED49C),
    onTertiary = Color(0xFF003919),
    tertiaryContainer = Color(0xFF1B4E2E),
    onTertiaryContainer = Color(0xFFA5F5C3),
    background = Color(0xFF121412),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF121412),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF282E29),
    onSurfaceVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color(0xFF0C0E0C),
    surfaceContainerLow = Color(0xFF161916),
    surfaceContainer = Color(0xFF1A1D1A),
    surfaceContainerHigh = Color(0xFF242824),
    surfaceContainerHighest = Color(0xFF2F332F),
    outline = Color(0xFF8A9389),
    outlineVariant = Color(0xFF3F4941),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun GMusicTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    seedPrimaryColor: Color? = null,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GMusicDarkColorScheme,
        typography = GMusicTypography,
        shapes = GMusicShapes,
        content = content
    )
}
