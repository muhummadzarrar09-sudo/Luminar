package dev.recto.reader.data.stats

import dev.recto.reader.data.db.ReadingSessionDao
import dev.recto.reader.data.db.ReadingSessionEntity

/**
 * Times a stretch of reading and writes it down when it ends.
 *
 * Two rules keep the numbers honest:
 *
 *  - A session with no page turns is discarded. Opening a book, realising it
 *    is the wrong one and backing out should not count as reading.
 *  - Sessions longer than [MAX_SESSION_MS] are clamped. If the reader is left
 *    open and the phone pockets itself, the raw elapsed time is fiction, and
 *    one bad session would poison every average on the stats screen.
 */
class SessionTracker(private val dao: ReadingSessionDao) {

    private companion object {
        /** Below this, it was a glance, not a session. */
        const val MIN_SESSION_MS = 5_000L

        /** Nobody reads one book for four hours without pausing. */
        const val MAX_SESSION_MS = 4L * 60 * 60 * 1000
    }

    private var bookId: Long = -1
    private var bookTitle: String = ""
    private var startedAt: Long = 0
    private var startChar: Int = 0
    private var pages: Int = 0
    private var running = false

    fun start(bookId: Long, bookTitle: String, atChar: Int) {
        if (running) return
        this.bookId = bookId
        this.bookTitle = bookTitle
        this.startedAt = System.currentTimeMillis()
        this.startChar = atChar
        this.pages = 0
        this.running = true
    }

    fun onPageTurned() {
        if (running) pages++
    }

    /**
     * Ends the session and persists it, returning the row id, or null when
     * the session was too short or had no page turns to be worth recording.
     */
    suspend fun stop(endChar: Int): Long? {
        if (!running) return null
        running = false

        val now = System.currentTimeMillis()
        val rawElapsed = now - startedAt
        if (rawElapsed < MIN_SESSION_MS || pages == 0) return null

        val elapsed = rawElapsed.coerceAtMost(MAX_SESSION_MS)

        return dao.insert(
            ReadingSessionEntity(
                bookId = bookId,
                bookTitle = bookTitle,
                startedAt = startedAt,
                endedAt = now,
                // Bucket by when the session STARTED. A session running past
                // midnight belongs to the evening you began it, which is also
                // what keeps a late-night read from breaking your streak.
                dayKey = ReadingStats.dayKey(startedAt),
                millisRead = elapsed,
                pagesTurned = pages,
                charsRead = (endChar - startChar).coerceAtLeast(0)
            )
        )
    }

    val isRunning: Boolean get() = running
}
