package com.luminar.reader.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminar.reader.data.epub.EpubBookLoader
import com.luminar.reader.data.local.datastore.UserPreferencesRepository
import com.luminar.reader.data.model.AppTheme
import com.luminar.reader.data.model.Book
import com.luminar.reader.data.repository.BookRepository
import com.luminar.reader.domain.usecase.SaveProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import org.readium.r2.shared.publication.Publication
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.luminar.reader.data.local.db.BookTocDao
import com.luminar.reader.data.model.BookToc

import com.luminar.reader.data.local.db.BookmarkDao
import com.luminar.reader.data.local.db.HighlightDao
import com.luminar.reader.data.model.BookFormat
import com.luminar.reader.data.model.Bookmark
import com.luminar.reader.data.model.Highlight

import com.luminar.reader.data.repository.DictionaryRepository
import com.luminar.reader.network.DictEntry

data class EpubReaderUiState(
    val book: Book? = null,
    val publication: Publication? = null,
    val tocItems: List<BookToc> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val isBookmarked: Boolean = false,
    val currentCfi: String = "",
    val initialCfi: String? = null,
    val fontScale: Float = 1.0f,
    val dictWord: String? = null,
    val dictEntry: DictEntry? = null,
    val isDictLoading: Boolean = false,
    val isDictOfflineError: Boolean = false,
    val isLoading: Boolean = true,
    val showControls: Boolean = false,
    val currentTheme: AppTheme = AppTheme.DARK_AMOLED,
    val keepScreenOn: Boolean = true,
    val volumeButtonsPageTurn: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class EpubReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val bookTocDao: BookTocDao,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao,
    private val dictionaryRepository: DictionaryRepository,
    private val epubBookLoader: EpubBookLoader,
    private val saveProgressUseCase: SaveProgressUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val readerInputController: ReaderInputController
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle["bookId"])

    private val _uiState = MutableStateFlow(EpubReaderUiState())
    val uiState = _uiState.asStateFlow()

    private var controlsAutoHideJob: Job? = null

    init {
        observeBookAndPublication()
        observeToc()
        observeHighlights()
        observeBookmarks()
        observeCfiBookmark()
        observePreferences()
        observeReaderCommands()
        markBookOpened()
    }

    fun onEvent(event: ReaderEvent) {
        when (event) {
            ReaderEvent.ToggleControls -> toggleControls()
            is ReaderEvent.ThemeChanged -> changeTheme(event.theme)
            else -> {}
        }
    }

    fun onLocatorChanged(cfiString: String) {
        val book = _uiState.value.book ?: return
        _uiState.update { it.copy(currentCfi = cfiString) }
        viewModelScope.launch {
            saveProgressUseCase(
                bookId = book.id,
                currentPage = 0,
                scrollOffset = 0f,
                epubCfi = cfiString
            )
        }
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
            bookmarkDao.getBookmarksForBook(bookId).collect { list ->
                _uiState.update { it.copy(bookmarks = list) }
            }
        }
    }

    private fun observeCfiBookmark() {
        viewModelScope.launch {
            _uiState.collect { state ->
                bookmarkDao.getBookmarkForCfi(bookId, state.currentCfi).collect { bm ->
                    _uiState.update { it.copy(isBookmarked = bm != null) }
                }
            }
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            val cfi = _uiState.value.currentCfi
            val existing = bookmarkDao.getBookmarkForCfi(bookId, cfi).first()
            if (existing != null) {
                bookmarkDao.deleteBookmark(existing)
            } else {
                val b = Bookmark(bookId = bookId, format = BookFormat.EPUB, pdfPage = null, epubCfi = cfi, label = "Saved Place")
                bookmarkDao.insertBookmark(b)
            }
        }
    }

    fun deleteBookmark(b: Bookmark) {
        viewModelScope.launch { bookmarkDao.deleteBookmark(b) }
    }

    fun lookupWord(word: String) {
        val clean = word.trim()
        if (clean.isEmpty()) return
        _uiState.update { it.copy(dictWord = clean, isDictLoading = true, isDictOfflineError = false, dictEntry = null) }
        viewModelScope.launch {
            val res = dictionaryRepository.lookup(clean)
            _uiState.update { it.copy(dictEntry = res.entry, isDictOfflineError = res.isOfflineError, isDictLoading = false) }
        }
    }

    fun dismissDict() {
        _uiState.update { it.copy(dictWord = null, dictEntry = null) }
    }

    fun renameBookmark(b: Bookmark, newLabel: String) {
        viewModelScope.launch { bookmarkDao.updateBookmark(b.copy(label = newLabel)) }
    }

    fun onFontScaleChanged(scale: Float) {
        _uiState.update { it.copy(fontScale = scale) }
        onControlsInteraction()
        viewModelScope.launch { userPreferencesRepository.setFontScale(scale) }
    }

    fun onControlsInteraction() {
        if (_uiState.value.showControls) {
            scheduleControlsAutoHide()
        }
    }

    fun setReaderVisible(visible: Boolean) {
        readerInputController.isReaderOpen = visible
    }

    private fun observeBookAndPublication() {
        viewModelScope.launch {
            val progress = bookRepository.getProgress(bookId).first()
            val initialCfi = progress?.epubCfi

            bookRepository.getBookById(bookId)
                .catch { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.message) }
                }
                .collect { book ->
                    if (book != null) {
                        val file = File(book.filePath)
                        val pub = if (file.exists()) epubBookLoader.openPublication(file) else null
                        _uiState.update {
                            it.copy(
                                book = book,
                                publication = pub,
                                initialCfi = initialCfi,
                                isLoading = false,
                                error = if (pub == null) "Unable to open EPUB" else null
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "Book not found") }
                    }
                }
        }
    }

    private fun observeToc() {
        viewModelScope.launch {
            bookTocDao.getTocForBook(bookId).collect { items ->
                _uiState.update { it.copy(tocItems = items) }
            }
        }
    }

    private fun observeHighlights() {
        viewModelScope.launch {
            highlightDao.getHighlightsForBook(bookId).collect { list ->
                _uiState.update { it.copy(highlights = list) }
            }
        }
    }

    fun addEpubHighlight(color: Int, note: String?, cfi: String?, text: String?) {
        viewModelScope.launch {
            val h = Highlight(
                bookId = bookId,
                format = BookFormat.EPUB,
                pdfPage = null,
                rectLeft = null,
                rectTop = null,
                rectRight = null,
                rectBottom = null,
                epubCfi = cfi,
                epubSelectedText = text,
                color = color,
                noteText = note
            )
            highlightDao.insertHighlight(h)
        }
    }

    fun deleteHighlight(h: Highlight) {
        viewModelScope.launch { highlightDao.deleteHighlight(h) }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        currentTheme = prefs.selectedTheme,
                        keepScreenOn = prefs.keepScreenOn,
                        volumeButtonsPageTurn = prefs.volumeButtonsPageTurn,
                        fontScale = prefs.fontScale
                    )
                }
            }
        }
    }

    private fun observeReaderCommands() {
        viewModelScope.launch {
            readerInputController.commands.collect { command ->
                // Commands handled via visual navigator
            }
        }
    }

    private fun markBookOpened() {
        viewModelScope.launch { bookRepository.markBookOpened(bookId) }
    }

    private fun toggleControls() {
        val shouldShow = !_uiState.value.showControls
        _uiState.update { it.copy(showControls = shouldShow) }
        if (shouldShow) scheduleControlsAutoHide() else controlsAutoHideJob?.cancel()
    }

    private fun changeTheme(theme: AppTheme) {
        _uiState.update { it.copy(currentTheme = theme) }
        onControlsInteraction()
        viewModelScope.launch { userPreferencesRepository.setSelectedTheme(theme) }
    }

    private fun scheduleControlsAutoHide() {
        controlsAutoHideJob?.cancel()
        controlsAutoHideJob = viewModelScope.launch {
            delay(3_000)
            _uiState.update { it.copy(showControls = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value.publication?.close()
    }
}
