package dev.recto.reader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Recto's reading palette.
 *
 * These are the four Kindle reading themes plus a true-black OLED variant.
 * Only Paper and Night are wired into the Material scheme in Phase 0; Sepia,
 * Green and OLED land in Phase 2 with the theme switcher.
 */

// Paper (light)
val Paper = Color(0xFFFBF7F0)
val PaperInk = Color(0xFF1A1714)
val PaperMuted = Color(0xFF6B615A)

// Night (dark)
val Night = Color(0xFF15130F)
val NightInk = Color(0xFFE8E2D8)
val NightMuted = Color(0xFF9A9188)

// Sepia (Phase 2)
val Sepia = Color(0xFFF4ECD8)
val SepiaInk = Color(0xFF3A2F24)

// Accent - the warm brown of a well-handled book spine
val Accent = Color(0xFF8C5A3C)
val AccentLight = Color(0xFFC08A66)
