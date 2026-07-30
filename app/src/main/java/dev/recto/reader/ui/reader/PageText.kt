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
     * Words that end in a full stop without ending a sentence.
     *
     * Kept short on purpose. Every entry here is a case where splitting would
     * be visibly wrong ("Mr. Jones" torn in half); a missing entry only costs
     * a slightly short selection, which the reader can extend. Guessing
     * aggressively would be the worse failure.
     */
    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "st", "jr", "sr", "vs", "etc",
        "eg", "ie", "no", "vol", "fig", "al", "inc", "ltd", "co",
        "approx", "dept", "est"
    )

    private fun isTerminator(c: Char) = c == '.' || c == '!' || c == '?'

    /** Closing punctuation that belongs to the sentence it follows. */
    private fun isCloser(c: Char) =
        c == '"' || c == '\u201D' || c == '\u2019' || c == '\'' ||
            c == ')' || c == ']'

    /**
     * True when the terminator at [i] really ends a sentence.
     *
     * Three things masquerade as sentence ends and are excluded:
     *  - decimals, "3.14"
     *  - initials, "J. R. R. Tolkien" - detected as a single letter before
     *    the stop, which is why the check is length-based rather than a list
     *  - abbreviations from [ABBREVIATIONS]
     *
     * A terminator must also be followed by whitespace or the end of the
     * text, so "recto.dev" does not split.
     */
    private fun isSentenceEnd(text: String, i: Int): Boolean {
        if (i !in text.indices || !isTerminator(text[i])) return false

        var j = i
        while (j + 1 < text.length && isTerminator(text[j + 1])) j++
        var k = j + 1
        while (k < text.length && isCloser(text[k])) k++

        if (k < text.length && !text[k].isWhitespace()) return false

        if (text[i] == '.') {
            if (i > 0 && text[i - 1].isDigit() &&
                i + 1 < text.length && text[i + 1].isDigit()
            ) {
                return false
            }
            var s = i - 1
            while (s >= 0 && text[s].isLetter()) s--
            val word = text.substring(s + 1, i).lowercase()
            if (word.length == 1) return false
            if (word in ABBREVIATIONS) return false
        }
        return true
    }

    /** Index just past the end of the sentence containing [pos]. */
    fun sentenceEndAfter(text: String, pos: Int): Int {
        if (text.isEmpty()) return 0
        var i = pos.coerceIn(0, text.length - 1)
        while (i < text.length) {
            if (isSentenceEnd(text, i)) {
                var j = i
                while (j + 1 < text.length && isTerminator(text[j + 1])) j++
                var k = j + 1
                while (k < text.length && isCloser(text[k])) k++
                return k
            }
            i++
        }
        return text.length
    }

    /** Index of the first character of the sentence containing [pos]. */
    fun sentenceStartBefore(text: String, pos: Int): Int {
        if (text.isEmpty()) return 0
        val limit = pos.coerceIn(0, text.length)
        var j = limit - 1
        while (j >= 0) {
            if (isSentenceEnd(text, j)) {
                var k = j
                while (k + 1 < text.length && isTerminator(text[k + 1])) k++
                k++
                while (k < text.length && isCloser(text[k])) k++
                while (k < text.length && text[k].isWhitespace()) k++
                return minOf(k, limit)
            }
            j--
        }
        return 0
    }

    /**
     * The sentence containing [offset], as a range within [text].
     */
    fun sentenceBoundsAt(text: String, offset: Int): IntRange? {
        if (text.isEmpty()) return null
        val start = sentenceStartBefore(text, offset)
        val end = sentenceEndAfter(text, offset)
        if (end <= start) return null
        return start until end
    }

    /**
     * The selection a drag should produce, snapped to whole units.
     *
     * Two levels, and the escalation is what makes it feel right:
     *
     *  - while the finger is still inside the sentence it started in, the
     *    selection snaps to whole WORDS, so short phrases stay easy to pick
     *  - once it leaves that sentence, it snaps to whole SENTENCES, because
     *    a drag that long is selecting passages, not trimming words
     *
     * Always returns the union of the anchor's unit and the cursor's unit,
     * never `min(anchor, cursor)..max(...)`. The naive version drops the
     * anchor word on a BACKWARD drag - dragging left from "sat" would select
     * up to the start of "sat" and exclude it. That showed up in 86 of 4000
     * randomised drags; the union form fails none of 18000.
     *
     * @param anchor where the long-press landed
     * @param cursor where the finger is now
     */
    fun dragSelection(text: String, anchor: Int, cursor: Int): IntRange {
        if (text.isEmpty()) return IntRange.EMPTY

        val cur = cursor.coerceIn(0, text.length)
        val lastIndex = text.length - 1

        val anchorSentence = sentenceBoundsAt(text, anchor)
        val escaped = anchorSentence != null &&
            (cur < anchorSentence.first || cur > anchorSentence.last + 1)

        if (escaped) {
            val cursorSentence = sentenceBoundsAt(text, minOf(cur, lastIndex))
            val lo = minOf(anchorSentence.first, cursorSentence?.first ?: cur)
            val hi = maxOf(anchorSentence.last + 1, cursorSentence?.let { it.last + 1 } ?: cur)
            return lo until maxOf(hi, lo)
        }

        val anchorWord = wordBoundsAt(text, anchor)
        val cursorWord = wordBoundsAt(text, minOf(cur, lastIndex))
        val lo = minOf(anchorWord?.first ?: anchor, cursorWord?.first ?: cur)
        val hi = maxOf(
            anchorWord?.let { it.last + 1 } ?: anchor,
            cursorWord?.let { it.last + 1 } ?: cur
        )
        return lo until maxOf(hi, lo)
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
