// app/src/main/java/com/luminar/reader/presentation/reader/ReaderScreen.kt
package com.luminar.reader.presentation.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.barteksc.pdfviewer.PDFView
import com.luminar.reader.R
import com.luminar.reader.data.model.AppTheme
import com.luminar.reader.data.model.BookFormat
import com.luminar.reader.presentation.components.ErrorReportDialog
import com.luminar.reader.presentation.theme.LuminarGold
import com.luminar.reader.presentation.theme.next
import com.luminar.reader.presentation.theme.readerBackgroundColor
import com.luminar.reader.presentation.theme.readerControlsContainerColor
import com.luminar.reader.presentation.theme.readerControlsContentColor
import com.luminar.reader.presentation.theme.usesPdfNightMode
import java.io.File
import kotlin.math.roundToInt

private const val CONTROLS_ANIMATION_DURATION_MILLIS = 300

@Composable
fun ReaderScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    DisposableEffect(activity) {
        val window = activity?.window
        val decorView = window?.decorView
        val controller = if (window != null && decorView != null) {
            WindowCompat.getInsetsController(window, decorView)
        } else null

        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(activity, uiState.keepScreenOn) {
        val window = activity?.window
        if (uiState.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(Unit) {
        viewModel.setReaderVisible(true)

        onDispose {
            viewModel.saveCurrentProgressImmediately()
            viewModel.setReaderVisible(false)
        }
    }

    DisposableEffect(lifecycleOwner.lifecycle) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.saveCurrentProgressImmediately()
            }
        }

        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    // Error report dialog
    if (uiState.showErrorReport && uiState.error != null) {
        ErrorReportDialog(
            errorMessage = uiState.error,
            onDismiss = { viewModel.onEvent(ReaderEvent.DismissErrorReport) },
            onSendReport = { note -> viewModel.onEvent(ReaderEvent.SendErrorReport(note)) }
        )
    }

    val backgroundColor = uiState.currentTheme.readerBackgroundColor()
    val book = uiState.book
    val file = remember(book?.filePath) { book?.filePath?.let(::File) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = LuminarGold
                )
            }

            book == null -> {
                ReaderMessage(
                    message = uiState.error ?: "Book not found",
                    onNavigateBack = currentOnNavigateBack
                )
            }

            file == null || !file.exists() -> {
                ReaderMessage(
                    message = "File not found",
                    onNavigateBack = currentOnNavigateBack
                )
            }

            else -> {
                if (uiState.isComicBook) {
                    val comicPaths = uiState.comicImagePaths
                    if (comicPaths != null) {
                        ComicReaderView(
                            comicFile = file,
                            imagePaths = comicPaths,
                            theme = uiState.currentTheme,
                            initialPage = uiState.currentPage,
                            onPageChanged = { page ->
                                viewModel.onEvent(ReaderEvent.PageChanged(page))
                            },
                            onToggleControls = {
                                viewModel.onEvent(ReaderEvent.ToggleControls)
                            },
                            getImageBytes = { f, path ->
                                viewModel.getComicImageBytes(path)
                            }
                        )
                    } else {
                        ReaderLoadingState(
                            modifier = Modifier.align(Alignment.Center),
                            message = "Loading comic…"
                        )
                    }
                } else if (uiState.usesTextRenderer) {
                    val textContent = uiState.textContent
                    if (textContent != null) {
                        val renderFormat = when {
                            uiState.isEpub -> BookFormat.MARKDOWN
                            uiState.isDocument -> BookFormat.MARKDOWN
                            else -> book.format
                        }
                        TextReaderView(
                            content = textContent,
                            format = renderFormat,
                            theme = uiState.currentTheme,
                            fontScale = uiState.fontScale,
                            searchQuery = uiState.searchQuery,
                            isSearchActive = uiState.isSearchActive,
                            currentMatchBlockIndex = if (uiState.currentMatchIndex >= 0 && uiState.searchMatchBlockIndices.isNotEmpty()) {
                                uiState.searchMatchBlockIndices[uiState.currentMatchIndex]
                            } else null,
                            scrollToBlockIndex = uiState.scrollToBlockIndex,
                            onToggleControls = {
                                viewModel.onEvent(ReaderEvent.ToggleControls)
                            },
                            onScrollPositionChanged = { position ->
                                viewModel.onEvent(ReaderEvent.ScrollPositionChanged(position))
                            },
                            onBlocksParsed = viewModel::onBlocksParsed,
                            onScrollToBlockConsumed = viewModel::consumeScrollToBlock,
                            initialScrollPosition = uiState.scrollPosition
                        )
                    } else {
                        ReaderLoadingState(
                            modifier = Modifier.align(Alignment.Center),
                            message = "Parsing document…"
                        )
                    }
                } else {
                    PdfReaderView(
                        file = file,
                        uiState = uiState,
                        onPageChanged = { page ->
                            viewModel.onEvent(ReaderEvent.PageChanged(page))
                        },
                        onPdfLoaded = viewModel::onPdfLoaded,
                        onToggleControls = {
                            viewModel.onEvent(ReaderEvent.ToggleControls)
                        }
                    )
                }

                ReaderControlsOverlay(
                    uiState = uiState,
                    onNavigateBack = {
                        viewModel.saveCurrentProgressImmediately()
                        currentOnNavigateBack()
                    },
                    onToggleTheme = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onEvent(
                            ReaderEvent.ThemeChanged(uiState.currentTheme.next())
                        )
                    },
                    onGoToPage = { page ->
                        viewModel.onEvent(ReaderEvent.GoToPage(page))
                    },
                    onIncreaseFontSize = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onEvent(ReaderEvent.IncreaseFontSize)
                    },
                    onDecreaseFontSize = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onEvent(ReaderEvent.DecreaseFontSize)
                    },
                    onOpenSearch = {
                        viewModel.onEvent(ReaderEvent.OpenSearch)
                    },
                    onToggleTts = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onEvent(ReaderEvent.ToggleTts)
                    },
                    onToggleBookmark = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.onEvent(ReaderEvent.ToggleBookmark)
                    },
                    onInteraction = viewModel::onControlsInteraction
                )

                // TTS controls (independent of controls visibility)
                val ttsState by viewModel.ttsController.state.collectAsStateWithLifecycle()
                if (ttsState.isSpeaking || ttsState.totalChunks > 0) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    TtsControlBar(
                        ttsState = ttsState,
                        theme = uiState.currentTheme,
                        onToggle = { viewModel.onEvent(ReaderEvent.ToggleTts) },
                        onSkipBack = { viewModel.onEvent(ReaderEvent.TtsSkipBackward) },
                        onSkipForward = { viewModel.onEvent(ReaderEvent.TtsSkipForward) },
                        onStop = { viewModel.ttsController.stop() }
                    )
                    }
                }

                // Search bar overlay (independent of controls visibility)
                if (uiState.isSearchActive) {
                    SearchBar(
                        uiState = uiState,
                        onQueryChanged = { viewModel.onEvent(ReaderEvent.UpdateSearchQuery(it)) },
                        onNextMatch = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.onEvent(ReaderEvent.NextMatch)
                        },
                        onPreviousMatch = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.onEvent(ReaderEvent.PreviousMatch)
                        },
                        onClose = { viewModel.onEvent(ReaderEvent.CloseSearch) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfReaderView(
    file: File,
    uiState: ReaderUiState,
    onPageChanged: (Int) -> Unit,
    onPdfLoaded: (Int) -> Unit,
    onToggleControls: () -> Unit
) {
    val backgroundColor = uiState.currentTheme.readerBackgroundColor()
    var pdfViewRef by remember(file.absolutePath) { mutableStateOf<PDFView?>(null) }

    DisposableEffect(file.absolutePath) {
        onDispose {
            pdfViewRef?.recycle()
            pdfViewRef = null
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        factory = { androidContext ->
            PDFView(androidContext, null).also { pdfView ->
                pdfViewRef = pdfView
            }
        },
        update = { pdfView ->
            val loadKey = "${file.absolutePath}:${uiState.currentTheme.name}"
            val maxPage = (uiState.totalPages - 1).coerceAtLeast(0)
            val targetPage = uiState.currentPage.coerceIn(0, maxPage)

            if (pdfView.tag != loadKey) {
                pdfView.recycle()
                pdfView.setBackgroundColor(backgroundColor.toArgb())

                pdfView.fromFile(file)
                    .defaultPage(targetPage)
                    .enableSwipe(true)
                    .swipeHorizontal(true)
                    .enableDoubletap(true)
                    .onPageChange { page, _ ->
                        onPageChanged(page)
                    }
                    .onLoad { pageCount ->
                        onPdfLoaded(pageCount)
                    }
                    .onTap { event ->
                        if (pdfView.isCenterTap(event)) {
                            onToggleControls()
                            true
                        } else {
                            false
                        }
                    }
                    .pageFling(true)
                    .pageSnap(true)
                    .autoSpacing(false)
                    .spacing(0)
                    .nightMode(uiState.currentTheme.usesPdfNightMode())
                    .load()

                pdfView.tag = loadKey
            } else {
                pdfView.setBackgroundColor(backgroundColor.toArgb())

                if (pdfView.currentPage != targetPage) {
                    pdfView.jumpTo(targetPage, true)
                }
            }
        }
    )
}

@Composable
private fun ReaderControlsOverlay(
    uiState: ReaderUiState,
    onNavigateBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onIncreaseFontSize: () -> Unit,
    onDecreaseFontSize: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleTts: () -> Unit,
    onToggleBookmark: () -> Unit,
    onInteraction: () -> Unit
) {
    AnimatedVisibility(
        visible = uiState.showControls,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = CONTROLS_ANIMATION_DURATION_MILLIS,
                easing = FastOutSlowInEasing
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = CONTROLS_ANIMATION_DURATION_MILLIS,
                easing = FastOutSlowInEasing
            ),
            initialOffsetY = { -it / 4 }
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = CONTROLS_ANIMATION_DURATION_MILLIS,
                easing = FastOutSlowInEasing
            )
        ) + slideOutVertically(
            animationSpec = tween(
                durationMillis = CONTROLS_ANIMATION_DURATION_MILLIS,
                easing = FastOutSlowInEasing
            ),
            targetOffsetY = { -it / 4 }
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ReaderTopControls(
                modifier = Modifier.align(Alignment.TopCenter),
                uiState = uiState,
                onNavigateBack = onNavigateBack,
                onToggleTheme = onToggleTheme,
                onOpenSearch = onOpenSearch,
                onToggleTts = onToggleTts,
                onToggleBookmark = onToggleBookmark,
                onInteraction = onInteraction
            )

            ReaderBottomControls(
                modifier = Modifier.align(Alignment.BottomCenter),
                uiState = uiState,
                onGoToPage = onGoToPage,
                onIncreaseFontSize = onIncreaseFontSize,
                onDecreaseFontSize = onDecreaseFontSize,
                onInteraction = onInteraction
            )
        }
    }
}

