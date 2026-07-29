package dev.recto.reader.data.search

import dev.recto.reader.data.BookFormat
import dev.recto.reader.data.BookRepository
import dev.recto.reader.data.EpubLoader
import dev.recto.reader.data.db.BookEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield

/**
 * Searches every book in the library, streaming results as it goes.
 *
 * No persistent index. An FTS table over a real library is roughly as big as
 * the text itself - around 190 MB for 200 books - and has to be built on
 * import, migrated, and kept in step with the library forever. Scanning on
 * demand costs a few seconds for a library that size and nothing to maintain.
 *
 * What makes that acceptable is streaming: hits are emitted per book, so the
 * first results appear in a few hundred milliseconds and the user can tap one
 * before the scan has finished.
 *
 * Books are searched most-recently-opened first, because the book you are
 * looking for is nearly always one you have read lately.
 */
class LibrarySearcher(private val repo: BookRepository) {

    fun search(books: List<BookEntity>, query: String): Flow<LibrarySearchProgress> = flow {
        val needle = query.trim()
        if (needle.length < 2) {
            emit(LibrarySearchProgress(running = false))
            return@flow
        }

        // Only formats we can actually read. A PDF in the library would
        // otherwise be counted as searched and silently contribute nothing.
        val searchable = books
            .filter { runCatching { BookFormat.valueOf(it.format).readable }.getOrDefault(false) }
            .sortedByDescending { it.lastOpenedAt ?: it.addedAt }

        var progress = LibrarySearchProgress(
            booksTotal = searchable.size,
            running = true
        )
        emit(progress)

        val collected = mutableListOf<SearchHit>()
        var skipped = 0

        for (book in searchable) {
            // Cooperative cancellation: typing another character cancels this
            // flow, and without a yield a tight scan loop would ignore it.
            yield()

            val hits = try {
                searchOne(book, needle)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                skipped++
                emptyList()
            }

            collected += hits
            progress = progress.copy(
                booksSearched = progress.booksSearched + 1,
                hits = collected.toList(),
                skipped = skipped
            )
            emit(progress)
        }

        emit(progress.copy(running = false))
    }.flowOn(Dispatchers.IO)

    private fun searchOne(book: BookEntity, needle: String): List<SearchHit> {
        val loaded = EpubLoader.load { repo.openBook(book) }.getOrNull() ?: return emptyList()

        return TextSearch.searchChapters(
            chapters = loaded.chapters,
            query = needle,
            bookId = book.id,
            bookTitle = loaded.title ?: book.title,
            // A handful per book is enough to answer "which book was that
            // in?". Opening the book gives you the full in-book search.
            maxHits = 8
        )
    }
}
