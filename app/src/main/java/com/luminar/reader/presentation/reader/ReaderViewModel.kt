// app/src/main/java/com/luminar/reader/presentation/reader/ReaderViewModel.kt
package com.luminar.reader.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminar.reader.data.local.datastore.UserPreferencesRepository
import com.luminar.reader.data.epub.EpubChapter
import com.luminar.reader.data.error.ErrorReporter
import com.luminar.reader.data.local.db.BookDao
import com.luminar.reader.data.model.AppTheme
import com.luminar.reader.data.model.Bookmark
import com.luminar.reader.data.model.Book
import com.luminar.reader.data.model.BookFormat
import com.luminar.reader.data.model.FontScale
import com.luminar.reader.data.model.ScrollMode
import com.luminar.reader.data.repository.BookRepository
import com.luminar.reader.domain.usecase.SaveProgressUseCase
import com.luminar.reader.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    val book: Book? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val isLoading: Boolean = true,
    val showControls: Boolean = false,
    val currentTheme: AppTheme = AppTheme.DARK_AMOLED,
    val keepScreenOn: Boolean = true,
    val volumeButtonsPageTurn: Boolean = true,
    val error: String? = null,
    val textContent: String? = null,
    val epubChapters: List<EpubChapter>? = null,
    val comicImagePaths: List<String>? = null,
    val scrollMode: ScrollMode = ScrollMode.VERTICAL_SCROLL,
    val scrollPosition: Int = 0,
    val fontScale: FontScale = FontScale.NORMAL,
    val wordCount: Int = 0,
    val charCount: Int = 0,
    // Ebook chapter info
    val chapterTitles: List<String> = emptyList(),
    val currentChapterIndex: Int = 0,
    // Search state
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchMatchBlockIndices: List<Int> = emptyList(),
    val currentMatchIndex: Int = -1,
    val scrollToBlockIndex: Int? = null,
    val lastErrorThrowable: Throwable? = null,
    val showErrorReport: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
    val isCurrentPageBookmarked: Boolean = false
) {
    val totalChapters: Int
        get() = chapterTitles.size

    val currentChapterTitle: String
        get() = chapterTitles.getOrElse(currentChapterIndex) { "" }

    val chapterProgressLabel: String
        get() = if (totalChapters > 1)
            "Ch. ${currentChapterIndex + 1} of $totalChapters"
        else ""

    /** Estimated minutes remaining based on ~230 WPM average reading speed */
    val estimatedMinutesLeft: Int
        get() {
            if (wordCount <= 0 || totalPages <= 0) return 0
            val progress = if (totalPages > 0) (currentPage + 1).toFloat() / totalPages else 0f
            val wordsLeft = (wordCount * (1f - progress)).toInt().coerceAtLeast(0)
            return (wordsLeft / 230).coerceAtLeast(0)
        }

    val timeRemainingLabel: String
        get() {
            val mins = estimatedMinutesLeft
            return when {
                mins <= 0 -> ""
                mins < 60 -> "~${mins}m left"
                else -> "~${mins / 60}h ${mins % 60}m left"
            }
        }

    val isTextBased: Boolean
        get() = book?.format?.isTextBased == true

    val isEpub: Boolean
        get() = book?.format == BookFormat.EPUB

    val isDocument: Boolean
        get() = book?.format?.isDocumentFormat == true

    val isComicBook: Boolean
        get() = book?.format?.isComicBook == true

    /** True if content is rendered through TextReaderView (not PDF or comic) */
    val usesTextRenderer: Boolean
        get() = isTextBased || isEpub || isDocument

    val matchCount: Int
        get() = searchMatchBlockIndices.size

    val matchLabel: String
        get() = if (matchCount == 0 && searchQuery.isNotEmpty()) {
            "No matches"
        } else if (matchCount > 0) {
            "${currentMatchIndex + 1} / $matchCount"
        } else {
            ""
        }
}

