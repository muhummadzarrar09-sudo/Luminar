// app/src/main/java/com/luminar/reader/presentation/library/LibraryViewModel.kt
package com.luminar.reader.presentation.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

import com.luminar.reader.data.local.db.ReadingSessionDao

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val progressByBookId: Map<Long, ReadingProgress> = emptyMap(),
    val todayMinutesByBookId: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val error: String? = null
)

sealed interface LibraryEvent {
    data class ImportPdf(val uri: Uri) : LibraryEvent
    data class DeleteBook(val book: Book) : LibraryEvent
    data class OpenBook(val book: Book) : LibraryEvent
}

sealed interface LibraryEffect {
    data class NavigateToReader(val bookId: Long) : LibraryEffect
    data class NavigateToEpubReader(val bookId: Long) : LibraryEffect
    data class ShowSnackbar(val message: String) : LibraryEffect
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getBooksUseCase: GetBooksUseCase,
    private val importBookUseCase: ImportBookUseCase,
    private val bookRepository: BookRepository,
    private val readingSessionDao: ReadingSessionDao
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
            is LibraryEvent.ImportPdf -> importPdf(event.uri)
            is LibraryEvent.DeleteBook -> deleteBook(event.book)
            is LibraryEvent.OpenBook -> openBook(event.book)
        }
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            combine(
                getBooksUseCase(),
                bookRepository.getAllProgress(),
                readingSessionDao.getAllBooksTodayMinutes()
            ) { books, progress, minutes ->
                Triple(books, progress.associateBy { it.bookId }, minutes.associate { it.bookId to it.minutes })
            }
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Unable to load library"
                        )
                    }
                }
                .collect { (books, progressByBookId, minsByBookId) ->
                    _uiState.update {
                        it.copy(
                            books = books,
                            progressByBookId = progressByBookId,
                            todayMinutesByBookId = minsByBookId,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    private fun importPdf(uri: Uri) {
        if (_uiState.value.isImporting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }

            runCatching {
                importBookUseCase(uri)
            }.onSuccess {
                _effects.emit(LibraryEffect.ShowSnackbar("Book added"))
            }.onFailure { throwable ->
                val message = throwable.message ?: "Unable to import PDF"
                _uiState.update { it.copy(error = message) }
                _effects.emit(LibraryEffect.ShowSnackbar(message))
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
                if (book.format == BookFormat.EPUB) {
                    _effects.emit(LibraryEffect.NavigateToEpubReader(book.id))
                } else {
                    _effects.emit(LibraryEffect.NavigateToReader(book.id))
                }
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
