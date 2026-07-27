package dev.recto.reader.ui.reader

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PageHorizontalPadding = 26.dp
private val PageVerticalPadding = 20.dp

@Composable
fun ReaderScreen(
    bookId: Long,
    onBack: () -> Unit,
    vm: ReaderViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pageIndex by vm.pageIndex.collectAsStateWithLifecycle()
    val chromeVisible by vm.chromeVisible.collectAsStateWithLifecycle()

    LaunchedEffect(bookId) { vm.load(bookId) }

    BackHandler {
        vm.persistNow()
        onBack()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
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
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                vm = vm,
                onBack = {
                    vm.persistNow()
                    onBack()
                }
            )
        }
    }
}

@Composable
private fun ReaderContent(
    state: ReaderState.Ready,
    pageIndex: Int,
    chromeVisible: Boolean,
    vm: ReaderViewModel,
    onBack: () -> Unit
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val readingStyle = remember {
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            lineHeight = 30.sp,
            textAlign = TextAlign.Justify
        )
    }

    var areaWidth by remember { mutableStateOf(0) }
    var areaHeight by remember { mutableStateOf(0) }

    // Re-paginate whenever the usable area changes: first layout, rotation,
    // or (later) a font-size change. Position is preserved by character
    // offset, not page number.
    LaunchedEffect(areaWidth, areaHeight, state.content) {
        if (areaWidth <= 0 || areaHeight <= 0) return@LaunchedEffect

        vm.rememberPositionBeforeRepaginate()

        val hPad = with(density) { PageHorizontalPadding.roundToPx() } * 2
        val vPad = with(density) { PageVerticalPadding.roundToPx() } * 2

        val pages = withContext(Dispatchers.Default) {
            Paginator.paginate(
                chapters = state.content.chapters.map { it.text },
                measurer = measurer,
                style = readingStyle,
                widthPx = (areaWidth - hPad).coerceAtLeast(1),
                heightPx = (areaHeight - vPad).coerceAtLeast(1)
            )
        }
        vm.onPaginated(pages)
    }

    val pages = state.pages

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                areaWidth = it.width
                areaHeight = it.height
            }
    ) {
        if (pages == null) {
            Centered { CircularProgressIndicator() }
        } else if (pages.isEmpty()) {
            Centered {
                Text(
                    "This book appears to be empty.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            PageSurface(
                pages = pages,
                pageIndex = pageIndex,
                style = readingStyle,
                onNext = vm::next,
                onPrevious = vm::previous,
                onToggleChrome = vm::toggleChrome
            )
        }

        // Top chrome
        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text("Back") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = state.content.title ?: state.book.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 12.dp)
                    )
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
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp
                ) {
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
                trackColor = MaterialTheme.colorScheme.background
            )
        }
    }
}

@Composable
private fun PageSurface(
    pages: List<Page>,
    pageIndex: Int,
    style: TextStyle,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleChrome: () -> Unit
) {
    var dragTotal by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(pages.size) {
                detectTapGestures { offset ->
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
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = PageHorizontalPadding,
                        vertical = PageVerticalPadding
                    )
            ) {
                if (page != null) {
                    Text(
                        text = page.text,
                        style = style,
                        color = MaterialTheme.colorScheme.onBackground
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
