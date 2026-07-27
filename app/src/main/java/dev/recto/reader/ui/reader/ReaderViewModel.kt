package dev.recto.reader.ui.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.recto.reader.data.BookRepository
import dev.recto.reader.data.EpubBook
import dev.recto.reader.data.EpubLoader
import dev.recto.reader.data.db.BookEntity
import dev.recto.reader.data.db.RectoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Error(val message: String) : ReaderState
    data class Ready(
        val book: BookEntity,
        val content: EpubBook,
        /** Null until the view has measured itself and pagination has run. */
        val pages: List<Page>?
    ) : ReaderState
}

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookRepository(app, RectoDatabase.get(app).bookDao())

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _pageIndex = MutableStateFlow(0)
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    private val _chromeVisible = MutableStateFlow(false)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    private var bookId: Long = -1
    private var saveJob: Job? = null

    /** Character offset we want to land on once pagination finishes. */
    private var pendingRestoreChar: Int? = null

    fun load(id: Long) {
        if (bookId == id && _state.value is ReaderState.Ready) return
        bookId = id

        viewModelScope.launch {
            _state.value = ReaderState.Loading

            val book = repo.byId(id)
            if (book == null) {
                _state.value = ReaderState.Error("That book is no longer in your library.")
                return@launch
            }

            val loaded = withContext(Dispatchers.IO) {
                EpubLoader.load {
                    getApplication<Application>().contentResolver
                        .openInputStream(Uri.parse(book.sourceUri))
                        ?: error("Cannot open this file. It may have been moved or deleted.")
                }
            }

            loaded.fold(
                onSuccess = { content ->
                    pendingRestoreChar = book.locator?.toIntOrNull()
                    _state.value = ReaderState.Ready(book, content, pages = null)
                },
                onFailure = { e ->
                    _state.value = ReaderState.Error(
                        e.message ?: "This EPUB could not be opened."
                    )
                }
            )
        }
    }

    /** Called by the reader once it knows its own size. */
    fun onPaginated(pages: List<Page>) {
        val ready = _state.value as? ReaderState.Ready ?: return
        _state.value = ready.copy(pages = pages)

        val restore = pendingRestoreChar
        if (restore != null && pages.isNotEmpty()) {
            pendingRestoreChar = null
            _pageIndex.value = pageForGlobalChar(pages, ready.content, restore)
        } else {
            _pageIndex.value = _pageIndex.value.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        }
    }

    /**
     * Re-anchors the reading position after a re-pagination (rotation, font
     * size change). We store a character offset rather than a page number
     * precisely so the position survives a different page count.
     */
    fun rememberPositionBeforeRepaginate() {
        val ready = _state.value as? ReaderState.Ready ?: return
        val pages = ready.pages ?: return
        pendingRestoreChar = globalCharForPage(pages, ready.content, _pageIndex.value)
    }

    fun next() {
        val pages = (_state.value as? ReaderState.Ready)?.pages ?: return
        if (_pageIndex.value < pages.size - 1) {
            _pageIndex.value++
            scheduleSave()
        }
    }

    fun previous() {
        if (_pageIndex.value > 0) {
            _pageIndex.value--
            scheduleSave()
        }
    }

    fun goTo(index: Int) {
        val pages = (_state.value as? ReaderState.Ready)?.pages ?: return
        _pageIndex.value = index.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        scheduleSave()
    }

    fun toggleChrome() {
        _chromeVisible.value = !_chromeVisible.value
    }

    fun hideChrome() {
        _chromeVisible.value = false
    }

    /**
     * Debounced so flipping quickly through pages does not hammer the
     * database, but a position is never more than a second stale.
     */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            persist()
        }
    }

    fun persistNow() {
        saveJob?.cancel()
        viewModelScope.launch { persist() }
    }

    private suspend fun persist() {
        val ready = _state.value as? ReaderState.Ready ?: return
        val pages = ready.pages ?: return
        if (pages.isEmpty()) return

        val index = _pageIndex.value.coerceIn(0, pages.size - 1)
        val globalChar = globalCharForPage(pages, ready.content, index)
        val total = ready.content.totalChars.coerceAtLeast(1)

        repo.saveProgress(
            id = ready.book.id,
            progress = globalChar.toFloat() / total,
            locator = globalChar.toString()
        )
    }

    // --- position maths -----------------------------------------------------

    private fun chapterOffsets(content: EpubBook): IntArray {
        val offsets = IntArray(content.chapters.size)
        var running = 0
        content.chapters.forEachIndexed { i, c ->
            offsets[i] = running
            running += c.text.length
        }
        return offsets
    }

    private fun globalCharForPage(pages: List<Page>, content: EpubBook, index: Int): Int {
        val page = pages.getOrNull(index) ?: return 0
        val offsets = chapterOffsets(content)
        val base = offsets.getOrElse(page.chapterIndex) { 0 }
        return base + page.startChar
    }

    private fun pageForGlobalChar(pages: List<Page>, content: EpubBook, target: Int): Int {
        val offsets = chapterOffsets(content)
        var best = 0
        for ((i, page) in pages.withIndex()) {
            val abs = offsets.getOrElse(page.chapterIndex) { 0 } + page.startChar
            if (abs <= target) best = i else break
        }
        return best
    }
}
