package dev.recto.reader.data.pdf

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Renders PDF pages to bitmaps using the platform's PdfRenderer.
 *
 * Why the platform renderer and not androidx.pdf: as of July 2026 that
 * library is still 1.0.0-alpha19 - nineteen alphas, no beta - and it ships a
 * Fragment-based viewer that fights a Compose app. PdfRenderer has been in
 * the framework since API 21, is backed by the same PDFium engine, and costs
 * zero download. If androidx.pdf reaches stable it can slot in behind this
 * class without the reader noticing.
 *
 * Two things make PdfRenderer genuinely dangerous to use naively:
 *
 *  1. It is NOT thread-safe, and only one Page may be open at a time. Opening
 *     a second page before closing the first throws. Every access here goes
 *     through a Mutex.
 *
 *  2. Page bitmaps are big. A full-screen page at 1080x1527 in ARGB_8888 is
 *     6.6 MB. Cache a few of those without thinking and a mid-range phone
 *     starts killing the app. The cache below is sized against the device's
 *     actual memory class.
 */
class PdfSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    memoryClassMb: Int
) : Closeable {

    /** Serialises every renderer call - PdfRenderer allows exactly one open page. */
    private val lock = Mutex()

    @Volatile
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /**
     * Bitmap cache sized to roughly an eighth of the app's heap, clamped to
     * something sane. On a 128 MB-class device that is 16 MB - about two
     * full-screen pages, enough for the current page plus one prefetch.
     */
    private val cache: LruCache<String, Bitmap> =
        object : LruCache<String, Bitmap>(
            (memoryClassMb * 1024 / 8).coerceIn(8 * 1024, 48 * 1024)
        ) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024

            override fun entryRemoved(
                evicted: Boolean,
                key: String,
                oldValue: Bitmap,
                newValue: Bitmap?
            ) {
                // Do NOT recycle here. A bitmap evicted from the cache may
                // still be on screen in a composition that has not recomposed
                // yet, and drawing a recycled bitmap crashes the process.
                // Letting the GC take it is slower but correct.
            }
        }

    /** Aspect ratio (height / width) of a page, for laying out before render. */
    suspend fun pageAspect(index: Int): Float = withContext(Dispatchers.IO) {
        if (closed) return@withContext DEFAULT_ASPECT
        lock.withLock {
            if (closed) return@withLock DEFAULT_ASPECT
            runCatching {
                renderer.openPage(index).use { page ->
                    if (page.width <= 0) DEFAULT_ASPECT
                    else page.height.toFloat() / page.width.toFloat()
                }
            }.getOrDefault(DEFAULT_ASPECT)
        }
    }

    /**
     * Renders one page at [targetWidth] pixels wide.
     *
     * The width is clamped: rendering a page at 4x zoom on a tablet could ask
     * for an 8000px bitmap, which is 250 MB and an instant OOM.
     */
    suspend fun renderPage(index: Int, targetWidth: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            if (closed || index !in 0 until pageCount) return@withContext null

            val width = targetWidth.coerceIn(MIN_RENDER_WIDTH, MAX_RENDER_WIDTH)
            val key = "$index@$width"

            cache.get(key)?.let { if (!it.isRecycled) return@withContext it }

            lock.withLock {
                if (closed) return@withLock null
                cache.get(key)?.let { if (!it.isRecycled) return@withLock it }

                runCatching {
                    renderer.openPage(index).use { page ->
                        val height =
                            (width * page.height.toFloat() / page.width.toFloat()).toInt()
                                .coerceIn(1, MAX_RENDER_HEIGHT)

                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        // PDF pages assume white paper. Without this, any
                        // transparent region renders as black, which looks
                        // like a corrupt file.
                        bitmap.eraseColor(Color.WHITE)

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        cache.put(key, bitmap)
                        bitmap
                    }
                }.getOrNull()
            }
        }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        cache.evictAll()
    }

    companion object {
        private const val DEFAULT_ASPECT = 1.414f // A4 portrait
        private const val MIN_RENDER_WIDTH = 320
        private const val MAX_RENDER_WIDTH = 2600
        private const val MAX_RENDER_HEIGHT = 6000

        /**
         * Opens a PDF. Returns a failure rather than throwing, because the
         * common cases - a password-protected file, a moved file, something
         * that is not really a PDF - all need to reach the user as a message
         * rather than a crash.
         */
        fun open(context: Context, descriptor: ParcelFileDescriptor): Result<PdfSource> =
            runCatching {
                val renderer = PdfRenderer(descriptor)
                val memoryClass = (
                    context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    )?.memoryClass ?: 64
                PdfSource(descriptor, renderer, memoryClass)
            }.recoverCatching { error ->
                runCatching { descriptor.close() }
                throw when (error) {
                    is SecurityException ->
                        IllegalStateException("This PDF is password protected.")
                    else ->
                        IllegalStateException(
                            "This PDF could not be opened. It may be damaged."
                        )
                }
            }
    }
}
