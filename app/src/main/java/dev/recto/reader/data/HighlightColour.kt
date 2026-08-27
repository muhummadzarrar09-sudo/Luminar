package dev.recto.reader.data

import androidx.compose.ui.graphics.Color

/**
 * The four highlighter colours, matching Kindle's set.
 *
 * Each has two forms: [onLight] for the paper-ish themes and [onDark] for
 * Night and Black. A yellow that reads as a highlighter on cream becomes a
 * glaring block on a black page, so the dark variants are desaturated and
 * dropped in luminance rather than being the same colour at lower alpha.
 */
enum class HighlightColour(
    val label: String,
    val onLight: Color,
    val onDark: Color
) {
    YELLOW("Yellow", Color(0xFFFFE08A), Color(0xFF5A4A18)),
    GREEN("Green", Color(0xFFB8E6B0), Color(0xFF2E4A29)),
    BLUE("Blue", Color(0xFFAFD6F5), Color(0xFF243F55)),
    PINK("Pink", Color(0xFFF7B8CE), Color(0xFF56283A));

    fun colorFor(darkTheme: Boolean): Color = if (darkTheme) onDark else onLight

    companion object {
        fun fromIndex(index: Int): HighlightColour =
            entries.getOrElse(index) { YELLOW }
    }
}
