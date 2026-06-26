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

data class EpubReaderUiState(
    val book: Book? = null,
    val publication: Publication? = null,
    val initialCfi: String? = null,
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
        viewModelScope.launch {
            saveProgressUseCase(
                bookId = book.id,
                currentPage = 0,
                scrollOffset = 0f,
                epubCfi = cfiString
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

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        currentTheme = prefs.selectedTheme,
                        keepScreenOn = prefs.keepScreenOn,
                        volumeButtonsPageTurn = prefs.volumeButtonsPageTurn
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