@Composable
private fun ReaderTopControls(
    modifier: Modifier,
    uiState: ReaderUiState,
    onNavigateBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleTts: () -> Unit,
    onToggleBookmark: () -> Unit,
    onInteraction: () -> Unit
) {
    val containerColor = uiState.currentTheme.readerControlsContainerColor()
    val contentColor = uiState.currentTheme.readerControlsContentColor()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    onInteraction()
                    onNavigateBack()
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back_24),
                    contentDescription = "Back",
                    tint = contentColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.book?.title.orEmpty(),
                    color = contentColor,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (uiState.usesTextRenderer || uiState.isComicBook) {
                    Text(
                        text = uiState.book?.format?.displayName.orEmpty(),
                        color = contentColor.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            if (uiState.usesTextRenderer) {
                IconButton(
                    onClick = {
                        onInteraction()
                        onOpenSearch()
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search_24),
                        contentDescription = "Search",
                        tint = contentColor
                    )
                }

                // TTS button
                IconButton(
                    onClick = {
                        onInteraction()
                        onToggleTts()
                    }
                ) {
                    Text(text = "🔊", fontSize = 18.sp)
                }
            }

            // Bookmark toggle (all formats)
            IconButton(
                onClick = {
                    onInteraction()
                    onToggleBookmark()
                }
            ) {
                Text(
                    text = if (uiState.isCurrentPageBookmarked) "🔖" else "🏷️",
                    fontSize = 18.sp
                )
            }

            TextButton(
                onClick = {
                    onInteraction()
                    onToggleTheme()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LuminarGold
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_palette_24),
                    contentDescription = null,
                    tint = LuminarGold
                )
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = uiState.currentTheme.shortLabel()
                )
            }
        }
    }
}

