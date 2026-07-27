package dev.recto.reader.ui.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A book cover, or a generated typographic one when the file has no artwork.
 *
 * Decoding happens off the main thread and is downsampled to roughly the cell
 * size - full-resolution cover bitmaps in a scrolling grid is a reliable way
 * to run out of memory.
 */
@Composable
fun BookCover(
    coverPath: String?,
    title: String,
    author: String?,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(coverPath) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(coverPath) { mutableStateOf(false) }

    LaunchedEffect(coverPath) {
        if (coverPath == null) {
            failed = true
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) { decodeSampled(coverPath) }
        if (decoded == null) failed = true else bitmap = decoded.asImageBitmap()
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (failed) {
            GeneratedCover(title = title, author = author)
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

/**
 * When there is no cover art, make something that still looks deliberate:
 * a colour derived from the title so the same book is always the same colour,
 * with the title set in the reading serif.
 */
@Composable
private fun GeneratedCover(title: String, author: String?) {
    val hue = (title.hashCode().toFloat().mod(360f))
    val base = Color.hsl(hue, 0.32f, 0.34f)
    val top = Color.hsl(hue, 0.30f, 0.42f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, base)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            if (!author.isNullOrBlank()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun decodeSampled(path: String, targetWidth: Int = 400): android.graphics.Bitmap? {
    return runCatching {
        val file = File(path)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)

        var sample = 1
        var w = bounds.outWidth
        while (w > targetWidth * 2) {
            sample *= 2
            w /= 2
        }

        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }.getOrNull()
}
