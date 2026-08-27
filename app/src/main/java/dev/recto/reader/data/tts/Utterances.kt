package dev.recto.reader.data.tts

import dev.recto.reader.ui.reader.PageText

/**
 * One thing the voice says, and where it lives in the book.
 *
 * Offsets are ABSOLUTE character positions in the book's concatenated text,
 * the same coordinate space as highlights and reading positions. That is what
 * lets read-aloud survive a re-pagination mid-sentence: the utterance knows
 * where it is in the book, not on the page.
 */
data class Utterance(
    val start: Int,
    val end: Int,
    val text: String
)

/**
 * Splits book text into things worth speaking.
 *
 * A sentence at a time, not a page at a time. Three reasons, all of which
 * showed up while designing this:
 *
 *  - Highlighting. The current sentence can be shown on the page only if the
 *    reader knows where each sentence starts and ends.
 *  - Stopping. Pause between sentences and resume from the same place; pause
 *    mid-page and you would restart the page.
 *  - Page turns. The page has to flip when the voice crosses onto the next
 *    one, which means knowing which page an utterance belongs to.
 */
object Utterances {

    /**
     * Longest string handed to the engine at once.
     *
     * TextToSpeech.getMaxSpeechInputLength() is around 4000 characters, but
     * a shorter cap is better anyway: a very long utterance delays the first
     * sound while the whole thing synthesises, and makes pausing feel
     * unresponsive because stop() only takes effect at a boundary.
     */
    const val MAX_UTTERANCE = 500

    /** Don't cut a chunk shorter than this - tiny fragments sound clipped. */
    private const val MIN_CHUNK = 40

    /**
     * @param text  a slice of the book
     * @param base  absolute offset of [text] within the book
     */
    fun split(text: String, base: Int = 0): List<Utterance> {
        val out = mutableListOf<Utterance>()
        var i = 0
        val n = text.length

        while (i < n) {
            // Whitespace between sentences belongs to no utterance. Leaving
            // it in makes the engine pause oddly and throws the highlight
            // rectangle off by a leading space.
            while (i < n && text[i].isWhitespace()) i++
            if (i >= n) break

            var end = PageText.sentenceEndAfter(text, i)
            if (end <= i) end = n

            // A "sentence" with no full stop for three pages - common in
            // older books and in badly converted EPUBs - would otherwise be
            // one enormous utterance. Break it at punctuation, then at a
            // space, and only then mid-word as a last resort.
            while (end - i > MAX_UTTERANCE) {
                var cut = -1

                for (p in (i + MAX_UTTERANCE - 1) downTo (i + MIN_CHUNK)) {
                    val c = text[p]
                    if (c == ',' || c == ';' || c == ':') {
                        cut = p + 1
                        break
                    }
                }
                if (cut < 0) {
                    for (p in (i + MAX_UTTERANCE - 1) downTo (i + MIN_CHUNK)) {
                        if (text[p].isWhitespace()) {
                            cut = p
                            break
                        }
                    }
                }
                if (cut < 0) cut = i + MAX_UTTERANCE

                emit(out, text, base, i, cut)
                i = cut
                while (i < n && text[i].isWhitespace()) i++
            }

            emit(out, text, base, i, end)
            i = end
        }
        return out
    }

    private fun emit(
        out: MutableList<Utterance>,
        text: String,
        base: Int,
        from: Int,
        to: Int
    ) {
        if (to <= from) return
        val raw = text.substring(from, to)
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return

        // Keep the offsets honest: trimming the string must move the start
        // and end with it, or the highlight lands on the wrong characters.
        val lead = raw.indexOfFirst { !it.isWhitespace() }
        val trail = raw.length - raw.indexOfLast { !it.isWhitespace() } - 1

        out += Utterance(
            start = base + from + lead,
            end = base + to - trail,
            text = trimmed
        )
    }

    /**
     * Softens things that sound wrong read aloud.
     *
     * Deliberately conservative. Every rule here fixes something a listener
     * would notice; anything cleverer risks mangling the author's words,
     * which is worse than a slightly odd reading.
     */
    fun forSpeech(text: String): String {
        var s = text
        // Ellipses and dashes: engines often say nothing at all for these,
        // running two clauses together. A comma gives the pause they imply.
        s = s.replace("\u2026", ", ")
        s = s.replace("--", ", ")
        s = s.replace(" \u2014 ", ", ")
        s = s.replace(" \u2013 ", ", ")
        // Collapse whitespace: EPUB text is full of newlines mid-sentence
        // from the original markup, and some engines pause at each one.
        s = s.replace(Regex("\\s+"), " ")
        return s.trim()
    }
}
