// app/src/main/java/com/luminar/reader/presentation/library/LibraryViewModel.kt
package com.luminar.reader.presentation.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminar.reader.data.error.ErrorReporter
import com.luminar.reader.data.model.Book
import com.luminar.reader.data.model.BookFormat
import com.luminar.reader.data.model.ReadingProgress
import com.luminar.reader.data.repository.BookRepository
import com.luminar.reader.domain.usecase.GetBooksUseCase
import com.luminar.reader.domain.usecase.ImportBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) {
    RECENT("Recent"),
    TITLE("Title"),
    DATE_ADDED("Added"),
    FILE_SIZE("Size")
}

enum class FormatFilter(val label: String) {
    ALL("All"),
    PDF("PDF"),
    EPUB("EPUB"),
    COMICS("Comics"),
    DOCS("Docs"),
    MARKDOWN("Markdown"),
    CODE("Code"),
    TEXT("Text"),
    OTHER("Other")
}

enum class ViewMode {
    GRID, LIST
}

data class LibraryUiState(
    val allBooks: List<Book> = emptyList(),
    val progressByBookId: Map<Long, ReadingProgress> = emptyMap(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val error: String? = null,
    val lastErrorThrowable: Throwable? = null,
    val showErrorReport: Boolean = false,
    val sortOrder: SortOrder = SortOrder.RECENT,
    val formatFilter: FormatFilter = FormatFilter.ALL,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val viewMode: ViewMode = ViewMode.GRID
) {
    val books: List<Book>
        get() {
            var result = allBooks

            // Filter by format
            result = when (formatFilter) {
                FormatFilter.ALL -> result
                FormatFilter.PDF -> result.filter { it.format == BookFormat.PDF }
                FormatFilter.EPUB -> result.filter { it.format == BookFormat.EPUB }
                FormatFilter.COMICS -> result.filter { it.format.isComicBook }
                FormatFilter.DOCS -> result.filter { it.format.isDocumentFormat }
                FormatFilter.MARKDOWN -> result.filter { it.format == BookFormat.MARKDOWN }
                FormatFilter.CODE -> result.filter { it.format == BookFormat.CODE }
                FormatFilter.TEXT -> result.filter { it.format == BookFormat.TXT || it.format == BookFormat.LOG }
                FormatFilter.OTHER -> result.filter {
                    it.format != BookFormat.PDF && it.format != BookFormat.EPUB &&
                    !it.format.isComicBook && !it.format.isDocumentFormat &&
                    it.format != BookFormat.MARKDOWN && it.format != BookFormat.CODE &&
                    it.format != BookFormat.TXT && it.format != BookFormat.LOG
                }
            }

            // Filter by search query
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.lowercase()
                result = result.filter { it.title.lowercase().contains(query) }
            }

            // Sort
            result = when (sortOrder) {
                SortOrder.RECENT -> result.sortedByDescending { it.lastOpenedAt ?: it.addedAt }
                SortOrder.TITLE -> result.sortedBy { it.title.lowercase() }
                SortOrder.DATE_ADDED -> result.sortedByDescending { it.addedAt }
                SortOrder.FILE_SIZE -> result.sortedByDescending {
                    runCatching { File(it.filePath).length() }.getOrDefault(0L)
                }
            }

            return result
        }

    val formatCounts: Map<FormatFilter, Int>
        get() {
            val counts = mutableMapOf<FormatFilter, Int>()
            counts[FormatFilter.ALL] = allBooks.size
            counts[FormatFilter.PDF] = allBooks.count { it.format == BookFormat.PDF }
            counts[FormatFilter.EPUB] = allBooks.count { it.format == BookFormat.EPUB }
            counts[FormatFilter.COMICS] = allBooks.count { it.format.isComicBook }
            counts[FormatFilter.DOCS] = allBooks.count { it.format.isDocumentFormat }
            counts[FormatFilter.MARKDOWN] = allBooks.count { it.format == BookFormat.MARKDOWN }
            counts[FormatFilter.CODE] = allBooks.count { it.format == BookFormat.CODE }
            counts[FormatFilter.TEXT] = allBooks.count { it.format == BookFormat.TXT || it.format == BookFormat.LOG }
            counts[FormatFilter.OTHER] = allBooks.size -
                (counts[FormatFilter.PDF]!! + counts[FormatFilter.EPUB]!! +
                 counts[FormatFilter.COMICS]!! + counts[FormatFilter.DOCS]!! +
                 counts[FormatFilter.MARKDOWN]!! + counts[FormatFilter.CODE]!! +
                 counts[FormatFilter.TEXT]!!)
            return counts
        }
}

