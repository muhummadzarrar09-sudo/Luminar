package dev.recto.reader.ui.pdf

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.recto.reader.notifications.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The PDF reader.
 *
 * Pages are rendered as images, exactly as Kindle does. A PDF is a fixed
 * layout - columns, tables, figures and equations are positioned absolutely -
 * so extracting the text and reflowing it would scramble any document more
 * complex than a plain letter. Two-column papers come out interleaved,
 * tables collapse into word salad, and equations vanish. Rendering the page
 * is what makes a PDF actually readable.
 *
 * The trade-off, and it is the same one Kindle makes: no font size, no
 * themes, no reflow. You zoom instead.
 */
@Composable
fun PdfScreen(
    bookId: Long,
    onBack: () -> Unit,
    vm: PdfViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pageIndex by vm.pageIndex.collectAsStateWithLifecycle()
    val chromeVisible by vm.chromeVisible.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext

    LaunchedEffect(bookId) { vm.load(bookId) }

    BackHandler {
        vm.persistNow()
        vm.endSession { m, s -> Notifications.goalReached(appContext, m, s) }
        onBack()
    }

    DisposableEffect(bookId) {
        onDispose {
            vm.persistNow()
            vm.endSession { m, s -> Notifications.goalReached(appContext, m, s) }
        }
    }

    // A PDF page is white paper. Framing it in a dark surround is easier on
    // the eyes than a white page floating on a white background, and it makes
    // the page edges legible.
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1A1A1A)) {
        when (val s = state) {
            is PdfState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            is PdfState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(36.dp)
                ) {
                    Text(
                        text = "Cannot open this PDF",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    TextButton(onClick = onBack) { Text("Back to library") }
                }
            }

            is PdfState.Ready -> PdfPager(
                state = s,
                pageIndex = pageIndex,
                chromeVisible = chromeVisible,
                keepScreenOn = settings.keepScreenOn,
                warmth = settings.warmth,
                dim = settings.dim,
                onNext = vm::next,
                onPrevious = vm::previous,
                onGoToPage = vm::goToPage,
                onToggleChrome = vm::toggleChrome,
                onBack = {
                    vm.persistNow()
                    vm.endSession { m, sk -> Notifications.goalReached(appContext, m, sk) }
                    onBack()
                }
            )
        }
    }
}

@Composable
private fun PdfPager(
    state: PdfState.Ready,
    pageIndex: Int,
    chromeVisible: Boolean,
    keepScreenOn: Boolean,
    warmth: Float,
    dim: Float,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    onBack: () -> Unit
) {
    var viewWidth by remember { mutableStateOf(0) }
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    // Zoom and pan, reset whenever the page changes - carrying a 3x zoom onto
    // the next page would drop you into a random corner of it.
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offsetX by remember(pageIndex) { mutableFloatStateOf(0f) }
    var offsetY by remember(pageIndex) { mutableFloatStateOf(0f) }

    val pageCount = state.source.pageCount

    // Render at the view width times the zoom, so zooming in re-renders
    // sharply from the PDF rather than upscaling a blurry bitmap.
    LaunchedEffect(pageIndex, viewWidth, scale) {
        if (viewWidth <= 0) return@LaunchedEffect
        val target = (viewWidth * scale.coerceAtMost(3f)).toInt()
        bitmap = withContext(Dispatchers.Default) {
            state.source.renderPage(pageIndex, target)
        }
    }

    // Prefetch the next page so a forward tap is instant. Reading is
    // overwhelmingly forwards, so only one direction is worth the memory.
    LaunchedEffect(pageIndex, viewWidth) {
        if (viewWidth <= 0 || pageIndex + 1 >= pageCount) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            state.source.renderPage(pageIndex + 1, viewWidth)
        }
    }

    dev.recto.reader.ui.reader.KeepScreenOn(enabled = keepScreenOn)

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewWidth = it.width }
            .pointerInput(pageCount) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 4f)

                    // Panning only makes sense while zoomed in; at 1x the page
                    // already fits, so a drag should not shift it off centre.
                    if (newScale > 1f) {
                        val maxX = size.width * (newScale - 1f) / 2f
                        val maxY = size.height * (newScale - 1f) / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                    scale = newScale
                }
            }
            .pointerInput(pageCount) {
                detectTapGestures(
                    onDoubleTap = { position ->
                        // Double tap toggles between fit and 2.5x, centred on
                        // where you tapped - the standard document gesture.
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                            offsetX = (size.width / 2f - position.x) * 1.5f
                            offsetY = (size.height / 2f - position.y) * 1.5f
                        }
                    },
                    onTap = { position ->
                        // While zoomed, taps pan rather than turn pages -
                        // otherwise you cannot read the right-hand column
                        // without skipping the page.
                        if (scale > 1f) {
                            onToggleChrome()
                            return@detectTapGestures
                        }
                        when {
                            position.x < size.width * 0.33f -> onPrevious()
                            position.x > size.width * 0.67f -> onNext()
                            else -> onToggleChrome()
                        }
                    }
                )
            }
    ) {
        val bmp = bitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            )
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        }

        // The same comfort overlays as the EPUB reader. Warmth on a white
        // scanned page is arguably more useful here than anywhere else.
        dev.recto.reader.ui.reader.EyeComfortOverlay(warmth = warmth, dim = dim)

        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text("Back") }
                    Text(
                        text = state.book.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    if (scale > 1f) {
                        TextButton(onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }) { Text("Fit") }
                    }
                }
            }
        }

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
                            onGoToPage(scrubValue.toInt())
                        },
                        valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat()
                    )

                    val shown = if (scrubbing) scrubValue.toInt() else pageIndex
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Page ${shown + 1} of $pageCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${((shown + 1) * 100 / pageCount).coerceIn(1, 100)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (!chromeVisible) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((pageIndex + 1).toFloat() / pageCount)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
