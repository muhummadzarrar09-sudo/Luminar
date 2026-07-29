package dev.recto.reader.ui.reader

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.recto.reader.data.ReaderFont
import dev.recto.reader.data.ReaderSettings
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PageVerticalPadding = 20.dp

@Composable
fun ReaderScreen(
    bookId: Long,
    /** Absolute char offset to land on, from a library search hit. */
    jumpToChar: Int? = null,
    onJumpConsumed: () -> Unit = {},
    onBack: () -> Unit,
    vm: ReaderViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pageIndex by vm.pageIndex.collectAsStateWithLifecycle()
    val chromeVisible by vm.chromeVisible.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val annotations by vm.annotations.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val editingNote by vm.editingNote.collectAsStateWithLifecycle()
    val lookupWord by vm.lookupWord.collectAsStateWithLifecycle()
    val lookupContext by vm.lookupContext.collectAsStateWithLifecycle()
    val dictionary by vm.dictionary.collectAsStateWithLifecycle()
    val wikipedia by vm.wikipedia.collectAsStateWithLifecycle()
    val wordSaved by vm.wordSaved.collectAsStateWithLifecycle()

    LaunchedEffect(bookId) { vm.load(bookId) }

    // A search hit carries a position. Wait for pagination before jumping -
    // pages do not exist until the reader has measured itself.
    val pagesReady = (state as? ReaderState.Ready)?.pages != null
    LaunchedEffect(jumpToChar, pagesReady) {
        if (jumpToChar != null && pagesReady) {
            vm.goToChar(jumpToChar)
            onJumpConsumed()
        }
    }

    BackHandler {
        vm.persistNow()
        onBack()
    }

    // The page owns the whole screen, so the surface takes the reading theme
    // rather than the app theme. That is what makes Sepia and Black actually
    // feel like a different page instead of a tinted panel.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = settings.theme.background
    ) {
        when (val s = state) {
            is ReaderState.Loading -> Centered { CircularProgressIndicator() }

            is ReaderState.Error -> Centered {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(36.dp)
                ) {
                    Text(
                        text = "Cannot open this book",
                        style = MaterialTheme.typography.titleLarge,
                        color = settings.theme.text
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = settings.theme.muted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    TextButton(onClick = onBack) { Text("Back to library") }
                }
            }

            is ReaderState.Ready -> ReaderContent(
                state = s,
                pageIndex = pageIndex,
                chromeVisible = chromeVisible,
                settings = settings,
                annotations = annotations,
                selection = selection,
                vm = vm,
                onBack = {
                    vm.persistNow()
                    onBack()
                }
            )
        }
    }

    when (sheet) {
        ReaderSheet.SETTINGS -> ReaderSettingsSheet(
            settings = settings,
            onTheme = vm::setTheme,
            onFont = vm::setFont,
            onFontSize = vm::setFontSize,
            onLineSpacing = vm::setLineSpacing,
            onMargin = vm::setMargin,
            onJustify = vm::setJustify,
            onVolumeKeys = vm::setVolumeKeys,
            onKeepScreenOn = vm::setKeepScreenOn,
            onWarmth = vm::setWarmth,
            onDim = vm::setDim,
            onUseReaderBrightness = vm::setUseReaderBrightness,
            onBrightness = vm::setBrightness,
            onDismiss = vm::dismissSheet
        )

        ReaderSheet.CONTENTS -> TableOfContentsSheet(
            entries = vm.tableOfContents(),
            currentChapter = vm.currentChapterIndex(),
            onSelect = vm::goToChapter,
            onDismiss = vm::dismissSheet
        )

        ReaderSheet.NOTEBOOK -> {
            val context = LocalContext.current
            NotebookSheet(
                annotations = annotations,
                onJump = { vm.goToChar(it.startChar) },
                onEditNote = vm::editNote,
                onDelete = vm::deleteAnnotation,
                onExport = {
                    val ready = state as? ReaderState.Ready
                    val body = buildExportText(
                        bookTitle = ready?.content?.title ?: ready?.book?.title ?: "Book",
                        author = ready?.content?.author ?: ready?.book?.author,
                        annotations = annotations
                    )
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, body)
                            },
                            "Export notes"
                        )
                    )
                },
                onDismiss = vm::dismissSheet
            )
        }

        ReaderSheet.SEARCH -> {
            val query by vm.searchQuery.collectAsStateWithLifecycle()
            val hits by vm.searchHits.collectAsStateWithLifecycle()
            val searching by vm.searching.collectAsStateWithLifecycle()
            InBookSearchSheet(
                query = query,
                hits = hits,
                searching = searching,
                onQueryChange = vm::search,
                onJump = { vm.goToChar(it.charOffset) },
                onDismiss = {
                    vm.clearSearch()
                    vm.dismissSheet()
                }
            )
        }

        ReaderSheet.VOCABULARY -> {
            val vocabulary by vm.vocabulary.collectAsStateWithLifecycle()
            val dueCount by vm.dueCount.collectAsStateWithLifecycle()
            val reviewQueue by vm.reviewQueue.collectAsStateWithLifecycle()
            VocabularySheet(
                words = vocabulary,
                dueCount = dueCount,
                reviewQueue = reviewQueue,
                onStartReview = vm::startReview,
                onAnswer = vm::answerCard,
                onDelete = vm::deleteVocabulary,
                onDismiss = vm::dismissSheet
            )
        }

        ReaderSheet.NONE -> Unit
    }

    lookupWord?.let { word ->
        val context = LocalContext.current
        LookupSheet(
            word = word,
            contextSentence = lookupContext,
            dictionary = dictionary,
            wikipedia = wikipedia,
            isSaved = wordSaved,
            onSave = vm::saveCurrentWord,
            onPlayAudio = { url -> playPronunciation(context, url) },
            onRetry = vm::retryLookup,
            onDismiss = vm::dismissLookup
        )
    }

    editingNote?.let { note ->
        NoteEditorDialog(
            annotation = note,
            onSave = { vm.saveNote(note.id, it) },
            onDelete = {
                vm.deleteAnnotation(note.id)
                vm.dismissNoteEditor()
            },
            onDismiss = vm::dismissNoteEditor
        )
    }
}

