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
import dev.recto.reader.data.db.RecentLookupEntity
import dev.recto.reader.data.db.VocabularyEntity
import dev.recto.reader.data.lookup.DictionaryEntry
import dev.recto.reader.data.lookup.LookupRepository
import dev.recto.reader.data.lookup.LookupState
import dev.recto.reader.data.lookup.WikipediaSummary
import dev.recto.reader.data.search.SearchHit
import dev.recto.reader.data.search.TextSearch
import dev.recto.reader.data.stats.ReadingStats
import dev.recto.reader.data.stats.SessionTracker
import dev.recto.reader.data.tts.EngineOption
import dev.recto.reader.data.tts.SpeechEngine
import dev.recto.reader.data.tts.SpeechListener
import dev.recto.reader.data.tts.SystemSpeechEngine
import dev.recto.reader.data.tts.Utterance
import dev.recto.reader.data.tts.Utterances
import dev.recto.reader.data.tts.VoiceOption
import dev.recto.reader.tts.ReadAloudBus
import dev.recto.reader.tts.ReadAloudCommand
import dev.recto.reader.tts.ReadAloudService
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
enum class ReaderSheet { NONE, SETTINGS, CONTENTS, NOTEBOOK, VOCABULARY, SEARCH, READ_ALOUD }

/**
 * Something that just happened and can be taken back.
 *
 * Deleted rows are carried whole rather than by id, because by the time undo
 * runs the row is gone from the database and there is nothing left to look
 * up. Re-inserting the entity restores its id too, since it is the primary
 * key - so a note that was attached to a highlight comes back attached.
 */
sealed interface UndoableAction {
    /** What the snackbar says happened. */
    val message: String

    /** A new annotation appeared. Undo deletes it. */
    data class Created(
        val created: AnnotationEntity,
        override val message: String
    ) : UndoableAction

    /** Annotations were removed. Undo puts them back. */
    data class Deleted(
        val removed: List<AnnotationEntity>,
        override val message: String
    ) : UndoableAction

    /**
     * Annotations were swallowed by a new one - what happens when a highlight
     * overlaps existing highlights. Undo removes the new one and restores the
     * originals, which is the only way to make "highlight over a highlight"
     * reversible.
     */
    data class Replaced(
        val created: AnnotationEntity,
        val removed: List<AnnotationEntity>,
        override val message: String
    ) : UndoableAction
}

/**
 * How long an undo stays on offer.
 *
 * Long enough to notice a mis-tap and react, short enough that the snackbar
 * is not still sitting over the page when you have moved on. Material's
 * "long" snackbar is 10s, which is too long to keep deleted rows in memory
 * for something this small.
 */
private const val UNDO_WINDOW_MS = 6_000L

/**
 * The line used to audition a voice.
 *
 * Deliberately prose rather than "testing one two three": you are
 * judging whether it is pleasant for an hour, and that only shows up on
 * a real sentence with real rhythm and a comma to breathe at.
 */
