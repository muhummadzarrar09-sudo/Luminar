package dev.recto.reader.ui.reader

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.recto.reader.data.ReaderFont
import dev.recto.reader.data.ReaderSettings
import dev.recto.reader.notifications.Notifications
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PageVerticalPadding = 20.dp

/**
 * Maximum width of the text column.
 *
 * Typography's oldest rule: a line longer than roughly 75 characters makes
 * the eye lose its place on the return sweep, which is exactly the tiring
 * part of reading on a wide screen. In landscape a phone would otherwise set
 * lines of nearly 90 characters.
 *
 * Capping the column and centring it is what every well-set book does, and
 * what Kindle does in landscape. Portrait is narrower than this already, so
 * the cap simply never applies there.
 */
private val MaxLineWidth = 560.dp

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

    val appContext = LocalContext.current.applicationContext

    BackHandler {
        vm.persistNow()
        vm.endSession { minutes, streak ->
            Notifications.goalReached(appContext, minutes, streak)
        }
        onBack()
    }

    // Covers every other way out - process death, config change, the user
    // swiping the app away. Without this a session left open would keep
    // counting while the phone sits in a pocket.
    DisposableEffect(bookId) {
        onDispose {
            vm.persistNow()
            vm.endSession { minutes, streak ->
                Notifications.goalReached(appContext, minutes, streak)
            }
        }
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
                    vm.endSession { minutes, streak ->
                        Notifications.goalReached(appContext, minutes, streak)
                    }
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
            val recent by vm.recentLookups.collectAsStateWithLifecycle()
            VocabularySheet(
                words = vocabulary,
                dueCount = dueCount,
                reviewQueue = reviewQueue,
                recent = recent,
                onStartReview = vm::startReview,
                onAnswer = vm::answerCard,
                onDelete = vm::deleteVocabulary,
                onLookUpAgain = { term ->
                    // Close the sheet first, or the lookup card opens behind
                    // it and looks like nothing happened.
                    vm.dismissSheet()
                    vm.lookUp(term)
                },
                onDeleteRecent = vm::deleteRecentLookup,
                onClearRecent = vm::clearRecentLookups,
                onDismiss = vm::dismissSheet
            )
        }

        ReaderSheet.READ_ALOUD -> {
            val speaking by vm.speaking.collectAsStateWithLifecycle()
            val voices by vm.voices.collectAsStateWithLifecycle()
            val engines by vm.engines.collectAsStateWithLifecycle()
            val sleepLeft by vm.sleepRemaining.collectAsStateWithLifecycle()
            val speechError by vm.speechError.collectAsStateWithLifecycle()
            val readerSettings by vm.settings.collectAsStateWithLifecycle()

            ReadAloudSheet(
                speaking = speaking,
                settings = readerSettings,
                voices = voices,
                engines = engines,
                currentEngine = readerSettings.ttsEnginePackage,
                sleepRemaining = sleepLeft,
                error = speechError,
                onToggle = vm::toggleSpeaking,
                onSkip = vm::skipSentence,
                onSpeed = vm::setTtsSpeed,
                onPitch = vm::setTtsPitch,
                onVoice = vm::setTtsVoice,
                onEngine = vm::setTtsEngine,
                onPreview = vm::previewVoice,
                onSleep = vm::applySleepTimer,
                onDismissError = vm::dismissSpeechError,
                // Dismissing the sheet leaves the voice running on purpose:
                // you close it to see the page it is reading. The menu and
                // the notification both stop it.
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

    val undo by vm.undo.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    val spokenRange by vm.spokenRange.collectAsStateWithLifecycle()

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
            color = settings.theme.text,

            // The four settings below are what separate "text on a screen"
            // from "a page of a book".

            // Hyphenation. Justified text without it opens rivers of white
            // space between words, which is the single most tiring thing
            // about badly set justified text. Requires LineBreak.Paragraph:
            // the Simple strategy only hyphenates when a word alone exceeds
            // the line, which is almost never.
            hyphens = Hyphens.Auto,

            // Paragraph-aware line breaking looks ahead over the whole
            // paragraph rather than greedily filling each line, so word
            // spacing stays even instead of lurching line to line. Slightly
            // more expensive, imperceptible at page size.
            lineBreak = LineBreak.Paragraph,

            // A whisper of extra tracking. Print sets body text looser than
            // screen defaults, and at reading sizes this measurably reduces
            // letter crowding without looking spaced out.
            letterSpacing = 0.01.em,

            // Trim the extra leading the platform adds above the first line
            // and below the last, so the text block sits optically centred
            // in the margins rather than pushed down.
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both
            )
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

    // Geometry for the floating selection toolbar. All in pixels, all in the
    // coordinate space of the root Box below.
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    var selectionBounds by remember { mutableStateOf<SelectionBounds?>(null) }

    // Measured, not assumed. The top bar's height depends on the title's font
    // and the bottom bar's on the scrubber, so hard-coding either would drift
    // the moment they change.
    var topChromeHeight by remember { mutableStateOf(0) }
    var bottomChromeHeight by remember { mutableStateOf(0) }

    // A stale rectangle is worse than none: it would point the caret at words
    // that are no longer selected.
    LaunchedEffect(selection) {
        if (selection == null) {
            selectionBounds = null
            // Also forget the measured size, or the NEXT selection skips its
            // entrance: animateFloatAsState starts at its target, so a card
            // that is already "measured" on first composition animates from
            // 1 to 1. Only the very first selection would ever animate.
            toolbarSize = IntSize.Zero
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .onGloballyPositioned { containerOrigin = it.positionInWindow() }
    ) {

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
                .widthIn(max = MaxLineWidth)
                .align(Alignment.TopCenter)
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
                spokenRange = spokenRange,
                darkTheme = settings.theme.isDark,
                onSelectionChange = vm::setSelection,
                onSelectionBounds = { selectionBounds = it },
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

        // Selection toolbar, placed against the words rather than parked at
        // the bottom of the screen.
        //
        // The old version was pinned to BottomCenter. That is fine when you
        // select near the top, and wrong every other time: selecting the last
        // paragraph put the card directly over it, and with the chrome open
        // the card hid behind the scrubber entirely.
        if (selection != null && selectionBounds != null && containerSize.width > 0) {
            val clipboard = LocalClipboardManager.current
            val density = LocalDensity.current
            val layoutDirection = LocalLayoutDirection.current
            val insets = WindowInsets.safeDrawing

            // Window space -> container space. Subtracting the container's
            // own window position is what makes this correct regardless of
            // anything wrapping the reader.
            val anchored = selectionBounds!!
            val localBounds = anchored.copy(
                top = anchored.top - containerOrigin.y,
                bottom = anchored.bottom - containerOrigin.y,
                topAnchorX = anchored.topAnchorX - containerOrigin.x,
                bottomAnchorX = anchored.bottomAnchorX - containerOrigin.x
            )

            // Computed on every recomposition rather than remembered. It is a
            // dozen comparisons on values that are already in registers, and
            // the alternative is a key list that has to name the insets and
            // the density - miss one and the toolbar quietly uses last
            // orientation's numbers. Not worth the risk to save this.
            val placement = SelectionAnchor.place(
                bounds = localBounds,
                containerWidth = containerSize.width,
                containerHeight = containerSize.height,
                toolbarWidth = toolbarSize.width,
                toolbarHeight = toolbarSize.height,
                // Chrome only blocks space while it is actually on screen.
                topBlocked = insets.getTop(density) +
                    if (chromeVisible) topChromeHeight else 0,
                bottomBlocked = insets.getBottom(density) +
                    if (chromeVisible) bottomChromeHeight else 0,
                leftBlocked = insets.getLeft(density, layoutDirection),
                rightBlocked = insets.getRight(density, layoutDirection),
                gap = with(density) { 10.dp.roundToPx() },
                margin = with(density) { 12.dp.roundToPx() },
                caretInset = with(density) { 22.dp.toPx() }
            )

            // Grow out of the caret.
            //
            // Doubles as the fix for the first-frame flash: until the card has
            // been measured its size is zero, the placement is meaningless,
            // and it would otherwise appear at the top-left corner for one
            // frame before jumping. Progress starts at 0 and only runs once a
            // real measurement exists, so that frame is invisible instead.
            //
            // Entrance only, deliberately. While you drag to extend a
            // selection the card follows your finger every frame - animating
            // that would make it lag behind the thing it is pointing at.
            val measured = toolbarSize.width > 0
            val appear by animateFloatAsState(
                targetValue = if (measured) 1f else 0f,
                animationSpec = tween(durationMillis = 130),
                label = "toolbarAppear"
            )

            SelectionToolbar(
                onColour = vm::highlightSelection,
                onDefine = vm::lookUpSelection,
                onNote = vm::addNoteToSelection,
                onCopy = {
                    clipboard.setText(AnnotatedString(vm.selectedText()))
                    vm.clearSelection()
                },
                caretX = with(density) { placement.caretX.toDp() },
                caretAbove = placement.above,
                defaultColour = settings.defaultHighlightColour,
                onDefaultColour = vm::setDefaultHighlightColour,
                darkTheme = settings.theme.isDark,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(placement.x, placement.y) }
                    .onSizeChanged { toolbarSize = it }
                    .graphicsLayer {
                        alpha = appear
                        // 0.9 rather than 0: a card springing from nothing
                        // reads as a popup, a card easing up from nearly
                        // full size reads as paper being placed down.
                        val s = 0.90f + 0.10f * appear
                        scaleX = s
                        scaleY = s
                        // Pivot on the caret, so it grows out of the words
                        // rather than out of its own middle.
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (toolbarSize.width > 0) {
                                (placement.caretX / toolbarSize.width)
                                    .coerceIn(0f, 1f)
                            } else {
                                0.5f
                            },
                            pivotFractionY = if (placement.above) 1f else 0f
                        )
                    }
            )
        }

        // Top chrome
        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                // Measured so the selection toolbar knows what to avoid. The
                // height depends on the title's font, so hard-coding it would
                // drift the moment typography changes.
                .onSizeChanged { topChromeHeight = it.height }
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
                            DropdownMenuItem(
                                text = {
                                    Text(if (speaking) "Stop reading" else "Read aloud")
                                },
                                onClick = {
                                    menuOpen = false
                                    if (speaking) {
                                        vm.stopSpeaking()
                                    } else {
                                        vm.showSheet(ReaderSheet.READ_ALOUD)
                                        vm.startSpeaking()
                                    }
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomChromeHeight = it.height }
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

        // Undo.
        //
        // A hand-rolled bar rather than a Scaffold's SnackbarHost, because
        // this screen has no Scaffold - it is a bare Box so the page can run
        // edge to edge under the system bars - and adding one just to host a
        // snackbar would re-introduce the insets the reader deliberately
        // manages itself.
        //
        // Sits above the selection toolbar in the Box so it is never covered
        // by it. It also gets out of the way of the bottom chrome, since
        // undoing right after highlighting is exactly when the scrubber might
        // be open.
        AnimatedVisibility(
            visible = undo != null,
            enter = fadeIn(tween(120)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // Held so the text does not blank out during the exit animation:
            // `undo` is already null by then, and reading it directly would
            // leave an empty bar sliding away.
            val shown = remember { mutableStateOf<UndoableAction?>(null) }
            LaunchedEffect(undo) { if (undo != null) shown.value = undo }

            Surface(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 6.dp
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = shown.value?.message.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        onClick = vm::performUndo,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.inversePrimary
                        )
                    ) {
                        Text("Undo")
                    }
                }
            }
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
    /** Absolute range being read aloud, highlighted as the voice moves. */
    spokenRange: IntRange?,
    darkTheme: Boolean,
    onSelectionChange: (IntRange?) -> Unit,
    /** Reports where the selection sits, in window pixels. */
    onSelectionBounds: (SelectionBounds?) -> Unit,
    onLookUp: (String) -> Unit,
    /** Absolute book offset of a page's first character. */
    pageStartOf: (Int) -> Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleChrome: () -> Unit
) {
    var dragTotal by remember { mutableFloatStateOf(0f) }

    // Held in a ref so the gesture detectors below can read the current value
    // without `selecting` becoming a pointerInput key. See the comment in the
    // tap handler for why that mattered.
    val selectingRef = remember { mutableStateOf(false) }
    selectingRef.value = selection != null

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(pages.size) {
                detectTapGestures { offset ->
                    // A tap while selecting means "done", not "turn the page".
                    //
                    // `selectingRef` rather than the `selecting` value: using
                    // it as a pointerInput key would tear this detector down
                    // and rebuild it the instant a selection appeared, which
                    // cancelled the very gesture that created the selection.
                    // That was the "have to try twice" bug.
                    if (selectingRef.value) {
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
            .pointerInput(pages.size) {
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        val threshold = size.width * 0.18f
                        // Checked here, not as a detector key, for the same
                        // reason as above.
                        if (!selectingRef.value && abs(dragTotal) > threshold) {
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
            Box(Modifier.fillMaxSize().background(background)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // Insets, then the line-length cap, then margins.
                        // Whatever survives all of that is exactly the box the
                        // text gets, so it must match the probe above exactly
                        // or pagination measures the wrong area.
                        .safeDrawingPadding()
                        .widthIn(max = MaxLineWidth)
                        .align(Alignment.TopCenter)
                        .padding(
                            horizontal = horizontalPadding,
                            vertical = PageVerticalPadding
                        )
                ) {
                if (page != null) {
                    val pageStart = pageStartOf(index)

                    // Layout of THIS page, needed to turn a touch point into a
                    // character offset. Set by Text's onTextLayout.
                    //
                    // Declared inside the AnimatedContent lambda, so each page
                    // owns its own. It used to live outside, keyed on
                    // pageIndex - but AnimatedContent keeps both the outgoing
                    // and incoming page composed during a turn, and the
                    // outgoing one fires onTextLayout too. Both wrote to the
                    // same ref, so the last writer won and gestures could
                    // resolve against the wrong page's layout.
                    //
                    // Held in a ref, not a captured var: the pointerInput
                    // lambda below is created once and would otherwise close
                    // over the value as it was at creation time - which is
                    // null, because onTextLayout has not fired yet. That was
                    // the other half of the "first long-press does nothing"
                    // bug.
                    val layoutRef = remember(index) {
                        mutableStateOf<TextLayoutResult?>(null)
                    }

                    // Where this Text sits in WINDOW coordinates. The Text is
                    // nested several padded boxes deep, so offsets from
                    // TextLayoutResult are local to it and have to be
                    // translated before the toolbar can use them.
                    //
                    // Window coordinates rather than root: the caller
                    // subtracts the container's own window position, which
                    // works no matter what ends up wrapping this screen.
                    // positionInRoot would silently be wrong the day a
                    // Scaffold or padding appears above the reader Box.
                    val textOrigin = remember(index) { mutableStateOf(Offset.Zero) }

                    // Measure the selection whenever it, or the layout under
                    // it, changes.
                    //
                    // Guarded on `index == pageIndex` because AnimatedContent
                    // keeps the outgoing page composed through the turn
                    // animation. Without the guard the old page also reports
                    // bounds, and whichever effect happened to run last wins -
                    // so the toolbar would sometimes point at the previous
                    // page's geometry.
                    val layout = layoutRef.value
                    val origin = textOrigin.value
                    LaunchedEffect(selection, layout, origin, pageStart, index, pageIndex) {
                        if (index != pageIndex) return@LaunchedEffect
                        val sel = selection
                        if (sel == null || layout == null) {
                            if (index == pageIndex) onSelectionBounds(null)
                            return@LaunchedEffect
                        }
                        onSelectionBounds(
                            SelectionAnchor.boundsOf(
                                layout = layout,
                                from = sel.first - pageStart,
                                to = sel.last - pageStart,
                                dx = origin.x,
                                dy = origin.y
                            )
                        )
                    }

                    Text(
                        text = PageText.build(
                            text = page.text,
                            pageStart = pageStart,
                            annotations = annotations,
                            selection = selection,
                            darkTheme = darkTheme,
                            selectionColour = selectionTint(style.color),
                            spoken = spokenRange,
                            spokenColour = spokenTint(style.color)
                        ),
                        style = style,
                        onTextLayout = { layoutRef.value = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                textOrigin.value = coords.positionInWindow()
                            }
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
                                        val l = layoutRef.value
                                            ?: return@detectDragGesturesAfterLongPress
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
                                        val l = layoutRef.value
                                            ?: return@detectDragGesturesAfterLongPress
                                        if (anchor < 0) return@detectDragGesturesAfterLongPress
                                        dragged = true
                                        val off = l.getOffsetForPosition(change.position)
                                            .coerceIn(0, page.text.length)

                                        // Snap to words, then to sentences
                                        // once the drag leaves the sentence
                                        // it started in. Raw character
                                        // offsets meant every long selection
                                        // ended mid-word and needed nudging.
                                        val range = PageText.dragSelection(
                                            text = page.text,
                                            anchor = anchor,
                                            cursor = off
                                        )
                                        if (!range.isEmpty()) {
                                            onSelectionChange(
                                                (pageStart + range.first)..
                                                    (pageStart + range.last + 1)
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
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
