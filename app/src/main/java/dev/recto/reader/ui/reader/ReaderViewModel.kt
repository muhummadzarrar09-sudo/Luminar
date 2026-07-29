package dev.recto.reader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.recto.reader.data.BookRepository
import dev.recto.reader.data.EpubBook
import dev.recto.reader.data.EpubLoader
import dev.recto.reader.data.LineSpacing
import dev.recto.reader.data.PageMargin
import dev.recto.reader.data.ReaderFont
import dev.recto.reader.data.ReaderSettings
import dev.recto.reader.data.ReaderTheme
import dev.recto.reader.data.SettingsRepository
import dev.recto.reader.data.db.AnnotationDao
import dev.recto.reader.data.db.AnnotationEntity
import dev.recto.reader.data.db.AnnotationKind
import dev.recto.reader.data.db.VocabularyEntity
import dev.recto.reader.data.lookup.DictionaryEntry
import dev.recto.reader.data.lookup.LookupRepository
import dev.recto.reader.data.lookup.LookupState
import dev.recto.reader.data.lookup.WikipediaSummary
import dev.recto.reader.data.search.SearchHit
import dev.recto.reader.data.search.TextSearch
import dev.recto.reader.data.db.BookEntity
import dev.recto.reader.data.db.RectoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

/** Which overlay, if any, is showing over the page. */
enum class ReaderSheet { NONE, SETTINGS, CONTENTS, NOTEBOOK, VOCABULARY, SEARCH }

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val db = RectoDatabase.get(app)
    private val repo = BookRepository(app, db.bookDao())
    private val settingsRepo = SettingsRepository(app)
    private val annotationDao: AnnotationDao = db.annotationDao()
    private val lookupRepo = LookupRepository(db.lookupDao())

    private val _bookIdFlow = MutableStateFlow(-1L)

    /** Every highlight, note and bookmark for the open book. */
    val annotations: StateFlow<List<AnnotationEntity>> = _bookIdFlow
        .flatMapLatest { id ->
            if (id <= 0) emptyFlow() else annotationDao.observeForBook(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Live text selection, as absolute character offsets into the book.
     * Non-null means the selection toolbar is showing.
     */
    private val _selection = MutableStateFlow<IntRange?>(null)
    val selection: StateFlow<IntRange?> = _selection.asStateFlow()

    /** Annotation currently being edited in the note dialog. */
    private val _editingNote = MutableStateFlow<AnnotationEntity?>(null)
    val editingNote: StateFlow<AnnotationEntity?> = _editingNote.asStateFlow()

    val settings: StateFlow<ReaderSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _pageIndex = MutableStateFlow(0)
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    private val _chromeVisible = MutableStateFlow(false)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    private val _sheet = MutableStateFlow(ReaderSheet.NONE)
    val sheet: StateFlow<ReaderSheet> = _sheet.asStateFlow()

    private var bookId: Long = -1
    private var saveJob: Job? = null

    /** Character offset we want to land on once pagination finishes. */
    private var pendingRestoreChar: Int? = null

    fun load(id: Long) {
        if (bookId == id && _state.value is ReaderState.Ready) return
        bookId = id
        _bookIdFlow.value = id

        viewModelScope.launch {
            _state.value = ReaderState.Loading

            val book = repo.byId(id)
            if (book == null) {
                _state.value = ReaderState.Error("That book is no longer in your library.")
                return@launch
            }

            // Goes through the repository, which knows whether this book is
            // referenced by SAF URI or was copied into app storage.
            val loaded = withContext(Dispatchers.IO) {
                EpubLoader.load { repo.openBook(book) }
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

    // --- table of contents --------------------------------------------------

    /**
     * Chapter list for the contents sheet, with each chapter's position in the
     * book as a percentage. Chapters with no title get a numbered placeholder
     * rather than being hidden, so the list always matches the book.
     */
    fun tableOfContents(): List<TocEntry> {
        val content = (_state.value as? ReaderState.Ready)?.content ?: return emptyList()
        val total = content.totalChars.coerceAtLeast(1)
        var running = 0
        return content.chapters.mapIndexed { index, chapter ->
            val percent = (running * 100 / total).coerceIn(0, 100)
            running += chapter.text.length
            TocEntry(
                chapterIndex = index,
                title = chapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}",
                percent = percent
            )
        }
    }

    /** Which chapter the current page belongs to, for highlighting in the TOC. */
    fun currentChapterIndex(): Int {
        val pages = (_state.value as? ReaderState.Ready)?.pages ?: return 0
        return pages.getOrNull(_pageIndex.value)?.chapterIndex ?: 0
    }

    fun goToChapter(entry: TocEntry) {
        val pages = (_state.value as? ReaderState.Ready)?.pages ?: return
        val target = pages.indexOfFirst { it.chapterIndex == entry.chapterIndex }
        if (target >= 0) {
            _pageIndex.value = target
            scheduleSave()
        }
        _sheet.value = ReaderSheet.NONE
    }

    // --- in-book search -----------------------------------------------------

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchHits = MutableStateFlow<List<SearchHit>>(emptyList())
    val searchHits: StateFlow<List<SearchHit>> = _searchHits.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Searches the open book. The text is already parsed and in memory, so
     * this is a scan of a few hundred kilobytes - fast enough to run as you
     * type, with a short debounce to avoid re-scanning on every keystroke.
     */
    fun search(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.trim().length < 2) {
            _searchHits.value = emptyList()
            _searching.value = false
            return
        }

        val ready = _state.value as? ReaderState.Ready ?: return
        _searching.value = true

        searchJob = viewModelScope.launch {
            delay(180)
            val hits = withContext(Dispatchers.Default) {
                TextSearch.searchChapters(
                    chapters = ready.content.chapters,
                    query = query,
                    bookId = ready.book.id,
                    bookTitle = ready.content.title ?: ready.book.title
                )
            }
            _searchHits.value = hits
            _searching.value = false
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchHits.value = emptyList()
        _searching.value = false
    }

    // --- lookup -------------------------------------------------------------

    /** The word being looked up, or null when the card is closed. */
    private val _lookupWord = MutableStateFlow<String?>(null)
    val lookupWord: StateFlow<String?> = _lookupWord.asStateFlow()

    /** Sentence the word appeared in, carried into the vocabulary entry. */
    private val _lookupContext = MutableStateFlow<String?>(null)
    val lookupContext: StateFlow<String?> = _lookupContext.asStateFlow()

    private val _dictionary =
        MutableStateFlow<LookupState<DictionaryEntry>>(LookupState.Idle)
    val dictionary: StateFlow<LookupState<DictionaryEntry>> = _dictionary.asStateFlow()

    private val _wikipedia =
        MutableStateFlow<LookupState<WikipediaSummary>>(LookupState.Idle)
    val wikipedia: StateFlow<LookupState<WikipediaSummary>> = _wikipedia.asStateFlow()

    /** Vocabulary list, for the flashcard sheet. */
    val vocabulary: StateFlow<List<VocabularyEntity>> = lookupRepo.observeVocabulary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueCount: StateFlow<Int> = lookupRepo.observeDueCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _wordSaved = MutableStateFlow(false)
    val wordSaved: StateFlow<Boolean> = _wordSaved.asStateFlow()

    /**
     * Looks a word up. Dictionary and Wikipedia are fetched concurrently -
     * they are independent, and doing them in sequence would double the wait
     * on the slower one.
     */
    fun lookUp(word: String, contextSentence: String? = null) {
        val clean = word.trim().trim('.', ',', ';', ':', '!', '?', '"', '\'', '(', ')')
        if (clean.isEmpty()) return

        _lookupWord.value = clean
        _lookupContext.value = contextSentence
        _dictionary.value = LookupState.Loading
        _wikipedia.value = LookupState.Loading
        _wordSaved.value = false

        viewModelScope.launch {
            _wordSaved.value = lookupRepo.isSaved(clean)
        }
        viewModelScope.launch {
            _dictionary.value = lookupRepo.define(clean)
        }
        viewModelScope.launch {
            _wikipedia.value = lookupRepo.wikipedia(clean)
        }
    }

    fun retryLookup() {
        _lookupWord.value?.let { lookUp(it, _lookupContext.value) }
    }

    fun dismissLookup() {
        _lookupWord.value = null
        _dictionary.value = LookupState.Idle
        _wikipedia.value = LookupState.Idle
    }

    /** Looks up whatever is currently selected. */
    fun lookUpSelection() {
        val text = selectedText()
        if (text.isBlank()) return
        // Only the first word - a dictionary cannot do phrases, and the
        // Wikipedia tab handles multi-word proper nouns anyway.
        val first = text.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        lookUp(if (text.trim().count { it == ' ' } <= 2) text.trim() else first,
            contextSentence = sentenceAround(_selection.value?.first ?: 0))
        _selection.value = null
    }

    fun saveCurrentWord() {
        val word = _lookupWord.value ?: return
        val entry = (_dictionary.value as? LookupState.Success)?.data ?: return
        val firstPos = entry.entries.firstOrNull() ?: return
        val definition = firstPos.senses.firstOrNull()?.definition ?: return
        val ready = _state.value as? ReaderState.Ready

        viewModelScope.launch {
            val ok = lookupRepo.saveWord(
                word = word,
                definition = definition,
                partOfSpeech = firstPos.partOfSpeech,
                phonetic = entry.phonetic,
                contextSentence = _lookupContext.value,
                bookId = ready?.book?.id,
                bookTitle = ready?.content?.title ?: ready?.book?.title
            )
            if (ok) _wordSaved.value = true
        }
    }

    /**
     * The sentence containing an offset, for vocabulary context. Context is
     * most of what makes a word stick, so it is worth storing.
     */
    private fun sentenceAround(offset: Int, window: Int = 220): String? {
        val ready = _state.value as? ReaderState.Ready ?: return null
        val from = (offset - window).coerceAtLeast(0)
        val slice = textBetween(ready, from, offset + window)
        if (slice.isBlank()) return null

        val local = (offset - from).coerceIn(0, slice.length)
        val start = slice.lastIndexOfAny(charArrayOf('.', '!', '?'), (local - 1).coerceAtLeast(0))
        val end = slice.indexOfAny(charArrayOf('.', '!', '?'), local)
        return slice.substring(
            (start + 1).coerceIn(0, slice.length),
            (if (end == -1) slice.length else end + 1).coerceIn(0, slice.length)
        ).trim().ifBlank { null }
    }

    // --- annotations --------------------------------------------------------

    /**
     * Absolute character offset of the start of the current page. Everything
     * annotation-related works in absolute book offsets, so a highlight
     * survives re-pagination the same way a reading position does.
     */
    fun pageStartChar(): Int {
        val ready = _state.value as? ReaderState.Ready ?: return 0
        val pages = ready.pages ?: return 0
        return globalCharForPage(pages, ready.content, _pageIndex.value)
    }

    /** Absolute book offset of the first character on a given page. */
    fun pageStartOf(index: Int): Int {
        val ready = _state.value as? ReaderState.Ready ?: return 0
        val pages = ready.pages ?: return 0
        return globalCharForPage(pages, ready.content, index)
    }

    fun setSelection(range: IntRange?) {
        _selection.value = range
    }

    fun clearSelection() {
        _selection.value = null
    }

    /** The words currently selected, for the Copy action. */
    fun selectedText(): String {
        val range = _selection.value ?: return ""
        val ready = _state.value as? ReaderState.Ready ?: return ""
        return textBetween(ready, range.first, range.last)
    }

    /**
     * Turns the current selection into a highlight.
     *
     * If it overlaps existing highlights, those are absorbed rather than
     * stacked - selecting across two highlights and picking a colour should
     * produce one highlight in that colour, not three overlapping spans.
     * Notes on absorbed annotations are preserved.
     */
    fun highlightSelection(colour: Int) {
        val range = _selection.value ?: return
        val ready = _state.value as? ReaderState.Ready ?: return
        val bookId = ready.book.id

        viewModelScope.launch {
            var start = range.first
            var end = range.last
            var keptNote: String? = null

            val overlaps = annotationDao.overlapping(bookId, start, end)
            overlaps.forEach { existing ->
                start = minOf(start, existing.startChar)
                end = maxOf(end, existing.endChar)
                if (keptNote == null) keptNote = existing.note
                annotationDao.delete(existing.id)
            }

            val text = textBetween(ready, start, end)
            val chapter = chapterAt(ready, start)

            annotationDao.insert(
                AnnotationEntity(
                    bookId = bookId,
                    kind = if (keptNote.isNullOrBlank()) {
                        AnnotationKind.HIGHLIGHT.name
                    } else {
                        AnnotationKind.NOTE.name
                    },
                    startChar = start,
                    endChar = end,
                    selectedText = text,
                    note = keptNote,
                    colour = colour,
                    chapterIndex = chapter.first,
                    chapterTitle = chapter.second
                )
            )
            _selection.value = null
        }
    }

    /** Opens the note editor for the selection, creating a highlight first. */
    fun addNoteToSelection() {
        val range = _selection.value ?: return
        val ready = _state.value as? ReaderState.Ready ?: return

        viewModelScope.launch {
            val existing = annotationDao
                .overlapping(ready.book.id, range.first, range.last)
                .firstOrNull()

            if (existing != null) {
                _editingNote.value = existing
            } else {
                val text = textBetween(ready, range.first, range.last)
                val chapter = chapterAt(ready, range.first)
                val id = annotationDao.insert(
                    AnnotationEntity(
                        bookId = ready.book.id,
                        kind = AnnotationKind.NOTE.name,
                        startChar = range.first,
                        endChar = range.last,
                        selectedText = text,
                        colour = 0,
                        chapterIndex = chapter.first,
                        chapterTitle = chapter.second
                    )
                )
                _editingNote.value = AnnotationEntity(
                    id = id,
                    bookId = ready.book.id,
                    kind = AnnotationKind.NOTE.name,
                    startChar = range.first,
                    endChar = range.last,
                    selectedText = text,
                    colour = 0,
                    chapterIndex = chapter.first,
                    chapterTitle = chapter.second
                )
            }
            _selection.value = null
        }
    }

    fun editNote(annotation: AnnotationEntity) {
        _editingNote.value = annotation
    }

    fun saveNote(id: Long, note: String) {
        viewModelScope.launch {
            val trimmed = note.trim()
            annotationDao.updateNote(
                id = id,
                note = trimmed.ifBlank { null },
                // Emptying a note demotes it back to a plain highlight rather
                // than leaving a NOTE with nothing written on it.
                kind = if (trimmed.isBlank()) {
                    AnnotationKind.HIGHLIGHT.name
                } else {
                    AnnotationKind.NOTE.name
                }
            )
            _editingNote.value = null
        }
    }

    fun dismissNoteEditor() {
        _editingNote.value = null
    }

    /** True when the current page already has a bookmark. */
    fun isCurrentPageBookmarked(): Boolean {
        val start = pageStartChar()
        return annotations.value.any {
            it.kind == AnnotationKind.BOOKMARK.name && it.startChar == start
        }
    }

    // --- flashcards ---

    private val _reviewQueue = MutableStateFlow<List<VocabularyEntity>>(emptyList())
    val reviewQueue: StateFlow<List<VocabularyEntity>> = _reviewQueue.asStateFlow()

    fun startReview() {
        viewModelScope.launch {
            _reviewQueue.value = lookupRepo.dueCards()
        }
    }

    fun answerCard(card: VocabularyEntity, recall: dev.recto.reader.data.lookup.Recall) {
        viewModelScope.launch {
            lookupRepo.review(card, recall)
            // Drop the answered card; "Again" cards come back next session
            // rather than immediately, which keeps a review finite.
            _reviewQueue.value = _reviewQueue.value.filterNot { it.id == card.id }
        }
    }

    fun deleteVocabulary(id: Long) {
        viewModelScope.launch {
            lookupRepo.deleteWord(id)
            _reviewQueue.value = _reviewQueue.value.filterNot { it.id == id }
        }
    }

    fun deleteAnnotation(id: Long) {
        viewModelScope.launch { annotationDao.delete(id) }
    }

    /** Toggles a bookmark on the current page. */
    fun toggleBookmark() {
        val ready = _state.value as? ReaderState.Ready ?: return
        val start = pageStartChar()
        viewModelScope.launch {
            val existing = annotationDao.bookmarkAt(ready.book.id, start)
            if (existing != null) {
                annotationDao.delete(existing.id)
            } else {
                val chapter = chapterAt(ready, start)
                val preview = textBetween(ready, start, start + 90)
                annotationDao.insert(
                    AnnotationEntity(
                        bookId = ready.book.id,
                        kind = AnnotationKind.BOOKMARK.name,
                        startChar = start,
                        endChar = start,
                        selectedText = preview,
                        chapterIndex = chapter.first,
                        chapterTitle = chapter.second
                    )
                )
            }
        }
    }

    /** Jumps to whatever page contains this offset. */
    fun goToChar(target: Int) {
        val ready = _state.value as? ReaderState.Ready ?: return
        val pages = ready.pages ?: return
        _pageIndex.value = pageForGlobalChar(pages, ready.content, target)
        _sheet.value = ReaderSheet.NONE
        scheduleSave()
    }

    /** Slices the book's concatenated text by absolute offsets. */
    private fun textBetween(ready: ReaderState.Ready, start: Int, end: Int): String {
        val sb = StringBuilder()
        var cursor = 0
        for (chapter in ready.content.chapters) {
            val chapterStart = cursor
            val chapterEnd = cursor + chapter.text.length
            if (chapterEnd > start && chapterStart < end) {
                val from = (start - chapterStart).coerceIn(0, chapter.text.length)
                val to = (end - chapterStart).coerceIn(0, chapter.text.length)
                if (from < to) sb.append(chapter.text, from, to)
            }
            cursor = chapterEnd
            if (cursor >= end) break
        }
        return sb.toString().trim()
    }

    private fun chapterAt(ready: ReaderState.Ready, offset: Int): Pair<Int, String?> {
        var cursor = 0
        ready.content.chapters.forEachIndexed { index, chapter ->
            val end = cursor + chapter.text.length
            if (offset < end) return index to chapter.title
            cursor = end
        }
        val last = ready.content.chapters.lastIndex.coerceAtLeast(0)
        return last to ready.content.chapters.getOrNull(last)?.title
    }

    // --- settings -----------------------------------------------------------

    fun setTheme(theme: ReaderTheme) = viewModelScope.launch { settingsRepo.setTheme(theme) }
    fun setFont(font: ReaderFont) = viewModelScope.launch { settingsRepo.setFont(font) }
    fun setFontSize(sp: Int) = viewModelScope.launch { settingsRepo.setFontSize(sp) }
    fun setLineSpacing(v: LineSpacing) = viewModelScope.launch { settingsRepo.setLineSpacing(v) }
    fun setMargin(v: PageMargin) = viewModelScope.launch { settingsRepo.setMargin(v) }
    fun setJustify(v: Boolean) = viewModelScope.launch { settingsRepo.setJustify(v) }
    fun setVolumeKeys(v: Boolean) = viewModelScope.launch { settingsRepo.setVolumeKeys(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { settingsRepo.setKeepScreenOn(v) }
    fun setWarmth(v: Float) = viewModelScope.launch { settingsRepo.setWarmth(v) }
    fun setDim(v: Float) = viewModelScope.launch { settingsRepo.setDim(v) }
    fun setUseReaderBrightness(v: Boolean) =
        viewModelScope.launch { settingsRepo.setUseReaderBrightness(v) }
    fun setBrightness(v: Float) = viewModelScope.launch { settingsRepo.setBrightness(v) }

    fun showSheet(which: ReaderSheet) {
        _sheet.value = which
    }

    fun dismissSheet() {
        _sheet.value = ReaderSheet.NONE
    }

    // --- pagination ---------------------------------------------------------

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
     * Re-anchors the reading position before a re-pagination (rotation, font
     * size change, margin change). We store a character offset rather than a
     * page number precisely so the position survives a different page count -
     * bumping the text size must not lose your place.
     */
    fun rememberPositionBeforeRepaginate() {
        val ready = _state.value as? ReaderState.Ready ?: return
        val pages = ready.pages ?: return
        if (pages.isEmpty()) return
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