private const val PREVIEW_SENTENCE =
    "It was a bright cold day in April, and the clocks were striking thirteen."

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val db = RectoDatabase.get(app)
    private val repo = BookRepository(app, db.bookDao())
    private val settingsRepo = SettingsRepository(app)
    private val annotationDao: AnnotationDao = db.annotationDao()
    private val lookupRepo = LookupRepository(db.lookupDao())
    private val sessionTracker = SessionTracker(db.readingSessionDao())

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

    /**
     * The last undoable thing that happened, or null.
     *
     * Only ONE step is kept. A stack sounds better and is worse: undo here is
     * for the mis-tap you just made, and a reader who has highlighted three
     * more passages since does not want the fourth undo to silently remove
     * something from five minutes ago.
     */
    private val _undo = MutableStateFlow<UndoableAction?>(null)
    val undo: StateFlow<UndoableAction?> = _undo.asStateFlow()

    private var undoTimer: Job? = null

    /**
     * Offers an undo, and retracts it after a while.
     *
     * The timeout matters for correctness, not just tidiness: [UndoableAction]
     * holds deleted rows in memory, and an offer that never expires is a
     * promise we cannot keep once the book closes.
     */
    private fun offerUndo(action: UndoableAction) {
        _undo.value = action
        undoTimer?.cancel()
        undoTimer = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            // Only clear if it is still the same offer. Without this check a
            // stale timer wipes a newer undo that arrived in the meantime.
            if (_undo.value === action) _undo.value = null
        }
    }

    fun dismissUndo() {
        undoTimer?.cancel()
        _undo.value = null
    }

    /**
     * Puts back whatever the last action removed or changed.
     *
     * Deleted rows are re-inserted with their original ids, so anything
     * pointing at them still resolves and the notebook keeps its ordering.
     */
    fun performUndo() {
        val action = _undo.value ?: return
        undoTimer?.cancel()
        _undo.value = null

        viewModelScope.launch {
            when (action) {
                is UndoableAction.Created ->
                    annotationDao.delete(action.created.id)

                is UndoableAction.Deleted ->
                    action.removed.forEach { annotationDao.insert(it) }

                is UndoableAction.Replaced -> {
                    annotationDao.delete(action.created.id)
                    action.removed.forEach { annotationDao.insert(it) }
                }
            }
        }
    }

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

        // An undo offered in the previous book must not survive into this one.
        // The rows it holds belong to a book that is no longer open, and
        // "Undo" would silently edit something you cannot see.
        dismissUndo()

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
                    sessionTracker.start(
                        bookId = book.id,
                        bookTitle = content.title ?: book.title,
                        atChar = pendingRestoreChar ?: 0
                    )
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

    /** Everything looked up recently, newest first. */
    val recentLookups: StateFlow<List<RecentLookupEntity>> =
        lookupRepo.observeRecentLookups()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteRecentLookup(id: Long) =
        viewModelScope.launch { lookupRepo.deleteRecent(id) }

    fun clearRecentLookups() =
        viewModelScope.launch { lookupRepo.clearRecent() }

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

        // A dictionary can only answer single words. Asking it about a
        // sentence wastes a request and returns a confusing "not found",
        // so we say plainly that it does not apply instead.
        val dictionaryJob = if (isWordLike(clean)) {
            viewModelScope.launch {
                _dictionary.value = lookupRepo.define(clean)
            }
        } else {
            _dictionary.value = LookupState.Empty(
                "Dictionaries only define single words. Try Wikipedia or a web " +
                    "search for a phrase."
            )
            null
        }

        val wikipediaJob = viewModelScope.launch {
            _wikipedia.value = lookupRepo.wikipedia(clean)
        }

        // Record it once both panes have settled, so the stored summary is
        // whatever we actually found rather than "Loading". Waiting also
        // means a word looked up twice keeps its best summary instead of
        // being overwritten by an in-flight blank.
        viewModelScope.launch {
            dictionaryJob?.join()
            wikipediaJob.join()

            val ready = _state.value as? ReaderState.Ready
            val dict = (_dictionary.value as? LookupState.Success)?.data
            val wiki = (_wikipedia.value as? LookupState.Success)?.data

            lookupRepo.recordLookup(
                term = clean,
                summary = dict?.entries?.firstOrNull()?.senses?.firstOrNull()?.definition
                    ?: wiki?.extract?.take(200),
                contextSentence = contextSentence,
                bookId = ready?.book?.id,
                bookTitle = ready?.content?.title ?: ready?.book?.title
            )
        }
    }

    /** Single word, or a short hyphenated/apostrophised one. */
    private fun isWordLike(text: String): Boolean =
        text.length <= 40 && !text.trim().contains(Regex("\\s"))

    fun retryLookup() {
        _lookupWord.value?.let { lookUp(it, _lookupContext.value) }
    }

    fun dismissLookup() {
        _lookupWord.value = null
        _dictionary.value = LookupState.Idle
        _wikipedia.value = LookupState.Idle
    }

    /** Looks up whatever is currently selected. */
    /**
     * Looks up whatever is selected, however long.
     *
     * Phrases are the point here. A dictionary can only answer single words,
     * but a selected phrase is usually the thing you did NOT understand - a
     * metaphor, an idiom, a turn of phrase. So a phrase still opens the card:
     * Wikipedia may well have the idiom, and if nothing does, the card offers
     * a web search, which is the honest answer for "what does this mean?".
     *
     * The dictionary tab is only queried for something word-shaped, so we do
     * not fire a pointless request for a fourteen-word sentence.
     */
    fun lookUpSelection() {
        val text = selectedText().trim()
        if (text.isBlank()) return
        lookUp(text, contextSentence = sentenceAround(_selection.value?.first ?: 0))
        _selection.value = null
    }

    /**
     * Saves the looked-up word OR phrase for study.
     *
     * Previously this bailed out unless the dictionary had returned a
     * definition, which meant phrases - the things you most want to remember -
     * could never be saved. Now it takes the best meaning available, in order:
     * a dictionary sense, then the Wikipedia extract, then the sentence you
     * met it in. Something you selected is always worth keeping.
     */
    fun saveCurrentWord() {
        val term = _lookupWord.value ?: return
        val ready = _state.value as? ReaderState.Ready

        val dict = (_dictionary.value as? LookupState.Success)?.data
        val firstPos = dict?.entries?.firstOrNull()
        val wiki = (_wikipedia.value as? LookupState.Success)?.data

        val definition = firstPos?.senses?.firstOrNull()?.definition
            ?: wiki?.extract?.take(300)
            ?: _lookupContext.value?.let { "In context: $it" }
            ?: return

        viewModelScope.launch {
            val ok = lookupRepo.saveWord(
                word = term,
                definition = definition,
                partOfSpeech = firstPos?.partOfSpeech
                    ?: if (term.contains(' ')) "phrase" else null,
                phonetic = dict?.phonetic,
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

            val entity = AnnotationEntity(
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
            // Room hands back the generated id; the entity as written has id
            // 0, so undo would delete nothing without this.
            val id = annotationDao.insert(entity)

            offerUndo(
                if (overlaps.isEmpty()) {
                    UndoableAction.Created(
                        created = entity.copy(id = id),
                        message = "Highlighted"
                    )
                } else {
                    UndoableAction.Replaced(
                        created = entity.copy(id = id),
                        removed = overlaps,
                        message = "Highlights merged"
                    )
                }
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
        viewModelScope.launch {
            // Read it first - after the delete there is nothing to put back.
            val existing = annotationDao.byId(id)
            annotationDao.delete(id)
            if (existing != null) {
                offerUndo(
                    UndoableAction.Deleted(
                        removed = listOf(existing),
                        message = when (existing.kind) {
                            AnnotationKind.BOOKMARK.name -> "Bookmark removed"
                            AnnotationKind.NOTE.name -> "Note deleted"
                            else -> "Highlight removed"
                        }
                    )
                )
            }
        }
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

    fun setDefaultHighlightColour(index: Int) =
        viewModelScope.launch { settingsRepo.setDefaultHighlightColour(index) }
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
            _selection.value = null
            sessionTracker.onPageTurned()
            scheduleSave()
        }
    }

    fun previous() {
        if (_pageIndex.value > 0) {
            _pageIndex.value--
            _selection.value = null
            sessionTracker.onPageTurned()
            scheduleSave()
        }
    }

    fun goTo(index: Int) {
        val pages = (_state.value as? ReaderState.Ready)?.pages ?: return
        _pageIndex.value = index.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        // Leaving the page a selection belongs to has to drop the selection.
        // The offsets are absolute, so a stale one still resolves - it just
        // resolves to somewhere off this page, and the toolbar would point at
        // nothing. Volume keys make this easy to hit: highlight, then press
        // volume-down without dismissing.
        _selection.value = null
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

    /**
     * Closes the reading session and, if today's goal was just crossed, says
     * so. Called when leaving the reader or when the app goes to background -
     * a session left open would otherwise keep counting in your pocket.
     */
    fun endSession(onGoalReached: (minutes: Int, streak: Int) -> Unit = { _, _ -> }) {
        if (!sessionTracker.isRunning) return
        val endChar = pageStartChar()
        viewModelScope.launch {
            val before = db.readingSessionDao().millisOnDay(ReadingStats.dayKey())
            sessionTracker.stop(endChar) ?: return@launch

            val settings = settings.value
            val goal = settings.dailyGoalMinutes
            if (goal <= 0) return@launch

            val after = db.readingSessionDao().millisOnDay(ReadingStats.dayKey())
            val beforeMin = (before / 60_000).toInt()
            val afterMin = (after / 60_000).toInt()

            // Only when the goal is crossed by THIS session, so finishing a
            // second session on the same day does not congratulate you twice.
            if (beforeMin < goal && afterMin >= goal) {
                val days = db.readingSessionDao().recentDays().toSet()
                onGoalReached(afterMin, ReadingStats.currentStreak(days))
            }
        }
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

    // ------------------------------------------------------------------
    // Read aloud
    // ------------------------------------------------------------------

    private val speech: SpeechEngine = SystemSpeechEngine(app)

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    /**
     * Absolute range currently being spoken, for the on-page highlight.
     * Narrows to the exact words when the engine reports ranges, otherwise
     * stays at sentence granularity.
     */
    private val _spokenRange = MutableStateFlow<IntRange?>(null)
    val spokenRange: StateFlow<IntRange?> = _spokenRange.asStateFlow()

    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceOption>> = _voices.asStateFlow()

    private val _engines = MutableStateFlow<List<EngineOption>>(emptyList())
    val engines: StateFlow<List<EngineOption>> = _engines.asStateFlow()

    private val _speechError = MutableStateFlow<String?>(null)
    val speechError: StateFlow<String?> = _speechError.asStateFlow()

    /** Minutes remaining on the sleep timer, or null when it is off. */
    private val _sleepRemaining = MutableStateFlow<Int?>(null)
    val sleepRemaining: StateFlow<Int?> = _sleepRemaining.asStateFlow()

    init {
        // Lock-screen and notification buttons arrive here. Routing them
        // through the same functions the UI calls means there is one
        // playback code path, not two that can drift apart.
        viewModelScope.launch {
            ReadAloudBus.commands.collect { command ->
                when (command) {
                    ReadAloudCommand.TOGGLE -> toggleSpeaking()
                    ReadAloudCommand.NEXT -> skipSentence(true)
                    ReadAloudCommand.PREVIOUS -> skipSentence(false)
                    ReadAloudCommand.STOP -> stopSpeaking()
                }
            }
        }
    }

    private var queue: List<Utterance> = emptyList()
    private var speechJob: Job? = null
    private var sleepJob: Job? = null

    /**
     * Bumped every time playback starts or is retargeted.
     *
     * Engine callbacks arrive on their own thread and can land AFTER the
     * session they belong to has been stopped. Checking only `speaking`
     * is not enough: stop, then start somewhere else, and a late onDone from
     * the old chain advances the NEW queue, silently skipping a sentence.
     * Simulated it - without this guard the second session loses its first
     * sentence. Each callback carries the generation it was issued under and
     * is ignored if that has moved on.
     */
    private var speechGeneration = 0

    /**
     * How many sentences to keep queued in the engine.
     *
     * Three is enough to cover synthesis of the next chunk even on a slow
     * chip, without queueing so far ahead that stopping feels laggy - the
     * engine finishes its current utterance before a flush takes effect.
     */
    private val LOOKAHEAD = 3

    /** Next index to hand to the engine. */
    private var queuedIndex = 0

    /** Index actually sounding right now, or -1. */
    private var playingIndex = -1

    /** Utterance ids handed over but not yet finished. */
    private val pending = mutableListOf<String>()
    private val idToIndex = mutableMapOf<String, Int>()

    private fun utteranceId(generation: Int, index: Int) = "recto-$generation-$index"

    /**
     * One listener for the whole session, dispatching by utterance id.
     *
     * The id carries its generation, so a callback from a session that has
     * been stopped is dropped rather than advancing the new queue. Without
     * that, stop-then-start-elsewhere silently eats a sentence.
     */
    private val speechListener = object : SpeechListener {
        override fun onStart(id: String) {
            val index = idToIndex[id] ?: return
            val generation = generationOf(id) ?: return
            viewModelScope.launch { onUtteranceStart(generation, index) }
        }

        override fun onRange(id: String, start: Int, end: Int) {
            val index = idToIndex[id] ?: return
            val generation = generationOf(id) ?: return
            viewModelScope.launch {
                if (generation != speechGeneration) return@launch
                val current = queue.getOrNull(index) ?: return@launch
                // Engine offsets are relative to the SPOKEN string, which
                // forSpeech may have shortened. Clamp so a mismatch can never
                // highlight past the sentence.
                val from = (current.start + start)
                    .coerceIn(current.start, current.end)
                val to = (current.start + end).coerceIn(from, current.end)
                _spokenRange.value = from until to
            }
        }

        override fun onDone(id: String) {
            val generation = generationOf(id) ?: return
            viewModelScope.launch {
                if (generation != speechGeneration) return@launch
                if (!_speaking.value) return@launch
                pending.remove(id)
                idToIndex.remove(id)
                if (queuedIndex >= queue.size && pending.isEmpty()) {
                    stopSpeaking()
                    return@launch
                }
                pump(generation)
            }
        }

        override fun onError(id: String, message: String) {
            val generation = generationOf(id) ?: return
            viewModelScope.launch {
                if (generation != speechGeneration) return@launch
                _speechError.value = message
                stopSpeaking()
            }
        }
    }

    private fun generationOf(id: String): Int? =
        id.split('-').getOrNull(1)?.toIntOrNull()

    /**
     * Starts reading aloud from [fromChar], or from the top of the page.
     *
     * The queue is built for the WHOLE BOOK from that point, not just the
     * page. Rebuilding at each page boundary would stutter, and the queue is
     * cheap - it is offsets into text already in memory.
     */
    fun startSpeaking(fromChar: Int? = null) {
        val ready = _state.value as? ReaderState.Ready ?: return
        _speechError.value = null

        speechJob?.cancel()
        speechJob = viewModelScope.launch {
            if (!speech.isReady && !speech.prepare(settings.value.ttsEnginePackage)) {
                _speechError.value =
                    "No text-to-speech engine is installed. Install one, then " +
                        "pick it in Android settings under Accessibility."
                return@launch
            }

            speech.setListener(speechListener)
            _engines.value = speech.engines()
            _voices.value = speech.voices(java.util.Locale.getDefault())
            speech.selectVoice(settings.value.ttsVoiceId)

            val start = fromChar ?: pageStartChar()
            val whole = wholeText(ready)
            queue = Utterances.split(whole).filter { it.end > start }

            if (queue.isEmpty()) {
                _speechError.value = "Nothing left to read in this book"
                return@launch
            }

            _speaking.value = true
            speechGeneration++
            queuedIndex = 0
            playingIndex = -1
            pending.clear()
            idToIndex.clear()

            // Start the foreground service ONCE, here, while the app is
            // definitely in the foreground - Android 12+ rejects a
            // background start. Per-sentence updates go straight to the
            // notification manager instead.
            val ready2 = _state.value as? ReaderState.Ready
            ReadAloudService.start(
                context = getApplication(),
                title = ready2?.content?.title ?: ready2?.book?.title ?: "Recto",
                line = queue.firstOrNull()?.text?.take(90).orEmpty()
            )

            pump(speechGeneration, flush = true)
        }
    }

    /**
     * Tops the engine's queue up to [LOOKAHEAD] pending utterances.
     *
     * This is the fix for the thing that made read-aloud sound broken. The
     * first version spoke ONE sentence, waited for onDone, then synthesised
     * the next - so every full stop had a dead gap while the model generated
     * the next chunk. On a fast phone a few hundred milliseconds; on a budget
     * chip, painfully obvious. It made good voices sound bad.
     *
     * Queueing ahead means the engine synthesises sentence N+1 while N is
     * still playing, so the audio is continuous.
     *
     * @param flush true when jumping somewhere new, which drops whatever the
     *              engine still has buffered from the old position
     */
    private fun pump(generation: Int, flush: Boolean = false) {
        if (generation != speechGeneration) return

        var first = flush
        while (queuedIndex < queue.size && pending.size < LOOKAHEAD) {
            val index = queuedIndex
            val utterance = queue[index]
            val id = utteranceId(generation, index)

            val accepted = speech.enqueue(
                id = id,
                text = utterance.text,
                flush = first,
                speed = settings.value.ttsSpeed,
                pitch = settings.value.ttsPitch
            )

            if (!accepted) {
                _speechError.value = "The engine refused that passage"
                stopSpeaking()
                return
            }

            pending += id
            idToIndex[id] = index
            first = false
            queuedIndex++
        }
    }

    /**
     * Called when audio for an utterance actually STARTS.
     *
     * Highlighting and page turns hang off this rather than off enqueueing -
     * with a lookahead of three, enqueue order runs several sentences ahead
     * of what you can hear, so turning the page there would flip it early
     * and highlight the wrong line.
     */
    private fun onUtteranceStart(generation: Int, index: Int) {
        if (generation != speechGeneration) return
        val current = queue.getOrNull(index) ?: return

        playingIndex = index
        _spokenRange.value = current.start until current.end
        updateNotification(current)

        // Follow the voice. If the sentence being read is not on the page in
        // front of you, turn to it.
        val target = pageForChar(current.start)
        if (target != _pageIndex.value) {
            _pageIndex.value = target
            scheduleSave()
        }
    }

    private fun updateNotification(current: Utterance) {
        val ready = _state.value as? ReaderState.Ready ?: return
        ReadAloudService.refresh(
            context = getApplication(),
            title = ready.content.title ?: ready.book.title,
            // The sentence being read, trimmed - a whole paragraph in a
            // notification is unreadable at a glance.
            line = current.text.take(90),
            playing = true
        )
    }

    fun stopSpeaking() {
        // Invalidate first, so any callback already in flight is a no-op by
        // the time it lands.
        speechGeneration++
        speechJob?.cancel()
        speechJob = null
        speech.stop()
        _speaking.value = false
        _spokenRange.value = null
        cancelSleepTimer()
        ReadAloudService.stop(getApplication())

        // Leave the reading position where the voice actually GOT TO, not
        // where the queue reached. With a lookahead those differ by up to
        // three sentences, and resuming that far ahead would skip text you
        // never heard.
        queue.getOrNull(playingIndex)?.let { rememberChar(it.start) }

        pending.clear()
        idToIndex.clear()
        playingIndex = -1
    }

    fun toggleSpeaking() {
        if (_speaking.value) stopSpeaking() else startSpeaking()
    }

    /** Skips to the next or previous sentence without stopping. */
    fun skipSentence(forward: Boolean) {
        if (!_speaking.value) return

        // Relative to what is SOUNDING, not to what has been queued. The
        // queue runs up to LOOKAHEAD sentences ahead of the audio, so
        // skipping from there would jump several sentences at once.
        val base = if (playingIndex >= 0) playingIndex else 0
        val next = (base + if (forward) 1 else -1)
            .coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        if (next == base) return

        restartAt(next)
    }

    /**
     * Re-points playback at [index], dropping whatever is buffered.
     *
     * A new generation is essential: speech.stop() makes the engine fire
     * onDone for every utterance it was holding, and those callbacks would
     * otherwise advance the fresh queue.
     */
    private fun restartAt(index: Int) {
        speech.stop()
        speechGeneration++
        pending.clear()
        idToIndex.clear()
        queuedIndex = index
        playingIndex = -1
        pump(speechGeneration, flush = true)
    }

    fun setTtsSpeed(value: Float) = viewModelScope.launch {
        settingsRepo.setTtsSpeed(value)
        // Everything already queued was synthesised at the old rate, so
        // re-point at the sentence being spoken to make the change audible
        // now rather than three sentences from now.
        if (_speaking.value) {
            restartAt(if (playingIndex >= 0) playingIndex else 0)
        }
    }

    fun setTtsPitch(value: Float) = viewModelScope.launch { settingsRepo.setTtsPitch(value) }

    fun setTtsVoice(id: String?) = viewModelScope.launch {
        settingsRepo.setTtsVoice(id)
        speech.selectVoice(id)
        if (_speaking.value) {
            restartAt(if (playingIndex >= 0) playingIndex else 0)
        }
    }

    /**
     * Switches TTS engine and reloads its voices.
     *
     * A full teardown, because TextToSpeech binds to one engine for its
     * lifetime - there is no setEngine(). Any saved voice is cleared too:
     * voice names are engine-specific, so keeping it would leave a dangling
     * reference that silently falls back to the default.
     */
    fun setTtsEngine(packageName: String?) = viewModelScope.launch {
        val wasSpeaking = _speaking.value
        val resumeAt = queue.getOrNull(playingIndex)?.start

        stopSpeaking()
        settingsRepo.setTtsEngine(packageName)
        settingsRepo.setTtsVoice(null)

        speech.shutdown()
        if (!speech.prepare(packageName)) {
            _speechError.value =
                "That engine would not start. It may still be unpacking - " +
                    "open it once from your app list, then try again."
            return@launch
        }

        speech.setListener(speechListener)
        speech.selectVoice(null)
        _engines.value = speech.engines()
        _voices.value = speech.voices(java.util.Locale.getDefault())

        if (wasSpeaking) startSpeaking(resumeAt)
    }

    /** Speaks a sample line so a voice can be judged before committing. */
    fun previewVoice(voiceId: String?) = viewModelScope.launch {
        if (_speaking.value) return@launch
        if (!speech.isReady && !speech.prepare(settings.value.ttsEnginePackage)) {
            return@launch
        }
        speech.preview(
            text = PREVIEW_SENTENCE,
            voiceId = voiceId,
            speed = settings.value.ttsSpeed,
            pitch = settings.value.ttsPitch
        )
    }

    fun dismissSpeechError() {
        _speechError.value = null
    }

    /**
     * Stops the voice after [minutes].
     *
     * Ticks once a minute rather than sleeping for the whole duration, so the
     * remaining time can be shown - a timer you cannot see is a timer you do
     * not trust when you are falling asleep.
     */
    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return

        sleepJob = viewModelScope.launch {
            var left = minutes
            _sleepRemaining.value = left
            while (left > 0) {
                delay(60_000)
                left--
                _sleepRemaining.value = left
            }
            stopSpeaking()
        }
    }

    /** Remembers the choice and applies it, so 0 also means "cancel". */
    fun applySleepTimer(minutes: Int) {
        viewModelScope.launch { settingsRepo.setTtsSleepMinutes(minutes) }
        if (minutes <= 0) cancelSleepTimer() else startSleepTimer(minutes)
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemaining.value = null
    }

    /** The book as one string, matching the offsets used everywhere else. */
    private fun wholeText(ready: ReaderState.Ready): String =
        ready.content.chapters.joinToString("") { it.text }

    private fun pageForChar(target: Int): Int {
        val ready = _state.value as? ReaderState.Ready ?: return _pageIndex.value
        val pages = ready.pages ?: return _pageIndex.value
        return pageForGlobalChar(pages, ready.content, target)
    }

    private fun rememberChar(target: Int) {
        pendingRestoreChar = target
        scheduleSave()
    }

    override fun onCleared() {
        super.onCleared()
        speech.shutdown()
        // The service outlives the ViewModel by design, but if the ViewModel
        // is going away the reader is gone, so the notification would be a
        // control panel for nothing.
        ReadAloudService.stop(getApplication())
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
