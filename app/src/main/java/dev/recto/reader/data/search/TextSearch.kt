package dev.recto.reader.data.search

import dev.recto.reader.data.Chapter

/**
 * Plain-text search over a book's chapters.
 *
 * Deliberately not SQLite FTS5. An FTS index over a real library is roughly
 * as large as the text itself - about 190 MB for 200 books - and has to be
 * built, migrated and kept in sync with every import. Scanning on demand
 * costs a couple of seconds across a whole library and nothing at all inside
 * one open book, whose text is already in memory.
 *
 * The trade-off: no stemming, so "running" will not match "run". For a reader
 * looking for a remembered phrase that is the right behaviour anyway.
 */
object TextSearch {

    private const val SNIPPET_BEFORE = 60
    private const val SNIPPET_AFTER = 90

    /**
     * Searches already-parsed chapters. Used for the open book, where the
     * text is in memory and results should feel instant.
     *
     * @param maxHits stops early on pathological queries - searching "e"
     *   across a novel would otherwise produce tens of thousands of hits and
     *   a list nobody can use.
     */
    fun searchChapters(
        chapters: List<Chapter>,
        query: String,
        bookId: Long,
        bookTitle: String,
        maxHits: Int = 300
    ): List<SearchHit> {
        val needle = query.trim()
        if (needle.length < 2) return emptyList()

        val hits = mutableListOf<SearchHit>()
        var chapterStart = 0

        for ((index, chapter) in chapters.withIndex()) {
            val text = chapter.text
            var from = 0

            while (hits.size < maxHits) {
                val at = text.indexOf(needle, from, ignoreCase = true)
                if (at < 0) break

                hits += buildHit(
                    text = text,
                    at = at,
                    length = needle.length,
                    bookId = bookId,
                    bookTitle = bookTitle,
                    chapterIndex = index,
                    chapterTitle = chapter.title,
                    chapterStart = chapterStart
                )

                // Advance past this match so overlapping occurrences of a
                // repeated string cannot loop forever.
                from = at + needle.length
            }

            chapterStart += text.length
            if (hits.size >= maxHits) break
        }

        return hits
    }

    /**
     * Searches a single flat string - used when scanning a book from disk,
     * where chapters are concatenated as they are read.
     */
    fun searchText(
        text: String,
        query: String,
        bookId: Long,
        bookTitle: String,
        maxHits: Int = 20
    ): List<SearchHit> {
        val needle = query.trim()
        if (needle.length < 2) return emptyList()

        val hits = mutableListOf<SearchHit>()
        var from = 0
        while (hits.size < maxHits) {
            val at = text.indexOf(needle, from, ignoreCase = true)
            if (at < 0) break
            hits += buildHit(
                text = text,
                at = at,
                length = needle.length,
                bookId = bookId,
                bookTitle = bookTitle,
                chapterIndex = 0,
                chapterTitle = null,
                chapterStart = 0
            )
            from = at + needle.length
        }
        return hits
    }

    private fun buildHit(
        text: String,
        at: Int,
        length: Int,
        bookId: Long,
        bookTitle: String,
        chapterIndex: Int,
        chapterTitle: String?,
        chapterStart: Int
    ): SearchHit {
        var start = (at - SNIPPET_BEFORE).coerceAtLeast(0)
        var end = (at + length + SNIPPET_AFTER).coerceAtMost(text.length)

        // Snap to word boundaries so a snippet never starts mid-word.
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        while (end < text.length && !text[end].isWhitespace()) end++

        val raw = text.substring(start, end)
        // Collapse the newlines that separate paragraphs; a snippet is one
        // line of context, not a layout.
        val snippet = raw.replace(Regex("\\s+"), " ").trim()

        // Re-find the match inside the cleaned snippet - collapsing
        // whitespace shifts every offset after it.
        val localRaw = at - start
        val prefixCollapsed = raw.substring(0, localRaw).replace(Regex("\\s+"), " ")
        val leading = raw.length - raw.trimStart().length
        val matchStart = (prefixCollapsed.length - if (leading > 0) 1 else 0)
            .coerceIn(0, snippet.length)
        val matchEnd = (matchStart + length).coerceAtMost(snippet.length)

        return SearchHit(
            bookId = bookId,
            bookTitle = bookTitle,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            charOffset = chapterStart + at,
            snippet = snippet,
            matchStart = matchStart,
            matchEnd = matchEnd
        )
    }
}