@Composable
private fun ReaderContent(
    state: ReaderState.Ready,
    pageIndex: Int,
    chromeVisible: Boolean,
    settings: ReaderSettings,
    annotations: List<dev.recto.reader.data.db.AnnotationEntity>,
    selection: IntRange?,
    vm: ReaderViewModel,
    onBack: () -> Unit
) {
    val measurer = rememberTextMeasurer()

    // Derived from settings, so any typography change produces a new style
    // object and therefore a re-pagination.
    val readingStyle = remember(settings) {
        TextStyle(
            fontFamily = when (settings.font) {
                ReaderFont.SERIF -> FontFamily.Serif
                ReaderFont.SANS -> FontFamily.SansSerif
                ReaderFont.MONO -> FontFamily.Monospace
            },
            fontSize = settings.fontSizeSp.sp,
            lineHeight = (settings.fontSizeSp * settings.lineSpacing.multiplier).sp,
            textAlign = if (settings.justify) TextAlign.Justify else TextAlign.Start,
            color = settings.theme.text
        )
    }

    val horizontalPadding = settings.margin.sizeDp.dp

    // The size of the text area AFTER system-bar insets and margins have been
    // taken out. Measured from the very box the text is drawn in, rather than
    // computed from the screen size.
    //
    // This is what the landscape bug was: pagination measured the full screen
    // while the page applied statusBarsPadding + navigationBarsPadding inside
    // it, so the text box was always smaller than the box we paginated for.
    // In portrait the difference hid inside the margins; in landscape the
    // navigation bar moves to the side and the last lines fell off the page.
    var textAreaWidth by remember { mutableStateOf(0) }
    var textAreaHeight by remember { mutableStateOf(0) }

    // Re-paginate on any change to the usable area or the typography.
    // Position survives because it is anchored to a character offset.
    LaunchedEffect(textAreaWidth, textAreaHeight, state.content, readingStyle) {
        if (textAreaWidth <= 0 || textAreaHeight <= 0) return@LaunchedEffect

        vm.rememberPositionBeforeRepaginate()

        val pages = withContext(Dispatchers.Default) {
            Paginator.paginate(
                chapters = state.content.chapters.map { it.text },
                measurer = measurer,
                style = readingStyle,
                widthPx = textAreaWidth,
                heightPx = textAreaHeight
            )
        }
        vm.onPaginated(pages)
    }

    val pages = state.pages

    // Hardware volume keys turn pages. These are intercepted at the Activity
    // level via VolumeKeyHandler - Compose's onKeyEvent never sees them,
    // because the system routes volume keys to the window before focus
    // targets get a look in.
    VolumeKeyPageTurns(
        enabled = settings.volumeKeysTurnPages,
        onNext = vm::next,
        onPrevious = vm::previous
    )

    KeepScreenOn(enabled = settings.keepScreenOn)

    WindowBrightness(
        level = if (settings.useReaderBrightness) settings.brightness else null
    )

    Box(Modifier.fillMaxSize()) {

        // An invisible stand-in for the text box, laid out with exactly the
        // same insets and margins. It is always present, so it can report the
        // text-area size before any page exists.
        //
        // Without it there is a deadlock: pages are null so we show a spinner,
        // the spinner is not the text box, nothing reports a size, and pages
        // stay null forever.
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = horizontalPadding, vertical = PageVerticalPadding)
                .onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0) {
                        textAreaWidth = size.width
                        textAreaHeight = size.height
                    }
                }
        )

        if (pages == null) {
            Centered { CircularProgressIndicator() }
        } else if (pages.isEmpty()) {
            Centered {
                Text("This book appears to be empty.", color = settings.theme.muted)
            }
        } else {
            PageSurface(
                pages = pages,
                pageIndex = pageIndex,
                style = readingStyle,
                background = settings.theme.background,
                horizontalPadding = horizontalPadding,
                annotations = annotations,
                selection = selection,
                darkTheme = settings.theme.isDark,
                onSelectionChange = vm::setSelection,
                onLookUp = { vm.lookUp(it) },
                pageStartOf = vm::pageStartOf,
                onNext = vm::next,
                onPrevious = vm::previous,
                onToggleChrome = vm::toggleChrome
            )
        }

        // Warmth and dimming go directly over the page and nothing else.
        // Composed here - after the page, before the chrome - so the toolbar
        // and scrubber stay legible while you drag the sliders. Overlaying the
        // controls too would make the sliders progressively harder to read the
        // more warmth you applied, which is the opposite of helpful.
        EyeComfortOverlay(warmth = settings.warmth, dim = settings.dim)

        // Selection toolbar. Sits just above the bottom edge so it never
        // covers the words you are selecting near the middle of the page.
        if (selection != null) {
            val clipboard = LocalClipboardManager.current
            SelectionToolbar(
                onColour = vm::highlightSelection,
                onDefine = vm::lookUpSelection,
                onNote = vm::addNoteToSelection,
                onCopy = {
                    clipboard.setText(AnnotatedString(vm.selectedText()))
                    vm.clearSelection()
                },
                onDismiss = vm::clearSelection,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
            )
        }

        // Top chrome
        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
                var menuOpen by remember { mutableStateOf(false) }
                val bookmarked = remember(annotations, pageIndex) {
                    vm.isCurrentPageBookmarked()
                }

                // Back, title, Aa, overflow. Aa deliberately stays on the bar:
                // the test for the menu is whether you touch it mid-chapter,
                // and text size and theme are constant while contents and the
                // notebook are occasional. Everything else moves behind the
                // dots so the title finally gets the room to be readable.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text("Back") }

                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = state.content.title ?: state.book.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val chapter = state.content.chapters
                            .getOrNull(vm.currentChapterIndex())?.title
                        if (!chapter.isNullOrBlank()) {
                            Text(
                                text = chapter,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    TextButton(onClick = { vm.showSheet(ReaderSheet.SETTINGS) }) {
                        Text("Aa", style = MaterialTheme.typography.titleLarge)
                    }

                    Box {
                        TextButton(onClick = { menuOpen = true }) {
                            Text("\u22EE", style = MaterialTheme.typography.titleLarge)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (bookmarked) "Remove bookmark" else "Bookmark this page") },
                                onClick = {
                                    menuOpen = false
                                    vm.toggleBookmark()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Search in book") },
                                onClick = {
                                    menuOpen = false
                                    vm.showSheet(ReaderSheet.SEARCH)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Contents") },
                                onClick = {
                                    menuOpen = false
                                    vm.showSheet(ReaderSheet.CONTENTS)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Notebook") },
                                onClick = {
                                    menuOpen = false
                                    vm.showSheet(ReaderSheet.NOTEBOOK)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Vocabulary") },
                                onClick = {
                                    menuOpen = false
                                    vm.showSheet(ReaderSheet.VOCABULARY)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom chrome: scrubber + position
        if (pages != null && pages.isNotEmpty()) {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        var scrubbing by remember { mutableStateOf(false) }
                        var scrubValue by remember { mutableFloatStateOf(pageIndex.toFloat()) }

                        LaunchedEffect(pageIndex, scrubbing) {
                            if (!scrubbing) scrubValue = pageIndex.toFloat()
                        }

                        Slider(
                            value = scrubValue,
                            onValueChange = {
                                scrubbing = true
                                scrubValue = it
                            },
                            onValueChangeFinished = {
                                scrubbing = false
                                vm.goTo(scrubValue.toInt())
                            },
                            valueRange = 0f..(pages.size - 1).coerceAtLeast(1).toFloat()
                        )

                        val shown = if (scrubbing) scrubValue.toInt() else pageIndex
                        val percent = ((shown + 1) * 100 / pages.size).coerceIn(1, 100)

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Page ${shown + 1} of ${pages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Always-visible hairline progress, even with chrome hidden.
        if (pages != null && pages.isNotEmpty() && !chromeVisible) {
            LinearProgressIndicator(
                progress = { (pageIndex + 1).toFloat() / pages.size },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp),
                color = settings.theme.muted,
                trackColor = settings.theme.background
            )
        }
    }
}

@Composable
private fun PageSurface(
    pages: List<Page>,
    pageIndex: Int,
    style: TextStyle,
    background: androidx.compose.ui.graphics.Color,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    annotations: List<dev.recto.reader.data.db.AnnotationEntity>,
    selection: IntRange?,
    darkTheme: Boolean,
    onSelectionChange: (IntRange?) -> Unit,
    onLookUp: (String) -> Unit,
    /** Absolute book offset of a page's first character. */
    pageStartOf: (Int) -> Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleChrome: () -> Unit
) {
    var dragTotal by remember { mutableFloatStateOf(0f) }

    // Layout of the page currently on screen, needed to turn a touch point
    // into a character offset. Set by Text's onTextLayout.
    var layout by remember(pageIndex) { mutableStateOf<TextLayoutResult?>(null) }

    val selecting = selection != null

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(pages.size, selecting) {
                detectTapGestures { offset ->
                    // A tap while selecting means "done", not "turn the page".
                    // Turning the page mid-selection would be maddening.
                    if (selecting) {
                        onSelectionChange(null)
                        return@detectTapGestures
                    }
                    // Kindle's three zones: left third back, right third
                    // forward, centre toggles the chrome.
                    when {
                        offset.x < size.width * 0.33f -> onPrevious()
                        offset.x > size.width * 0.67f -> onNext()
                        else -> onToggleChrome()
                    }
                }
            }
            .pointerInput(pages.size, selecting) {
                if (selecting) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        val threshold = size.width * 0.18f
                        if (abs(dragTotal) > threshold) {
                            if (dragTotal < 0) onNext() else onPrevious()
                        }
                        dragTotal = 0f
                    },
                    onHorizontalDrag = { _, amount -> dragTotal += amount }
                )
            }
    ) {
        AnimatedContent(
            targetState = pageIndex,
            transitionSpec = {
                val forward = targetState > initialState
                val dir = if (forward) 1 else -1
                (
                    slideInHorizontally(tween(260)) { w -> dir * w / 6 } +
                        fadeIn(tween(200))
                    ) togetherWith (
                    slideOutHorizontally(tween(260)) { w -> -dir * w / 6 } +
                        fadeOut(tween(160))
                    )
            },
            label = "page"
        ) { index ->
            val page = pages.getOrNull(index)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(background)
                    // Insets first, then margins. Whatever survives all of
                    // that is exactly the box the text gets, so that is the
                    // box we measure and paginate against.
                    .safeDrawingPadding()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = PageVerticalPadding
                    )
            ) {
                if (page != null) {
                    val pageStart = pageStartOf(index)

                    Text(
                        text = PageText.build(
                            text = page.text,
                            pageStart = pageStart,
                            annotations = annotations,
                            selection = selection,
                            darkTheme = darkTheme,
                            selectionColour = selectionTint(style.color)
                        ),
                        style = style,
                        onTextLayout = { layout = it },
                        modifier = Modifier
                            .fillMaxSize()
                            // Long-press selects a word; dragging afterwards
                            // extends the selection. Placed on the Text rather
                            // than the outer Box so offsets map directly onto
                            // the laid-out text with no coordinate juggling.
                            .pointerInput(index, pageStart) {
                                var anchor = -1
                                var dragged = false
                                var pressedWord: String? = null

                                detectDragGesturesAfterLongPress(
                                    onDragStart = { pos ->
                                        val l = layout ?: return@detectDragGesturesAfterLongPress
                                        val off = l.getOffsetForPosition(pos)
                                        val word = PageText.wordBoundsAt(page.text, off)
                                        if (word != null) {
                                            anchor = word.first
                                            dragged = false
                                            pressedWord = page.text.substring(
                                                word.first,
                                                (word.last + 1).coerceAtMost(page.text.length)
                                            )
                                            onSelectionChange(
                                                (pageStart + word.first)..(pageStart + word.last + 1)
                                            )
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val l = layout ?: return@detectDragGesturesAfterLongPress
                                        if (anchor < 0) return@detectDragGesturesAfterLongPress
                                        dragged = true
                                        val off = l.getOffsetForPosition(change.position)
                                            .coerceIn(0, page.text.length)
                                        val from = minOf(anchor, off)
                                        val to = maxOf(anchor, off)
                                        if (to > from) {
                                            onSelectionChange(
                                                (pageStart + from)..(pageStart + to)
                                            )
                                        }
                                    },
                                    onDragEnd = {
                                        // Long-press and release on a single
                                        // word means "what does this mean?".
                                        // Long-press and DRAG means "select a
                                        // phrase", so no card - the toolbar is
                                        // what you want there. This is the
                                        // whole gesture design: no new taps,
                                        // no conflict with page turns.
                                        if (!dragged) {
                                            pressedWord?.let { onLookUp(it) }
                                        }
                                        anchor = -1
                                        dragged = false
                                        pressedWord = null
                                    },
                                    onDragCancel = {
                                        anchor = -1
                                        dragged = false
                                        pressedWord = null
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
