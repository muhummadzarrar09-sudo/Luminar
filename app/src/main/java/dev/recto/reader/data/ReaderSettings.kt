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
    // Ink is deliberately not black, and paper is deliberately not white.
    //
    // Maximum contrast is the single biggest cause of screen eye strain:
    // pure #000 on pure #FFF is a contrast ratio of 21:1, roughly double what
    // print achieves. Real book paper is a warm off-white and real ink is a
    // soft near-black, landing around 12-14:1 - still far above the 7:1 that
    // WCAG calls enhanced, but without the glare.
    PAPER(
        label = "Paper",
        background = Color(0xFFFAF6EE),
        text = Color(0xFF2B2620),
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
        background = Color(0xFF17150F),
        // Warm grey rather than white. Bright text on a dark field blooms at
        // the edges - halation - which is worse for astigmatic readers, and
        // dropping the luminance a little removes most of it.
        text = Color(0xFFD8D2C6),
        muted = Color(0xFF938B80),
        isDark = true
    ),
    BLACK(
        label = "Black",
        background = Color(0xFF000000),
        // Kept dimmer still: on OLED the pixels around a glyph are genuinely
        // off, so the edge contrast is even harsher than on Night.
        text = Color(0xFFC9C4BC),
        muted = Color(0xFF807B74),
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

/**
 * Page margins.
 *
 * These are the margins on a phone in portrait, where the screen is already
 * narrow enough that the line length lands around 35-45 characters - shorter
 * than print, but comfortable.
 *
 * Landscape is the real problem, and margins cannot fix it: on a 842dp-wide
 * screen the line runs to nearly 90 characters, well past the ~75 at which
 * the eye starts losing its place on the return sweep. That is handled by
 * capping the text column width instead - see MaxLineWidth in the reader.
 */
enum class PageMargin(val label: String, val sizeDp: Int) {
    NARROW("Narrow", 16),
    NORMAL("Normal", 24),
    WIDE("Wide", 36);

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
    val brightness: Float = 0.5f,

    // --- habits ---

    /**
     * Minutes of reading a day. Zero means no goal, which also disables the
     * "you have not hit your goal" flavour of the reminder.
     */
    val dailyGoalMinutes: Int = 0,

    /**
     * Off by default. An app that starts notifying before being asked is an
     * app that gets its notifications muted.
     */
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,

    val streakAlertsEnabled: Boolean = true,

    // --- annotating ---

    /**
     * Which highlighter colour a plain tap uses, as an index into
     * [HighlightColour]. Long-pressing a swatch changes it.
     *
     * Kindle has no equivalent and it is the small thing that annoys most:
     * if you always highlight in blue, you should not have to aim for blue
     * every single time.
     */
    val defaultHighlightColour: Int = 0
) {
    companion object {
        const val MIN_FONT_SP = 12
        const val MAX_FONT_SP = 32
    }
}
