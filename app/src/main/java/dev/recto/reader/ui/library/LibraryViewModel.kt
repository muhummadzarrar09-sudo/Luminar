package dev.recto.reader.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.recto.reader.data.BookRepository
import dev.recto.reader.data.ImportResult
import dev.recto.reader.data.db.BookEntity
import dev.recto.reader.data.db.RectoDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookRepository(
        context = app,
        dao = RectoDatabase.get(app).bookDao()
    )

    val books: StateFlow<List<BookEntity>> = repo.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val current: StateFlow<BookEntity?> = repo.observeCurrent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun importAll(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true

            var added = 0
            var duplicate = 0
            var unsupported = 0
            var failed = 0
            var lastTitle: String? = null
            var firstError: String? = null

            for (uri in uris) {
                when (val result = repo.import(uri)) {
                    is ImportResult.Added -> { added++; lastTitle = result.title }
                    is ImportResult.Duplicate -> duplicate++
                    is ImportResult.Unsupported -> unsupported++
                    is ImportResult.Failed -> {
                        failed++
                        if (firstError == null) firstError = result.reason
                    }
                }
            }

            _importing.value = false
            _message.value = summarise(added, duplicate, unsupported, failed, lastTitle, firstError)
        }
    }

    private fun summarise(
        added: Int,
        duplicate: Int,
        unsupported: Int,
        failed: Int,
        lastTitle: String?,
        firstError: String?
    ): String = when {
        added == 1 && duplicate + unsupported + failed == 0 ->
            "Added ${lastTitle ?: "1 book"}"

        added > 1 && duplicate + unsupported + failed == 0 ->
            "Added $added books"

        added == 0 && duplicate > 0 && unsupported + failed == 0 ->
            if (duplicate == 1) "Already in your library" else "$duplicate already in your library"

        added == 0 && unsupported > 0 && failed == 0 ->
            if (unsupported == 1) "That file type is not supported yet"
            else "$unsupported files are not supported yet"

        added == 0 && failed > 0 ->
            "Import failed: ${firstError ?: "unknown error"}"

        else -> buildList {
            if (added > 0) add("$added added")
            if (duplicate > 0) add("$duplicate already there")
            if (unsupported > 0) add("$unsupported unsupported")
            if (failed > 0) add("$failed failed")
        }.joinToString(", ")
    }

    fun open(book: BookEntity) {
        viewModelScope.launch { repo.touch(book.id) }
    }

    fun delete(book: BookEntity) {
        viewModelScope.launch { repo.delete(book) }
    }

    fun clearMessage() {
        _message.value = null
    }
}
