package dev.recto.reader.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.recto.reader.data.BookRepository
import dev.recto.reader.data.ImportResult
import dev.recto.reader.data.ImportSource
import dev.recto.reader.data.db.BookCollectionCrossRef
import dev.recto.reader.data.db.BookEntity
import dev.recto.reader.data.db.CollectionEntity
import dev.recto.reader.data.db.CollectionWithCount
import dev.recto.reader.data.db.RectoDatabase
import dev.recto.reader.data.search.LibrarySearchProgress
import dev.recto.reader.data.search.LibrarySearcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val db = RectoDatabase.get(app)
    private val repo = BookRepository(context = app, dao = db.bookDao())
    private val collectionDao = db.collectionDao()

    /** Null means "All books"; otherwise filter to one collection. */
    private val _selectedCollection = MutableStateFlow<Long?>(null)
    val selectedCollection: StateFlow<Long?> = _selectedCollection.asStateFlow()

    val collections: StateFlow<List<CollectionWithCount>> = collectionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val books: StateFlow<List<BookEntity>> = _selectedCollection
        .flatMapLatest { id ->
            if (id == null) repo.observeBooks() else collectionDao.observeBooksIn(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val searcher = LibrarySearcher(repo)

    /**
     * Title/author filter. Instant, since it is just a predicate over rows
     * already in memory.
     */
    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter.asStateFlow()

    /**
     * Full-text search across every book, regardless of shelf. This is the
     * "which book was that in?" failsafe - deliberately separate from the
     * quick title filter, because it costs seconds rather than milliseconds.
     */
    private val _deepSearch = MutableStateFlow(LibrarySearchProgress())
    val deepSearch: StateFlow<LibrarySearchProgress> = _deepSearch.asStateFlow()

    private var deepSearchJob: Job? = null

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Ids of books picked in multi-select mode. Empty means not selecting. */
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _openImmediately = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val openImmediately: SharedFlow<Long> = _openImmediately.asSharedFlow()

    // --- import -------------------------------------------------------------

    fun importFromPicker(uris: List<Uri>) =
        import(uris, ImportSource.PICKER, openAfter = false)

    fun importFromIntent(uris: List<Uri>) =
        import(uris, ImportSource.EXTERNAL, openAfter = true)

    private fun import(uris: List<Uri>, source: ImportSource, openAfter: Boolean) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true

            var added = 0
            var duplicate = 0
            var unsupported = 0
            var failed = 0
            var lastTitle: String? = null
            var firstError: String? = null
            var openId: Long? = null

            for (uri in uris) {
                when (val result = repo.import(uri, source)) {
                    is ImportResult.Added -> {
                        added++
                        lastTitle = result.title
                        if (openId == null) openId = result.id
                    }
                    is ImportResult.Duplicate -> {
                        duplicate++
                        lastTitle = result.title
                        if (openId == null && result.id > 0) openId = result.id
                    }
                    is ImportResult.Unsupported -> unsupported++
                    is ImportResult.Failed -> {
                        failed++
                        if (firstError == null) firstError = result.reason
                    }
                }
            }

            _importing.value = false

            // Opening a single book is its own feedback - except when it was a
            // duplicate, where saying so explains why no new cover appeared.
            val silent = uris.size == 1 && openAfter && openId != null && duplicate == 0
            if (!silent) {
                _message.value =
                    summarise(added, duplicate, unsupported, failed, lastTitle, firstError)
            }

            if (openAfter) openId?.let { _openImmediately.emit(it) }
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

        added == 0 && duplicate == 1 && unsupported + failed == 0 ->
            "Already in your library - opening it"

        added == 0 && duplicate > 1 && unsupported + failed == 0 ->
            "$duplicate already in your library"

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

    // --- search ---------------------------------------------------------------

    fun setFilter(text: String) {
        _filter.value = text
        // Changing the filter invalidates any deep search in flight; its
        // results were for a different query.
        if (text.isBlank()) cancelDeepSearch()
    }

    /**
     * Searches inside every book in the library, ignoring the shelf filter -
     * the whole point is to find a book you cannot locate.
     *
     * Results stream in per book, so the first hits appear in a few hundred
     * milliseconds rather than after the entire library has been scanned.
     */
    fun runDeepSearch() {
        val query = _filter.value.trim()
        if (query.length < 2) return

        deepSearchJob?.cancel()
        deepSearchJob = viewModelScope.launch {
            // Always search the full library, not the current shelf.
            val all = repo.observeBooks().first()
            searcher.search(all, query).collect { progress ->
                _deepSearch.value = progress
            }
        }
    }

    fun cancelDeepSearch() {
        deepSearchJob?.cancel()
        deepSearchJob = null
        _deepSearch.value = LibrarySearchProgress()
    }

    // --- selection ----------------------------------------------------------

    fun toggleSelection(id: Long) {
        _selection.value = _selection.value.let {
            if (id in it) it - id else it + id
        }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun selectAll() {
        _selection.value = books.value.map { it.id }.toSet()
    }

    // --- delete -------------------------------------------------------------

    fun deleteSelected() {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val toDelete = books.value.filter { it.id in ids }
            toDelete.forEach { repo.delete(it) }
            _selection.value = emptySet()
            _message.value =
                if (toDelete.size == 1) "Removed ${toDelete.first().title}"
                else "Removed ${toDelete.size} books"
        }
    }

    fun delete(book: BookEntity) {
        viewModelScope.launch {
            repo.delete(book)
            _message.value = "Removed ${book.title}"
        }
    }

    // --- collections --------------------------------------------------------

    fun selectCollection(id: Long?) {
        _selectedCollection.value = id
        _selection.value = emptySet()
    }

    fun createCollectionWithSelection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val ids = _selection.value
        viewModelScope.launch {
            val existing = collectionDao.byName(trimmed)
            val collectionId = existing?.id
                ?: collectionDao.insert(CollectionEntity(name = trimmed))
                    .takeIf { it != -1L }
                ?: collectionDao.byName(trimmed)?.id
                ?: return@launch

            ids.forEach { bookId ->
                collectionDao.addBook(BookCollectionCrossRef(bookId, collectionId))
            }
            _selection.value = emptySet()
            _message.value =
                if (ids.size == 1) "Added to $trimmed" else "Added ${ids.size} books to $trimmed"
        }
    }

    fun addSelectionTo(collectionId: Long, collectionName: String) {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { collectionDao.addBook(BookCollectionCrossRef(it, collectionId)) }
            _selection.value = emptySet()
            _message.value =
                if (ids.size == 1) "Added to $collectionName"
                else "Added ${ids.size} books to $collectionName"
        }
    }

    fun removeSelectionFromCurrentCollection() {
        val collectionId = _selectedCollection.value ?: return
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { collectionDao.removeBook(it, collectionId) }
            _selection.value = emptySet()
            _message.value = "Removed from shelf"
        }
    }

    fun deleteCollection(id: Long) {
        viewModelScope.launch {
            collectionDao.delete(id)
            if (_selectedCollection.value == id) _selectedCollection.value = null
            // The books themselves are untouched; only the shelf goes away.
            _message.value = "Shelf deleted"
        }
    }

    // --- misc ---------------------------------------------------------------

    fun open(book: BookEntity) {
        viewModelScope.launch { repo.touch(book.id) }
    }

    fun clearMessage() {
        _message.value = null
    }
}
