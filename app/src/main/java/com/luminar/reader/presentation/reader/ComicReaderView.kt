// app/src/main/java/com/luminar/reader/presentation/reader/ComicReaderView.kt
package com.luminar.reader.presentation.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminar.reader.data.model.AppTheme
import com.luminar.reader.presentation.theme.readerBackgroundColor
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicReaderView(
    comicFile: File,
    imagePaths: List<String>,
    theme: AppTheme,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onToggleControls: () -> Unit,
    getImageBytes: (File, String) -> ByteArray
) {
    val backgroundColor = theme.readerBackgroundColor()

    if (imagePaths.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No pages found in comic",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp
            )
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, imagePaths.lastIndex),
        pageCount = { imagePaths.size }
    )

    // Report page changes
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ComicPage(
                comicFile = comicFile,
                entryPath = imagePaths[page],
                onTap = onToggleControls,
                getImageBytes = getImageBytes
            )
        }

        // Page indicator (bottom-center, subtle)
        Text(
            text = "${pagerState.currentPage + 1} / ${imagePaths.size}",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
    }
}

sealed interface ComicLoadState {
    object Loading : ComicLoadState
    data class Success(val bitmap: android.graphics.Bitmap) : ComicLoadState
    object Error : ComicLoadState
}

@Composable
private fun ComicPage(
    comicFile: File,
    entryPath: String,
    onTap: () -> Unit,
    getImageBytes: (File, String) -> ByteArray
) {
    val loadState = produceState<ComicLoadState>(initialValue = ComicLoadState.Loading, comicFile.absolutePath, entryPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = getImageBytes(comicFile, entryPath)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.fold(
                onSuccess = { bmp -> if (bmp != null) ComicLoadState.Success(bmp) else ComicLoadState.Error },
                onFailure = { ComicLoadState.Error }
            )
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onTap() }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (val state = loadState.value) {
            ComicLoadState.Loading -> {
                androidx.compose.material3.CircularProgressIndicator(
                    color = com.luminar.reader.presentation.theme.LuminarGold.copy(alpha = 0.5f)
                )
            }
            is ComicLoadState.Success -> {
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = "Comic page",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentScale = ContentScale.Fit
                )
            }
            ComicLoadState.Error -> {
                Text(
                    text = "Unable to load page",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        }
    }
}
