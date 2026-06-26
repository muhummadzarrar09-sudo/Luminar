// app/src/main/java/com/luminar/reader/presentation/theme/Theme.kt
package com.luminar.reader.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.luminar.reader.data.model.AppTheme

private val AmoledColorScheme = darkColorScheme(
    primary = LuminarGold,
    onPrimary = Color(0xFF241B00),
    background = LuminarAmoledBlack,
    onBackground = LuminarTextPrimary,
    surface = LuminarSurface,
    onSurface = LuminarTextPrimary,
    surfaceVariant = LuminarSurfaceElevated,
    onSurfaceVariant = LuminarTextSecondary,
    outline = LuminarDivider,
    outlineVariant = LuminarDivider
)

private val SepiaColorScheme = lightColorScheme(
    primary = LuminarGoldDim,
    onPrimary = Color.White,
    background = LuminarSepiaBackground,
    onBackground = LuminarSepiaText,
    surface = LuminarSepiaSurface,
    onSurface = LuminarSepiaText,
    surfaceVariant = Color(0xFFDED1B4),
    onSurfaceVariant = Color(0xFF5A4B37),
    outline = Color(0xFFC9B991),
    outlineVariant = Color(0xFFD8CAA9)
)

private val LightColorScheme = lightColorScheme(
    primary = LuminarGoldDim,
    onPrimary = Color.White,
    background = LuminarLightBackground,
    onBackground = LuminarLightText,
    surface = LuminarLightSurface,
    onSurface = LuminarLightText,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF505050),
    outline = Color(0xFFD0D0D0),
    outlineVariant = Color(0xFFE2E2E2)
)

@Composable
fun LuminarReaderTheme(
    selectedTheme: AppTheme = AppTheme.DARK_AMOLED,
    content: @Composable () -> Unit
) {
    val colorScheme = when (selectedTheme) {
        AppTheme.DARK_AMOLED -> AmoledColorScheme
        AppTheme.SEPIA -> SepiaColorScheme
        AppTheme.LIGHT -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LuminarTypography,
        content = content
    )
}

fun AppTheme.next(): AppTheme {
    return when (this) {
        AppTheme.DARK_AMOLED -> AppTheme.SEPIA
        AppTheme.SEPIA -> AppTheme.LIGHT
        AppTheme.LIGHT -> AppTheme.DARK_AMOLED
    }
}

fun AppTheme.readerBackgroundColor(): Color {
    return when (this) {
        AppTheme.DARK_AMOLED -> LuminarAmoledBlack
        AppTheme.SEPIA -> LuminarSepiaBackground
        AppTheme.LIGHT -> Color.White
    }
}

fun AppTheme.readerControlsContainerColor(): Color {
    return when (this) {
        AppTheme.DARK_AMOLED -> Color(0xE6000000)
        AppTheme.SEPIA -> Color(0xEEF4ECD8)
        AppTheme.LIGHT -> Color(0xEEFFFFFF)
    }
}

fun AppTheme.readerControlsContentColor(): Color {
    return when (this) {
        AppTheme.DARK_AMOLED -> LuminarTextPrimary
        AppTheme.SEPIA -> LuminarSepiaText
        AppTheme.LIGHT -> LuminarLightText
    }
}

fun AppTheme.usesPdfNightMode(): Boolean {
    return this == AppTheme.DARK_AMOLED
}
