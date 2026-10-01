package com.mediavault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CyanPrimary = Color(0xFF00E5FF)
val CyanPrimaryContainer = Color(0xFF004D5A)
val CyanOnPrimary = Color(0xFF000000)

val DarkBackground = Color(0xFF0F1117)
val DarkSurface = Color(0xFF181B24)
val DarkSurfaceVariant = Color(0xFF222736)

val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0A0A0A)

val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = CyanOnPrimary,
    primaryContainer = CyanPrimaryContainer,
    onPrimaryContainer = Color(0xFF8CF5FF),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = Color(0xFFECEFF4),
    onSurface = Color(0xFFECEFF4)
)

val AmoledColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = CyanOnPrimary,
    primaryContainer = CyanPrimaryContainer,
    background = AmoledBackground,
    surface = AmoledSurface,
    surfaceVariant = Color(0xFF141414),
    onBackground = Color.White,
    onSurface = Color.White
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00838F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2EBF2),
    background = Color(0xFFF7F9FC),
    surface = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun MediaVaultTheme(
    isAmoled: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = when {
        isAmoled -> AmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
