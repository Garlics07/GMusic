package com.gmusic.app.core.designsystem

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/**
 * Utility per estrarre il seed color dalla copertina
 * e generare una ColorScheme Dark 100% conforme alle tonalità M3 (Tonal Roles HCT/Lab).
 */
object MaterialYouPaletteGenerator {

    fun extractSeedColor(bitmap: Bitmap): Color {
        val palette = Palette.from(bitmap).generate()
        val swatch = palette.vibrantSwatch
            ?: palette.dominantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.mutedSwatch

        return swatch?.rgb?.let { Color(it) } ?: Color(0xFF6750A4)
    }

    fun createHarmonizedDarkColorScheme(seedColor: Color): ColorScheme {
        val seedArgb = seedColor.toArgb()
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seedArgb, hsl)
        val h = hsl[0]
        val s = hsl[1].coerceIn(0.25f, 0.85f)

        fun hslColor(luminance: Float, saturationFactor: Float = 1.0f): Color {
            val resHsl = floatArrayOf(h, (s * saturationFactor).coerceIn(0f, 1f), luminance.coerceIn(0f, 1f))
            return Color(ColorUtils.HSLToColor(resHsl))
        }

        val primary = hslColor(luminance = 0.80f, saturationFactor = 0.95f)
        val onPrimary = hslColor(luminance = 0.20f, saturationFactor = 1.0f)
        val primaryContainer = hslColor(luminance = 0.30f, saturationFactor = 0.9f)
        val onPrimaryContainer = hslColor(luminance = 0.90f)

        val secondary = hslColor(luminance = 0.78f, saturationFactor = 0.50f)
        val onSecondary = hslColor(luminance = 0.20f, saturationFactor = 0.50f)
        val secondaryContainer = hslColor(luminance = 0.28f, saturationFactor = 0.40f)
        val onSecondaryContainer = hslColor(luminance = 0.88f)

        val tertiary = hslColor(luminance = 0.82f, saturationFactor = 0.60f)
        val onTertiary = hslColor(luminance = 0.20f, saturationFactor = 0.60f)

        val background = hslColor(luminance = 0.06f, saturationFactor = 0.15f)
        val onBackground = hslColor(luminance = 0.90f, saturationFactor = 0.05f)
        val surface = hslColor(luminance = 0.07f, saturationFactor = 0.18f)
        val onSurface = hslColor(luminance = 0.90f, saturationFactor = 0.05f)

        val surfaceContainerLowest = hslColor(luminance = 0.04f, saturationFactor = 0.15f)
        val surfaceContainerLow = hslColor(luminance = 0.08f, saturationFactor = 0.18f)
        val surfaceContainer = hslColor(luminance = 0.11f, saturationFactor = 0.20f)
        val surfaceContainerHigh = hslColor(luminance = 0.15f, saturationFactor = 0.22f)
        val surfaceContainerHighest = hslColor(luminance = 0.19f, saturationFactor = 0.25f)

        val surfaceVariant = hslColor(luminance = 0.26f, saturationFactor = 0.25f)
        val onSurfaceVariant = hslColor(luminance = 0.78f, saturationFactor = 0.15f)
        val outline = hslColor(luminance = 0.56f, saturationFactor = 0.20f)
        val outlineVariant = hslColor(luminance = 0.30f, saturationFactor = 0.20f)

        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = outline,
            outlineVariant = outlineVariant
        )
    }
}

@Composable
fun rememberHarmonizedPlayerColorScheme(seedColor: Color?): ColorScheme {
    return remember(seedColor) {
        if (seedColor != null) {
            MaterialYouPaletteGenerator.createHarmonizedDarkColorScheme(seedColor)
        } else {
            MaterialYouPaletteGenerator.createHarmonizedDarkColorScheme(Color(0xFF6750A4))
        }
    }
}
