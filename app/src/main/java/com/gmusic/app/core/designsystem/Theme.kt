package com.gmusic.app.core.designsystem

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3. ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DefaultDarkColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    primaryContainer = SpotifyGreenBright,
    onPrimaryContainer = Color.Black,
    secondary = SpotifyGreenBright,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = LightTextPrimary,
    surface = DarkSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    error = ErrorRed
)

private val DefaultLightColorScheme = lightColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F8E3),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF14833B),
    onSecondary = Color.White,
    background = Color(0xFFF9F9F9),
    onBackground = Color(0xFF121212),
    surface = Color.White,
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF555555)
)

@Composable
fun GMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedPrimaryColor: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme: ColorScheme = when {
        seedPrimaryColor != null -> {
            DefaultDarkColorScheme.copy(
                primary = seedPrimaryColor,
                primaryContainer = seedPrimaryColor.copy(alpha = 0.25f)
            )
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DefaultDarkColorScheme
        else -> DefaultLightColorScheme
    }

    val animatedPrimary by animateColorAsState(
        targetValue = baseScheme.primary,
        animationSpec = tween(durationMillis = 500),
        label = "primaryColorAnim"
    )
    val animatedPrimaryContainer by animateColorAsState(
        targetValue = baseScheme.primaryContainer,
        animationSpec = tween(durationMillis = 500),
        label = "primaryContainerAnim"
    )

    val finalScheme = baseScheme.copy(
        primary = animatedPrimary,
        primaryContainer = animatedPrimaryContainer
    )

    MaterialTheme(
        colorScheme = finalScheme,
        typography = GMusicTypography,
        shapes = GMusicShapes,
        content = content
    )
}
