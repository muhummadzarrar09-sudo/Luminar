package dev.recto.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Paper,
    background = Paper,
    onBackground = PaperInk,
    surface = Paper,
    onSurface = PaperInk,
    onSurfaceVariant = PaperMuted
)

private val DarkColors = darkColorScheme(
    primary = AccentLight,
    onPrimary = Night,
    background = Night,
    onBackground = NightInk,
    surface = Night,
    onSurface = NightInk,
    onSurfaceVariant = NightMuted
)

/**
 * Deliberately not using dynamic colour. A reading app wants a stable,
 * paper-like surface, not one that shifts with the user's wallpaper.
 */
@Composable
fun RectoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = RectoTypography,
        content = content
    )
}
