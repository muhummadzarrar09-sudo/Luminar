// app/src/main/java/com/luminar/reader/presentation/reader/ReaderViewModel.kt
package com.luminar.reader.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminar.reader.data.local.datastore.UserPreferencesRepository
import com.luminar.reader.data.model.AppTheme
import com.luminar.reader.data.model.Book
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

import com.luminar.reader.data.local.db.BookTocDao
import com.luminar.reader.data.model.BookToc

data class ReaderUiState(
    val book: Book? = null,
    val tocItems: List<BookToc> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val savedZoom: Float = 1.0f,
    val isLoading: Boolean = true,
    val showControls: Boolean = false,
    val currentTheme: AppTheme = AppTheme.DARK_AMOLED,
    val keepScreenOn: Boolean = true,
    val volumeButtonsPageTurn: Boolean = true,
    val error: String? = null
)

sealed interface ReaderEvent {
    data class PageChanged(val page: Int) : ReaderEvent
    data object ToggleControls : ReaderEvent
    data class GoToPage(val page: Int) : ReaderEvent
    data class ThemeChanged(val theme: AppTheme) : ReaderEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val bookTocDao: BookTocDao,
    private val saveProgressUseCase: SaveProgressUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val readerInputController: ReaderInputController
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[Screen.Reader.BOOK_ID_ARG])

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState = _uiState.asStateFlow()

    private val pageChanges = MutableSharedFlow<Int>(
        extraBufferCapacity = 64
    )
    private val zoomChanges = MutableSharedFlow<Float>(
        extraBufferCapacity = 64
    )

    private var controlsAutoHideJob: Job? = null
    private var lastPersistedPage: Int = 0
    private var lastPersistedZoom: Float = 1.0f

    init {
        observeBook()
        observeToc()
        observeInitialProgress()
        observePreferences()
        observePageChanges()
        observeZoomChanges()
        observeReaderCommands()
        markBookOpened()
    }

    fun onEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.PageChanged -> onPageChanged(event.page)
            ReaderEvent.ToggleControls -> toggleControls()
            is ReaderEvent.GoToPage -> goToPage(event.page)
            is ReaderEvent.ThemeChanged -> changeTheme(event.theme)
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

    fun onZoomChanged(zoom: Float) {
        if (abs(zoom - lastPersistedZoom) < 0.05f) return
        zoomChanges.tryEmit(zoom)
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
            persistPage(
                page = _uiState.value.currentPage,
                force = true
            )
        }
    }

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

    private fun observeInitialProgress() {
        viewModelScope.launch {
            val progress = bookRepository.getProgress(bookId).first()
            if (progress != null) {
                lastPersistedPage = progress.currentPage
                lastPersistedZoom = progress.lastZoomLevel
                _uiState.update {
                    it.copy(
                        currentPage = progress.currentPage.coerceAtLeast(0),
                        savedZoom = progress.lastZoomLevel
                    )
                }
            }
        }
    }

    private fun observeZoomChanges() {
        viewModelScope.launch {
            zoomChanges.debounce(500).collect { zoom ->
                persistZoom(zoom)
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
                        volumeButtonsPageTurn = preferences.volumeButtonsPageTurn
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
        }
    }

    private fun onPageChanged(page: Int) {
        val target = page.coerceIn(0, maxPage())
        if (target == _uiState.value.currentPage) return

        _uiState.update { it.copy(currentPage = target) }
        pageChanges.tryEmit(target)
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

    private fun toggleControls() {
        val shouldShow = !_uiState.value.showControls
        _uiState.update { it.copy(showControls = shouldShow) }

        if (shouldShow) {
            scheduleControlsAutoHide()
        } else {
            controlsAutoHideJob?.cancel()
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
            delay(3_000)
            _uiState.update { it.copy(showControls = false) }
        }
    }

    private suspend fun persistPage(page: Int, force: Boolean) {
        val book = _uiState.value.book ?: return
        val target = page.coerceIn(0, maxPage())

        if (!force && abs(target - lastPersistedPage) < 3) {
            return
        }

        saveProgressUseCase(
            bookId = book.id,
            currentPage = target,
            scrollOffset = 0f,
            zoomLevel = _uiState.value.savedZoom
        )
        lastPersistedPage = target
    }

    private suspend fun persistZoom(zoom: Float) {
        val book = _uiState.value.book ?: return
        _uiState.update { it.copy(savedZoom = zoom) }
        saveProgressUseCase(
            bookId = book.id,
            currentPage = _uiState.value.currentPage,
            scrollOffset = 0f,
            zoomLevel = zoom
        )
        lastPersistedZoom = zoom
    }

    private fun maxPage(): Int {
        return (_uiState.value.totalPages - 1).coerceAtLeast(0)
    }
}
