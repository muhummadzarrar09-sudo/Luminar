package dev.recto.reader.data.search

/**
 * One match inside a book.
 *
 * [charOffset] is an absolute offset into the book's concatenated chapter
 * text, the same coordinate system used for reading positions, highlights and
 * bookmarks. That consistency is what lets a search result jump straight to
 * the right page at any font size.
 */
data class SearchHit(
    val bookId: Long,
    val bookTitle: String,
    val chapterIndex: Int,
    val chapterTitle: String?,
    val charOffset: Int,
    /** Surrounding sentence fragment, for the result list. */
    val snippet: String,
    /** Where the match sits inside [snippet], for bolding it. */
    val matchStart: Int,
    val matchEnd: Int
)

/**
 * Progress of a library-wide search. Results stream in as each book is
 * scanned rather than arriving all at once, so the first hits are visible in
 * a few hundred milliseconds instead of after the whole library.
 */
data class LibrarySearchProgress(
    val booksSearched: Int = 0,
    val booksTotal: Int = 0,
    val hits: List<SearchHit> = emptyList(),
    val running: Boolean = false,
    /** Books that could not be opened - moved, deleted, or not yet readable. */
    val skipped: Int = 0
) {
    val done: Boolean get() = !running && booksTotal > 0
}