sealed interface LibraryEvent {
    data class ImportFile(val uri: Uri) : LibraryEvent
    data class DeleteBook(val book: Book) : LibraryEvent
    data class OpenBook(val book: Book) : LibraryEvent
    data class SetSortOrder(val order: SortOrder) : LibraryEvent
    data class SetFormatFilter(val filter: FormatFilter) : LibraryEvent
    data class UpdateSearchQuery(val query: String) : LibraryEvent
    data object ToggleSearch : LibraryEvent
    data object ToggleViewMode : LibraryEvent
    data object ShowErrorReport : LibraryEvent
    data object DismissErrorReport : LibraryEvent
    data class SendErrorReport(val userNote: String) : LibraryEvent

    @Suppress("unused")
    companion object {
        fun ImportPdf(uri: Uri): LibraryEvent = ImportFile(uri)
    }
}

sealed interface LibraryEffect {
    data class NavigateToReader(val bookId: Long) : LibraryEffect
    data class ShowSnackbar(val message: String) : LibraryEffect
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getBooksUseCase: GetBooksUseCase,
    private val importBookUseCase: ImportBookUseCase,
    private val bookRepository: BookRepository,
    private val errorReporter: ErrorReporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<LibraryEffect>()
    val effects = _effects.asSharedFlow()

    init {
        observeLibrary()
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.ImportFile -> importFile(event.uri)
            is LibraryEvent.DeleteBook -> deleteBook(event.book)
            is LibraryEvent.OpenBook -> openBook(event.book)
            is LibraryEvent.SetSortOrder -> _uiState.update { it.copy(sortOrder = event.order) }
            is LibraryEvent.SetFormatFilter -> _uiState.update { it.copy(formatFilter = event.filter) }
            is LibraryEvent.UpdateSearchQuery -> _uiState.update { it.copy(searchQuery = event.query) }
            LibraryEvent.ToggleSearch -> _uiState.update {
                it.copy(
                    isSearchActive = !it.isSearchActive,
                    searchQuery = if (it.isSearchActive) "" else it.searchQuery
                )
            }
            LibraryEvent.ToggleViewMode -> _uiState.update {
                it.copy(viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
            }
            LibraryEvent.ShowErrorReport -> _uiState.update { it.copy(showErrorReport = true) }
            LibraryEvent.DismissErrorReport -> _uiState.update {
                it.copy(showErrorReport = false, error = null, lastErrorThrowable = null)
            }
            is LibraryEvent.SendErrorReport -> sendErrorReport(event.userNote)
        }
    }

    private fun sendErrorReport(userNote: String) {
        val state = _uiState.value
        viewModelScope.launch {
            errorReporter.report(
                errorType = "library_error",
                errorMessage = state.error ?: "Unknown",
                throwable = state.lastErrorThrowable,
                context = "Library — importing or loading books",
                userNote = userNote
            )
            _uiState.update {
                it.copy(showErrorReport = false, error = null, lastErrorThrowable = null)
            }
        }
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            combine(
                getBooksUseCase(),
                bookRepository.getAllProgress()
            ) { books, progress ->
                books to progress.associateBy { it.bookId }
            }
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Unable to load library"
                        )
                    }
                }
                .collect { (books, progressByBookId) ->
                    _uiState.update {
                        it.copy(
                            allBooks = books,
                            progressByBookId = progressByBookId,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    private fun importFile(uri: Uri) {
        if (_uiState.value.isImporting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }

            runCatching {
                importBookUseCase(uri)
            }.onSuccess {
                _effects.emit(LibraryEffect.ShowSnackbar("File added"))
            }.onFailure { throwable ->
                val message = throwable.message ?: "Unable to import file"
                _uiState.update {
                    it.copy(
                        error = message,
                        lastErrorThrowable = throwable,
                        showErrorReport = true
                    )
                }
            }

            _uiState.update { it.copy(isImporting = false) }
        }
    }

    private fun deleteBook(book: Book) {
        viewModelScope.launch {
            runCatching {
                bookRepository.deleteBook(book)
            }.onSuccess {
                _effects.emit(LibraryEffect.ShowSnackbar("Book removed"))
            }.onFailure { throwable ->
                _effects.emit(
                    LibraryEffect.ShowSnackbar(
                        throwable.message ?: "Unable to remove book"
                    )
                )
            }
        }
    }

    private fun openBook(book: Book) {
        viewModelScope.launch {
            if (File(book.filePath).exists()) {
                _effects.emit(LibraryEffect.NavigateToReader(book.id))
            } else {
                _effects.emit(
                    LibraryEffect.ShowSnackbar(
                        "File not found. Remove it from the library."
                    )
                )
            }
        }
    }
}
