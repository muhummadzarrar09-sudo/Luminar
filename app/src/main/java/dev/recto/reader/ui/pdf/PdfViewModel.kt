package dev.recto.reader.ui.pdf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.recto.reader.data.BookRepository
import dev.recto.reader.data.ReaderSettings
import dev.recto.reader.data.SettingsRepository
import dev.recto.reader.data.db.BookEntity
import dev.recto.reader.data.db.RectoDatabase
import dev.recto.reader.data.pdf.PdfSource
import dev.recto.reader.data.stats.ReadingStats
import dev.recto.reader.data.stats.SessionTracker
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PdfState {
    data object Loading : PdfState
    data class Error(val message: String) : PdfState
    data class Ready(val book: BookEntity, val source: PdfSource) : PdfState
}

class PdfViewModel(app: Application) : AndroidViewModel(app) {

    private val db = RectoDatabase.get(app)
    private val repo = BookRepository(app, db.bookDao())
    private val sessionTracker = SessionTracker(db.readingSessionDao())

    val settings: StateFlow<ReaderSettings> = SettingsRepository(app).settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    private val _state = MutableStateFlow<PdfState>(PdfState.Loading)
    val state: StateFlow<PdfState> = _state.asStateFlow()

    private val _pageIndex = MutableStateFlow(0)
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    private val _chromeVisible = MutableStateFlow(false)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    private var bookId = -1L
    private var saveJob: Job? = null

    fun load(id: Long) {
        if (bookId == id && _state.value is PdfState.Ready) return
        bookId = id

        viewModelScope.launch {
            _state.value = PdfState.Loading

            val book = repo.byId(id)
            if (book == null) {
                _state.value = PdfState.Error("That book is no longer in your library.")
                return@launch
            }

            val opened = withContext(Dispatchers.IO) {
                openDescriptor(book)?.let { PdfSource.open(getApplication(), it) }
            }

            when {
                opened == null ->
                    _state.value = PdfState.Error(
                        "Cannot open this file. It may have been moved or deleted."
                    )

                opened.isFailure ->
                    _state.value = PdfState.Error(
                        opened.exceptionOrNull()?.message ?: "This PDF could not be opened."
                    )

                else -> {
                    val source = opened.getOrThrow()
                    _state.value = PdfState.Ready(book, source)
                    // For PDFs the stored locator is simply a page index.
                    _pageIndex.value = (book.locator?.toIntOrNull() ?: 0)
                        .coerceIn(0, (source.pageCount - 1).coerceAtLeast(0))
                    sessionTracker.start(
                        bookId = book.id,
                        bookTitle = book.title,
                        atChar = _pageIndex.value
                    )
                }
            }
        }
    }

    /**
     * PdfRenderer needs a real seekable file descriptor. A copied book already
     * has one; a SAF-referenced book has to be opened through the resolver,
     * which returns a descriptor backed by the provider.
     */
    private fun openDescriptor(book: BookEntity) = runCatching {
        book.localPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                return@runCatching android.os.ParcelFileDescriptor.open(
                    file,
                    android.os.ParcelFileDescriptor.MODE_READ_ONLY
                )
            }
        }
        getApplication<Application>().contentResolver
            .openFileDescriptor(Uri.parse(book.sourceUri), "r")
    }.getOrNull()

    fun goToPage(index: Int) {
        val source = (_state.value as? PdfState.Ready)?.source ?: return
        val clamped = index.coerceIn(0, (source.pageCount - 1).coerceAtLeast(0))
        if (clamped != _pageIndex.value) {
            _pageIndex.value = clamped
            sessionTracker.onPageTurned()
            scheduleSave()
        }
    }

    fun next() = goToPage(_pageIndex.value + 1)

    fun previous() = goToPage(_pageIndex.value - 1)

    fun toggleChrome() {
        _chromeVisible.value = !_chromeVisible.value
    }

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
        val ready = _state.value as? PdfState.Ready ?: return
        val total = ready.source.pageCount.coerceAtLeast(1)
        repo.saveProgress(
            id = ready.book.id,
            progress = (_pageIndex.value + 1).toFloat() / total,
            locator = _pageIndex.value.toString()
        )
    }

    fun endSession(onGoalReached: (minutes: Int, streak: Int) -> Unit = { _, _ -> }) {
        if (!sessionTracker.isRunning) return
        val endPage = _pageIndex.value
        viewModelScope.launch {
            val before = db.readingSessionDao().millisOnDay(ReadingStats.dayKey())
            sessionTracker.stop(endPage) ?: return@launch

            val goal = settings.value.dailyGoalMinutes
            if (goal <= 0) return@launch

            val after = db.readingSessionDao().millisOnDay(ReadingStats.dayKey())
            if ((before / 60_000).toInt() < goal && (after / 60_000).toInt() >= goal) {
                val days = db.readingSessionDao().recentDays().toSet()
                onGoalReached((after / 60_000).toInt(), ReadingStats.currentStreak(days))
            }
        }
    }

    /**
     * Releases the native renderer. Skipping this leaks a file descriptor per
     * book opened, and Android gives a process about a thousand before it
     * starts failing to open anything at all.
     */
    override fun onCleared() {
        super.onCleared()
        (_state.value as? PdfState.Ready)?.source?.close()
    }
}
