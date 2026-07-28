package dev.recto.reader.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import dev.recto.reader.data.HighlightColour
import dev.recto.reader.data.db.AnnotationEntity
import dev.recto.reader.data.db.AnnotationKind

/**
 * Builds the styled text for one page: the words, plus a coloured background
 * behind anything highlighted, plus the live selection.
 *
 * Rendering highlights as SpanStyle backgrounds rather than hand-drawn
 * rectangles is the important choice here. Compose then handles wrapping,
 * justification, RTL and multi-line spans for free - a highlight that runs
 * across four lines just works. Drawing rects would mean walking
 * TextLayoutResult line boxes and getting every edge case right by hand.
 */
object PageText {

    /**
     * @param pageStart absolute character offset of this page's first char
     * @param annotations all annotations for the book; filtered here
     * @param selection live selection as absolute offsets, or null
     */
    fun build(
        text: String,
        pageStart: Int,
        annotations: List<AnnotationEntity>,
        selection: IntRange?,
        darkTheme: Boolean,
        selectionColour: Color
    ): AnnotatedString {
        val pageEnd = pageStart + text.length

        return buildAnnotatedString {
            append(text)

            annotations.forEach { a ->
                if (a.kind == AnnotationKind.BOOKMARK.name) return@forEach

                // Clip to this page. A highlight spanning a page break shows
                // its correct portion on each side.
                val from = (a.startChar - pageStart).coerceIn(0, text.length)
                val to = (a.endChar - pageStart).coerceIn(0, text.length)
                if (from >= to) return@forEach
                if (a.endChar <= pageStart || a.startChar >= pageEnd) return@forEach

                val colour = HighlightColour.fromIndex(a.colour).colorFor(darkTheme)

                addStyle(
                    SpanStyle(
                        background = colour,
                        // A note gets an underline as well, so you can tell at
                        // a glance which highlights have something written
                        // against them without opening the notebook.
                        textDecoration = if (a.kind == AnnotationKind.NOTE.name) {
                            TextDecoration.Underline
                        } else {
                            null
                        }
                    ),
                    from,
                    to
                )
            }

            // Live selection paints last so it sits on top of any highlight
            // it overlaps.
            if (selection != null) {
                val from = (selection.first - pageStart).coerceIn(0, text.length)
                val to = (selection.last - pageStart).coerceIn(0, text.length)
                if (from < to) {
                    addStyle(SpanStyle(background = selectionColour), from, to)
                }
            }
        }
    }

    /**
     * Grows an offset to cover the whole word under it.
     *
     * Long-pressing mid-word should select the word, not a zero-width point.
     * Word characters are letters, digits, apostrophes and hyphens, so
     * "well-meaning" and "don't" stay intact.
     */
    fun wordBoundsAt(text: String, offset: Int): IntRange? {
        if (text.isEmpty()) return null
        val i = offset.coerceIn(0, text.length - 1)

        fun isWord(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '\u2019' || c == '-'

        if (!isWord(text[i])) {
            // Landed on a space or punctuation - look at the character before,
            // which is what a reader means when they press just past a word.
            val prev = i - 1
            if (prev < 0 || !isWord(text[prev])) return null
            return wordBoundsAt(text, prev)
        }

        var start = i
        while (start > 0 && isWord(text[start - 1])) start--
        var end = i + 1
        while (end < text.length && isWord(text[end])) end++

        return start until end
    }
}