@Composable
private fun ReaderBottomControls(
    modifier: Modifier,
    uiState: ReaderUiState,
    onGoToPage: (Int) -> Unit,
    onIncreaseFontSize: () -> Unit,
    onDecreaseFontSize: () -> Unit,
    onInteraction: () -> Unit
) {
    val containerColor = uiState.currentTheme.readerControlsContainerColor()
    val contentColor = uiState.currentTheme.readerControlsContentColor()
    val maxPage = (uiState.totalPages - 1).coerceAtLeast(0)
    val sliderMax = maxPage.coerceAtLeast(1)

    var sliderPage by remember { mutableFloatStateOf(uiState.currentPage.toFloat()) }
    var showPageJumpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.currentPage, uiState.totalPages) {
        sliderPage = uiState.currentPage.toFloat()
    }

    if (showPageJumpDialog) {
        PageJumpDialog(
            currentPage = uiState.currentPage,
            totalPages = uiState.totalPages,
            onDismiss = { showPageJumpDialog = false },
            onConfirm = { page ->
                onGoToPage(page)
                showPageJumpDialog = false
            }
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.usesTextRenderer) {
                // Font size controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Font size decrease
                    TextButton(
                        onClick = {
                            onInteraction()
                            onDecreaseFontSize()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = contentColor)
                    ) {
                        Text(text = "A−", fontSize = 14.sp)
                    }

                    // Current font size label
                    Text(
                        text = "Font: ${uiState.fontScale.displayName}",
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge
                    )

                    // Font size increase
                    TextButton(
                        onClick = {
                            onInteraction()
                            onIncreaseFontSize()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = contentColor)
                    ) {
                        Text(text = "A+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Word / char count
                Text(
                    text = "${formatCount(uiState.wordCount)} words  ·  ${formatCount(uiState.charCount)} chars",
                    modifier = Modifier.padding(bottom = 2.dp),
                    color = contentColor.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            } else {
                TextButton(
                    onClick = {
                        onInteraction()
                        showPageJumpDialog = true
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = contentColor)
                ) {
                    Text(
                        text = "${(uiState.currentPage + 1).coerceAtMost(uiState.totalPages.coerceAtLeast(1))} / ${uiState.totalPages.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Slider(
                    value = sliderPage.coerceIn(0f, sliderMax.toFloat()),
                    onValueChange = { value ->
                        sliderPage = value
                        onInteraction()
                    },
                    onValueChangeFinished = {
                        val page = sliderPage.roundToInt().coerceIn(0, maxPage)
                        onGoToPage(page)
                    },
                    valueRange = 0f..sliderMax.toFloat(),
                    enabled = uiState.totalPages > 1,
                    colors = SliderDefaults.colors(
                        thumbColor = LuminarGold,
                        activeTrackColor = LuminarGold,
                        inactiveTrackColor = contentColor.copy(alpha = 0.22f)
                    )
                )
            }
        }
    }
}

@Composable
private fun PageJumpDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val safeTotalPages = totalPages.coerceAtLeast(1)
    var pageText by remember(currentPage, totalPages) {
        mutableStateOf((currentPage + 1).coerceIn(1, safeTotalPages).toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Go to page") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { value ->
                        pageText = value.filter { it.isDigit() }.take(6)
                    },
                    label = { Text(text = "Page number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )

                Text(
                    text = "Enter a page from 1 to $safeTotalPages",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val requestedPage = pageText.toIntOrNull()
                        ?.coerceIn(1, safeTotalPages)
                        ?: (currentPage + 1).coerceIn(1, safeTotalPages)

                    onConfirm(requestedPage - 1)
                }
            ) {
                Text(text = "Go", color = LuminarGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
private fun ReaderMessage(
    message: String,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNavigateBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = LuminarGold
            )
        ) {
            Text(text = "Back")
        }
    }
}

@Composable
private fun SearchBar(
    uiState: ReaderUiState,
    onQueryChanged: (String) -> Unit,
    onNextMatch: () -> Unit,
    onPreviousMatch: () -> Unit,
    onClose: () -> Unit
) {
    val containerColor = uiState.currentTheme.readerControlsContainerColor()
    val contentColor = uiState.currentTheme.readerControlsContentColor()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search text field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        contentColor.copy(alpha = 0.08f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (uiState.searchQuery.isEmpty()) {
                    Text(
                        text = "Search in document…",
                        color = contentColor.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = uiState.searchQuery,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = contentColor,
                        fontSize = 14.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(LuminarGold),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNextMatch() })
                )
            }

            // Match count
            if (uiState.matchLabel.isNotEmpty()) {
                Text(
                    text = uiState.matchLabel,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = if (uiState.matchCount == 0) contentColor.copy(alpha = 0.4f)
                            else LuminarGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Previous match
            IconButton(
                onClick = onPreviousMatch,
                enabled = uiState.matchCount > 0
            ) {
                Text(
                    text = "▲",
                    color = if (uiState.matchCount > 0) contentColor else contentColor.copy(alpha = 0.25f),
                    fontSize = 14.sp
                )
            }

            // Next match
            IconButton(
                onClick = onNextMatch,
                enabled = uiState.matchCount > 0
            ) {
                Text(
                    text = "▼",
                    color = if (uiState.matchCount > 0) contentColor else contentColor.copy(alpha = 0.25f),
                    fontSize = 14.sp
                )
            }

            // Close search
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_24),
                    contentDescription = "Close search",
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }
        }
    }
}

