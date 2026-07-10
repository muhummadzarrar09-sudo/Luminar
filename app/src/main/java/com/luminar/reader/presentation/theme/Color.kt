// app/src/main/java/com/luminar/reader/presentation/theme/Color.kt
package com.luminar.reader.presentation.theme

import androidx.compose.ui.graphics.Color

// ─── AMOLED Dark Palette (Luxury Dark Gold) ──────────────
// Inspired by: Spotify dark + gold accent, 60-30-10 rule
// 60% backgrounds, 30% surfaces, 10% accent
val LuminarAmoledBlack = Color(0xFF050505)        // near-pure black (OLED efficient)
val LuminarSurface = Color(0xFF111111)             // card/surface - subtle lift
val LuminarSurfaceElevated = Color(0xFF1A1A1A)     // elevated cards, modals
val LuminarGold = Color(0xFFD4A843)                // primary accent - warm gold (less yellow, more premium)
val LuminarGoldDim = Color(0xFF8B7028)             // secondary gold - for inactive/subtle elements
val LuminarTextPrimary = Color(0xFFEAEAEA)         // primary text - off-white (softer than pure white)
val LuminarTextSecondary = Color(0xFF8A8A8A)       // secondary text - medium gray
val LuminarDivider = Color(0xFF1F1F1F)             // dividers - barely visible

// ─── Sepia Palette (Warm Paper) ──────────────────────────
val LuminarSepiaBackground = Color(0xFFF5EDD6)
val LuminarSepiaSurface = Color(0xFFEBE1C8)
val LuminarSepiaText = Color(0xFF2E2418)

// ─── Light Palette (Clean White) ─────────────────────────
val LuminarLightBackground = Color(0xFFFAFAFA)     // slightly off-white (easier on eyes)
val LuminarLightSurface = Color(0xFFF0F0F0)
val LuminarLightText = Color(0xFF1A1A1A)