sealed interface ReaderEvent {
    data class PageChanged(val page: Int) : ReaderEvent
    data object ToggleControls : ReaderEvent
    data class GoToPage(val page: Int) : ReaderEvent
    data class ThemeChanged(val theme: AppTheme) : ReaderEvent
    data class ScrollPositionChanged(val position: Int) : ReaderEvent
    data object ToggleScrollMode : ReaderEvent
    data object IncreaseFontSize : ReaderEvent
    data object DecreaseFontSize : ReaderEvent
    // Search events
    data object OpenSearch : ReaderEvent
    data object CloseSearch : ReaderEvent
    data class UpdateSearchQuery(val query: String) : ReaderEvent
    data object NextMatch : ReaderEvent
    data object PreviousMatch : ReaderEvent
    data object ShowErrorReport : ReaderEvent
    data object DismissErrorReport : ReaderEvent
    data class SendErrorReport(val userNote: String) : ReaderEvent
    // TTS
    data object ToggleTts : ReaderEvent
    data object TtsSkipForward : ReaderEvent
    data object TtsSkipBackward : ReaderEvent
    data class TtsSetSpeed(val speed: Float) : ReaderEvent
    // Bookmarks
    data object ToggleBookmark : ReaderEvent
    data class GoToBookmark(val bookmark: Bookmark) : ReaderEvent
    data class DeleteBookmark(val bookmark: Bookmark) : ReaderEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val bookDao: BookDao,
    private val saveProgressUseCase: SaveProgressUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val readerInputController: ReaderInputController,
    private val errorReporter: ErrorReporter,
    val ttsController: TtsController
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[Screen.Reader.BOOK_ID_ARG])

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState = _uiState.asStateFlow()

    private val pageChanges = MutableSharedFlow<Int>(
        extraBufferCapacity = 64
    )

    private var controlsAutoHideJob: Job? = null
    private var lastPersistedPage: Int = 0
    private val sessionStartTime = System.currentTimeMillis()

    // Cache parsed blocks for search
    private var parsedBlockTexts: List<String> = emptyList()

    init {
        observeBook()
        observeInitialProgress()
        observePreferences()
        observePageChanges()
        observeReaderCommands()
        observeBookmarks()
        markBookOpened()
    }

    fun onEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.PageChanged -> onPageChanged(event.page)
            ReaderEvent.ToggleControls -> toggleControls()
            is ReaderEvent.GoToPage -> goToPage(event.page)
            is ReaderEvent.ThemeChanged -> changeTheme(event.theme)
            is ReaderEvent.ScrollPositionChanged -> onScrollPositionChanged(event.position)
            ReaderEvent.ToggleScrollMode -> toggleScrollMode()
            ReaderEvent.IncreaseFontSize -> changeFontScale(increase = true)
            ReaderEvent.DecreaseFontSize -> changeFontScale(increase = false)
            ReaderEvent.ShowErrorReport -> _uiState.update { it.copy(showErrorReport = true) }
            ReaderEvent.DismissErrorReport -> _uiState.update {
                it.copy(showErrorReport = false, error = null, lastErrorThrowable = null)
            }
            is ReaderEvent.SendErrorReport -> sendErrorReport(event.userNote)
            ReaderEvent.ToggleTts -> toggleTts()
            ReaderEvent.TtsSkipForward -> ttsController.skipForward()
            ReaderEvent.TtsSkipBackward -> ttsController.skipBackward()
            is ReaderEvent.TtsSetSpeed -> ttsController.setSpeed(event.speed)
            ReaderEvent.ToggleBookmark -> toggleBookmark()
            is ReaderEvent.GoToBookmark -> goToPage(event.bookmark.page)
            is ReaderEvent.DeleteBookmark -> deleteBookmark(event.bookmark)
            ReaderEvent.OpenSearch -> openSearch()
            ReaderEvent.CloseSearch -> closeSearch()
            is ReaderEvent.UpdateSearchQuery -> updateSearchQuery(event.query)
            ReaderEvent.NextMatch -> navigateMatch(forward = true)
            ReaderEvent.PreviousMatch -> navigateMatch(forward = false)
        }
    }

    fun onPdfLoaded(totalPages: Int) {
        if (totalPages <= 0) return

        _uiState.update { state ->
            val maxPage = (totalPages - 1).coerceAtLeast(0)
            state.copy(
                totalPages = totalPages,
                currentPage = state.currentPage.coerceIn(0, maxPage)
            )
        }
    }

    fun onControlsInteraction() {
        if (_uiState.value.showControls) {
            scheduleControlsAutoHide()
        }
    }

    fun setReaderVisible(visible: Boolean) {
        readerInputController.isReaderOpen = visible
    }

    fun saveCurrentProgressImmediately() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.usesTextRenderer || state.isComicBook) {
                saveProgressUseCase(
                    bookId = bookId,
                    currentPage = state.scrollPosition,
                    scrollOffset = 0f
                )
            } else {
                persistPage(
                    page = state.currentPage,
                    force = true
                )
            }
        }
    }

    /**
     * Called by TextReaderView after it has parsed its blocks,
     * so the VM can search through them.
     */
    fun onBlocksParsed(blockTexts: List<String>) {
        parsedBlockTexts = blockTexts
        // Re-run search if there's an active query
        val query = _uiState.value.searchQuery
        if (query.isNotEmpty()) {
            performSearch(query)
        }
    }

    private fun toggleTts() {
        val state = _uiState.value
        val ttsState = ttsController.state.value
        if (ttsState.isSpeaking) {
            ttsController.pause()
        } else {
            val text = state.textContent
            if (text != null) {
                ttsController.startSpeaking(text)
            }
        }
        cancelControlsAutoHide()
    }

    override fun onCleared() {
        super.onCleared()
        ttsController.stop()
        // Record reading session duration
        val durationMinutes = (System.currentTimeMillis() - sessionStartTime) / 60_000
        if (durationMinutes > 0) {
            viewModelScope.launch {
                userPreferencesRepository.recordReadingSession(durationMinutes)
            }
        }
    }

    // ─── Bookmarks ────────────────────────────────────────────

    private fun observeBookmarks() {
        viewModelScope.launch {
            bookDao.getBookmarks(bookId).collect { bookmarks ->
                _uiState.update { state ->
                    val currentPage = state.currentPage
                    state.copy(
                        bookmarks = bookmarks,
                        isCurrentPageBookmarked = bookmarks.any { it.page == currentPage }
                    )
                }
            }
        }
    }

    private fun toggleBookmark() {
        val state = _uiState.value
        val currentPage = if (state.usesTextRenderer) state.scrollPosition else state.currentPage
        val existing = state.bookmarks.find { it.page == currentPage }

        viewModelScope.launch {
            if (existing != null) {
                bookDao.deleteBookmark(existing)
            } else {
                bookDao.insertBookmark(
                    Bookmark(
                        bookId = bookId,
                        page = currentPage,
                        label = "Page ${currentPage + 1}"
                    )
                )
            }
        }
    }

    private fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookDao.deleteBookmark(bookmark)
        }
    }

    private fun sendErrorReport(userNote: String) {
        val state = _uiState.value
        viewModelScope.launch {
            errorReporter.report(
                errorType = "reader_error",
                errorMessage = state.error ?: "Unknown",
                throwable = state.lastErrorThrowable,
                context = "Reader — ${state.book?.format?.displayName ?: "unknown"} file",
                fileFormat = state.book?.format?.name,
                userNote = userNote
            )
            _uiState.update {
                it.copy(showErrorReport = false, error = null, lastErrorThrowable = null)
            }
        }
    }

    // ─── Search ──────────────────────────────────────────────

    private fun openSearch() {
        cancelControlsAutoHide()
        _uiState.update {
            it.copy(
                isSearchActive = true,
                showControls = false
            )
        }
    }

    private fun closeSearch() {
        _uiState.update {
            it.copy(
                isSearchActive = false,
                searchQuery = "",
                searchMatchBlockIndices = emptyList(),
                currentMatchIndex = -1,
                scrollToBlockIndex = null
            )
        }
    }

    private fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.length >= 2) {
            performSearch(query)
        } else {
            _uiState.update {
                it.copy(
                    searchMatchBlockIndices = emptyList(),
                    currentMatchIndex = -1,
                    scrollToBlockIndex = null
                )
            }
        }
    }

    private fun performSearch(query: String) {
        val lowerQuery = query.lowercase()
        val matches = parsedBlockTexts.indices.filter { index ->
            parsedBlockTexts[index].lowercase().contains(lowerQuery)
        }

        _uiState.update {
            it.copy(
                searchMatchBlockIndices = matches,
                currentMatchIndex = if (matches.isNotEmpty()) 0 else -1,
                scrollToBlockIndex = matches.firstOrNull()
            )
        }
    }

    private fun navigateMatch(forward: Boolean) {
        val state = _uiState.value
        if (state.searchMatchBlockIndices.isEmpty()) return

        val newIndex = if (forward) {
            (state.currentMatchIndex + 1) % state.matchCount
        } else {
            (state.currentMatchIndex - 1 + state.matchCount) % state.matchCount
        }

        _uiState.update {
            it.copy(
                currentMatchIndex = newIndex,
                scrollToBlockIndex = it.searchMatchBlockIndices[newIndex]
            )
        }
    }

    /** Called by the UI after it has consumed the scroll-to request. */
    fun consumeScrollToBlock() {
        _uiState.update { it.copy(scrollToBlockIndex = null) }
    }

    // ─── Book loading ────────────────────────────────────────

    private fun observeBook() {
        viewModelScope.launch {
            bookRepository.getBookById(bookId)
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Unable to load book"
                        )
                    }
                }
                .collect { book ->
                    val totalPages = book?.totalPages ?: 0
                    val maxPage = (totalPages - 1).coerceAtLeast(0)

                    _uiState.update { state ->
                        state.copy(
                            book = book,
                            totalPages = totalPages,
                            currentPage = state.currentPage.coerceIn(0, maxPage),
                            isLoading = false,
                            error = if (book == null) "Book not found" else null
                        )
                    }

                    if (book != null) {
                        when {
                            book.format.isComicBook && _uiState.value.comicImagePaths == null -> loadComicPages(book)
                            book.format == BookFormat.EPUB && _uiState.value.textContent == null -> loadEpubChapters(book)
                            book.format.isDocumentFormat && _uiState.value.textContent == null -> loadDocumentContent(book)
                            book.format.isTextBased && _uiState.value.textContent == null -> loadTextContent(book)
                        }
                    }
                }
        }
    }

    private fun loadEpubChapters(book: Book) {
        viewModelScope.launch {
            try {
                val chapters = bookRepository.readEpubChapters(book)
                // Convert to markdown for search/text features
                val fullText = epubChaptersToMarkdown(chapters)
                val words = fullText.split(Regex("\\s+")).count { it.isNotBlank() }
                val chars = fullText.length
                _uiState.update {
                    it.copy(
                        epubChapters = chapters,
                        textContent = fullText,
                        wordCount = words,
                        charCount = chars,
                        totalPages = chapters.size.coerceAtLeast(1),
                        chapterTitles = chapters.map { ch -> ch.title }
                    )
                }
            } catch (throwable: Throwable) {
                _uiState.update {
                    it.copy(error = "Unable to read EPUB: ${throwable.message}", lastErrorThrowable = throwable, showErrorReport = true)
                }
            }
        }
    }

    private fun loadComicPages(book: Book) {
        viewModelScope.launch {
            try {
                val paths = bookRepository.getComicImagePaths(book)
                _uiState.update {
                    it.copy(
                        comicImagePaths = paths,
                        totalPages = paths.size.coerceAtLeast(1)
                    )
                }
            } catch (throwable: Throwable) {
                _uiState.update {
                    it.copy(error = "Unable to read comic: ${throwable.message}", lastErrorThrowable = throwable, showErrorReport = true)
                }
            }
        }
    }

    fun getComicImageBytes(entryPath: String): ByteArray {
        val book = _uiState.value.book ?: throw IllegalStateException("No book loaded")
        return bookRepository.getComicImageBytes(book, entryPath)
    }

    private fun loadDocumentContent(book: Book) {
        viewModelScope.launch {
            try {
                val content = bookRepository.readDocumentContent(book)
                val words = content.split(Regex("\\s+")).count { it.isNotBlank() }
                val chars = content.length
                _uiState.update {
                    it.copy(
                        textContent = content,
                        wordCount = words,
                        charCount = chars
                    )
                }
            } catch (throwable: Throwable) {
                _uiState.update {
                    it.copy(error = "Unable to read document: ${throwable.message}", lastErrorThrowable = throwable, showErrorReport = true)
                }
            }
        }
    }

    private fun loadTextContent(book: Book) {
        viewModelScope.launch {
            try {
                val content = bookRepository.readTextContent(book)
                val words = content.split(Regex("\\s+")).count { it.isNotBlank() }
                val chars = content.length
                _uiState.update {
                    it.copy(
                        textContent = content,
                        wordCount = words,
                        charCount = chars
                    )
                }
            } catch (throwable: Throwable) {
                _uiState.update {
                    it.copy(error = "Unable to read file: ${throwable.message}", lastErrorThrowable = throwable, showErrorReport = true)
                }
            }
        }
    }

    private fun observeInitialProgress() {
        viewModelScope.launch {
            val progress = bookRepository.getProgress(bookId).first()
            if (progress != null) {
                lastPersistedPage = progress.currentPage
                _uiState.update {
                    it.copy(
                        currentPage = progress.currentPage.coerceAtLeast(0),
                        scrollPosition = progress.currentPage.coerceAtLeast(0)
                    )
                }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collect { preferences ->
                _uiState.update {
                    it.copy(
                        currentTheme = preferences.selectedTheme,
                        keepScreenOn = preferences.keepScreenOn,
                        volumeButtonsPageTurn = preferences.volumeButtonsPageTurn,
                        fontScale = preferences.fontScale,
                        scrollMode = preferences.defaultScrollMode
                    )
                }
            }
        }
    }

    private fun observePageChanges() {
        viewModelScope.launch {
            pageChanges
                .debounce(650)
                .collect { page ->
                    persistPage(page = page, force = false)
                }
        }
    }

    private fun observeReaderCommands() {
        viewModelScope.launch {
            readerInputController.commands.collect { command ->
                when (command) {
                    ReaderCommand.PreviousPage -> previousPage()
                    ReaderCommand.NextPage -> nextPage()
                }
            }
        }
    }

    private fun markBookOpened() {
        viewModelScope.launch {
            bookRepository.markBookOpened(bookId)
            userPreferencesRepository.incrementBooksOpened()
        }
    }

    // ─── Page / scroll ───────────────────────────────────────

    private fun onPageChanged(page: Int) {
        val target = page.coerceIn(0, maxPage())
        if (target == _uiState.value.currentPage) return
        _uiState.update {
            it.copy(
                currentPage = target,
                isCurrentPageBookmarked = it.bookmarks.any { bm -> bm.page == target }
            )
        }
        pageChanges.tryEmit(target)
    }

    private fun onScrollPositionChanged(position: Int) {
        _uiState.update { it.copy(scrollPosition = position) }
        pageChanges.tryEmit(position)
    }

    private fun toggleScrollMode() {
        val newMode = _uiState.value.scrollMode.next()
        _uiState.update { it.copy(scrollMode = newMode) }
        onControlsInteraction()
        viewModelScope.launch {
            userPreferencesRepository.setDefaultScrollMode(newMode)
        }
    }

    private fun changeFontScale(increase: Boolean) {
        val newScale = if (increase) {
            _uiState.value.fontScale.next()
        } else {
            _uiState.value.fontScale.previous()
        }
        _uiState.update { it.copy(fontScale = newScale) }
        onControlsInteraction()
        viewModelScope.launch {
            userPreferencesRepository.setFontScale(newScale)
        }
    }

    private fun previousPage() {
        goToPage(_uiState.value.currentPage - 1)
    }

    private fun nextPage() {
        goToPage(_uiState.value.currentPage + 1)
    }

    private fun goToPage(page: Int) {
        val target = page.coerceIn(0, maxPage())
        _uiState.update { it.copy(currentPage = target) }
        pageChanges.tryEmit(target)
        onControlsInteraction()
    }

    // ─── Controls ────────────────────────────────────────────

    private fun toggleControls() {
        if (_uiState.value.isSearchActive) return // don't toggle controls while searching

        val shouldShow = !_uiState.value.showControls
        _uiState.update { it.copy(showControls = shouldShow) }

        if (shouldShow) {
            scheduleControlsAutoHide()
        } else {
            cancelControlsAutoHide()
        }
    }

    private fun changeTheme(theme: AppTheme) {
        _uiState.update { it.copy(currentTheme = theme) }
        onControlsInteraction()
        viewModelScope.launch {
            userPreferencesRepository.setSelectedTheme(theme)
        }
    }

    private fun scheduleControlsAutoHide() {
        controlsAutoHideJob?.cancel()
        controlsAutoHideJob = viewModelScope.launch {
            delay(4_500)
            _uiState.update { it.copy(showControls = false) }
        }
    }

    private fun cancelControlsAutoHide() {
        controlsAutoHideJob?.cancel()
    }

    private suspend fun persistPage(page: Int, force: Boolean) {
        val book = _uiState.value.book ?: return
        val target = if (book.format.isTextBased || book.format == BookFormat.EPUB || book.format.isDocumentFormat || book.format.isComicBook) page else page.coerceIn(0, maxPage())

        if (!force && abs(target - lastPersistedPage) < 3) return

        saveProgressUseCase(bookId = book.id, currentPage = target, scrollOffset = 0f)
        lastPersistedPage = target
    }

    private fun maxPage(): Int {
        return (_uiState.value.totalPages - 1).coerceAtLeast(0)
    }
}