@Composable
private fun TtsControlBar(
    ttsState: TtsState,
    theme: AppTheme,
    onToggle: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onStop: () -> Unit
) {
    val containerColor = theme.readerControlsContainerColor()
    val contentColor = theme.readerControlsContentColor()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = 80.dp),
        color = containerColor,
        contentColor = contentColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Stop
            IconButton(onClick = onStop) {
                Text(text = "⏹", fontSize = 18.sp)
            }

            // Skip back
            IconButton(onClick = onSkipBack) {
                Text(text = "⏮", fontSize = 18.sp)
            }

            // Play/Pause
            IconButton(onClick = onToggle) {
                Text(
                    text = if (ttsState.isSpeaking) "⏸" else "▶",
                    fontSize = 22.sp
                )
            }

            // Skip forward
            IconButton(onClick = onSkipForward) {
                Text(text = "⏭", fontSize = 18.sp)
            }

            // Progress
            Text(
                text = if (ttsState.totalChunks > 0)
                    "${ttsState.currentChunkIndex + 1}/${ttsState.totalChunks}"
                else "",
                color = contentColor.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ReaderLoadingState(
    modifier: Modifier = Modifier,
    message: String = "Loading…"
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = LuminarGold,
            strokeWidth = 3.dp
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontSize = 13.sp
        )
    }
}

private fun PDFView.isCenterTap(event: MotionEvent): Boolean {
    if (width == 0 || height == 0) return true

    val minX = width * 0.2f
    val maxX = width * 0.8f
    val minY = height * 0.2f
    val maxY = height * 0.8f

    return event.x in minX..maxX && event.y in minY..maxY
}

private fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun AppTheme.shortLabel(): String {
    return when (this) {
        AppTheme.DARK_AMOLED -> "AMOLED"
        AppTheme.SEPIA -> "Sepia"
        AppTheme.LIGHT -> "Light"
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
