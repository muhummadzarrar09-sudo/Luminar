package dev.recto.reader.data

import androidx.compose.ui.graphics.Color

/**
 * The reading themes. Kindle ships exactly four - White, Sepia, Green, Black -
 * and we add a true-black OLED variant, which is the one thing Kindle's
 * "Black" gets wrong on modern phone screens (it is dark grey, not off).
 */
enum class ReaderTheme(
    val label: String,
    val background: Color,
    val text: Color,
    val muted: Color,
    /** True when the page is dark, so system bars can be told to match. */
    val isDark: Boolean
) {
    PAPER(
        label = "Paper",
        background = Color(0xFFFBF7F0),
        text = Color(0xFF1A1714),
        muted = Color(0xFF6B615A),
        isDark = false
    ),
    SEPIA(
        label = "Sepia",
        background = Color(0xFFF4ECD8),
        text = Color(0xFF3A2F24),
        muted = Color(0xFF7A6A55),
        isDark = false
    ),
    QUIET(
        label = "Quiet",
        background = Color(0xFFE6EDE4),
        text = Color(0xFF23301F),
        muted = Color(0xFF5F6E5A),
        isDark = false
    ),
    NIGHT(
        label = "Night",
        background = Color(0xFF15130F),
        text = Color(0xFFE8E2D8),
        muted = Color(0xFF9A9188),
        isDark = true
    ),
    BLACK(
        label = "Black",
        background = Color(0xFF000000),
        text = Color(0xFFD6D2CB),
        muted = Color(0xFF8A857E),
        isDark = true
    );

    companion object {
        fun fromName(name: String?): ReaderTheme =
            entries.firstOrNull { it.name == name } ?: PAPER
    }
}

/**
 * Reading fonts.
 *
 * Phase 2 uses the platform families, which is why this is an enum of stacks
 * rather than bundled files: shipping six OFL fonts adds several MB to the
 * APK, and the system serif is already a decent reading face. Real bundled
 * fonts (Literata, Bitter, Atkinson Hyperlegible, OpenDyslexic) arrive with
 * the Readium swap, when the renderer can actually use them.
 */
enum class ReaderFont(val label: String) {
    SERIF("Serif"),
    SANS("Sans"),
    MONO("Mono")
}

/**
 * How tight the lines are set.
 */
enum class LineSpacing(val label: String, val multiplier: Float) {
    TIGHT("Tight", 1.35f),
    NORMAL("Normal", 1.62f),
    RELAXED("Relaxed", 1.90f);

    companion object {
        fun fromName(name: String?): LineSpacing =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

enum class PageMargin(val label: String, val sizeDp: Int) {
    NARROW("Narrow", 16),
    NORMAL("Normal", 26),
    WIDE("Wide", 40);

    companion object {
        fun fromName(name: String?): PageMargin =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

/**
 * Everything that changes how a page looks. Kept as one immutable value so
 * the reader can treat "settings changed" as a single re-pagination trigger.
 */
data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val font: ReaderFont = ReaderFont.SERIF,
    val fontSizeSp: Int = 18,
    val lineSpacing: LineSpacing = LineSpacing.NORMAL,
    val margin: PageMargin = PageMargin.NORMAL,
    val justify: Boolean = true,
    /** Follow the system dark theme instead of a fixed page colour. */
    val followSystemDark: Boolean = false,
    val keepScreenOn: Boolean = true,
    val volumeKeysTurnPages: Boolean = true,

    /**
     * Amber tint over the page, 0..1. Good evidence for protecting sleep when
     * reading at night; little evidence it reduces eye strain by itself.
     */
    val warmth: Float = 0f,

    /**
     * Extra dimming below the phone's hardware minimum, 0..1. This is the
     * lever that actually helps comfort in a dark room.
     */
    val dim: Float = 0f,

    /**
     * Take over the screen backlight while reading. Off means the system
     * brightness applies as normal.
     */
    val useReaderBrightness: Boolean = false,

    /** Backlight level when [useReaderBrightness] is on, 0..1. */
    val brightness: Float = 0.5f
) {
    companion object {
        const val MIN_FONT_SP = 12
        const val MAX_FONT_SP = 32
    }
}
