// app/src/main/java/com/luminar/reader/data/repository/BookRepositoryImpl.kt
package com.luminar.reader.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.luminar.reader.data.document.DocumentParser
import com.luminar.reader.data.epub.EpubChapter
import com.luminar.reader.data.epub.EpubParser
import com.luminar.reader.data.local.db.BookDao
import com.luminar.reader.data.model.Book
import com.luminar.reader.data.model.BookFormat
import com.luminar.reader.data.model.ReadingProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor

@Singleton
class BookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val epubParser: EpubParser,
    private val documentParser: DocumentParser
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> = bookDao.getAllBooks()
    override fun getBookById(id: Long): Flow<Book?> = bookDao.getBookById(id)
    override fun getAllProgress(): Flow<List<ReadingProgress>> = bookDao.getAllProgress()
    override fun getProgress(bookId: Long): Flow<ReadingProgress?> = bookDao.getProgress(bookId)
    override suspend fun importPdf(uri: Uri): Long = importFile(uri)

    override suspend fun importFile(uri: Uri): Long = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri)
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val format = BookFormat.fromExtension(extension)
        val destination = createDestinationFile(extension.ifBlank { "txt" })

        try {
            copyUriToInternalFile(uri, destination)

            when {
                format == BookFormat.PDF -> {
                    val metadata = readPdfMetadata(destination)
                    bookDao.insertBook(Book(
                        title = displayName.toBookTitle(),
                        filePath = destination.absolutePath,
                        coverPath = metadata.coverPath,
                        format = BookFormat.PDF,
                        totalPages = metadata.pageCount
                    ))
                }

                format == BookFormat.EPUB -> {
                    val coversDir = File(context.filesDir, COVERS_DIRECTORY)
                    val epub = epubParser.parse(destination, coversDir)
                    bookDao.insertBook(Book(
                        title = epub.title ?: displayName.toBookTitle(),
                        filePath = destination.absolutePath,
                        coverPath = epub.coverPath,
                        format = BookFormat.EPUB,
                        totalPages = epub.chapters.size.coerceAtLeast(1)
                    ))
                }

                format == BookFormat.CBZ -> {
                    val pageCount = documentParser.getCbzImagePaths(destination).size
                    bookDao.insertBook(Book(
                        title = displayName.toBookTitle(),
                        filePath = destination.absolutePath,
                        coverPath = null,
                        format = format,
                        totalPages = pageCount.coerceAtLeast(1)
                    ))
                }

                format.isDocumentFormat -> {
                    val text = documentParser.parseToText(destination, extension)
                    val lineCount = text.lines().count { it.isNotBlank() }
                    bookDao.insertBook(Book(
                        title = displayName.toBookTitle(),
                        filePath = destination.absolutePath,
                        coverPath = null,
                        format = format,
                        totalPages = lineCount.coerceAtLeast(1)
                    ))
                }

                else -> {
                    val lineCount = countLines(destination)
                    bookDao.insertBook(Book(
                        title = displayName.toBookTitle(),
                        filePath = destination.absolutePath,
                        coverPath = null,
                        format = format,
                        totalPages = lineCount.coerceAtLeast(1)
                    ))
                }
            }
        } catch (cancellation: CancellationException) {
            destination.delete()
            throw cancellation
        } catch (throwable: Throwable) {
            destination.delete()
            throw BookImportException(
                message = "Unable to import file: ${throwable.message ?: "Unknown error"}",
                cause = throwable
            )
        }
    }

    override suspend fun readTextContent(book: Book): String = withContext(Dispatchers.IO) {
        val file = File(book.filePath)
        if (!file.exists()) throw IOException("File not found: ${book.filePath}")
        file.readText(Charsets.UTF_8)
    }

    override suspend fun readDocumentContent(book: Book): String = withContext(Dispatchers.IO) {
        val file = File(book.filePath)
        if (!file.exists()) throw IOException("File not found: ${book.filePath}")
        val extension = file.name.substringAfterLast('.', "").lowercase()
        documentParser.parseToText(file, extension)
    }

    override suspend fun getComicImagePaths(book: Book): List<String> = withContext(Dispatchers.IO) {
        val file = File(book.filePath)
        if (!file.exists()) throw IOException("File not found: ${book.filePath}")
        documentParser.getCbzImagePaths(file)
    }

    override fun getComicImageBytes(book: Book, entryPath: String): ByteArray {
        val file = File(book.filePath)
        if (!file.exists()) throw IOException("File not found: ${book.filePath}")
        return documentParser.getCbzImageBytes(file, entryPath)
    }

    override suspend fun readEpubChapters(book: Book): List<EpubChapter> = withContext(Dispatchers.IO) {
        val file = File(book.filePath)
        if (!file.exists()) throw IOException("File not found: ${book.filePath}")
        val coversDir = File(context.filesDir, COVERS_DIRECTORY)
        epubParser.parse(file, coversDir).chapters
    }

    override suspend fun deleteBook(book: Book) {
        withContext(Dispatchers.IO) {
            bookDao.deleteBook(book)
            File(book.filePath).delete()
            book.coverPath?.let { File(it).delete() }
        }
    }

    override suspend fun saveProgress(bookId: Long, currentPage: Int, scrollOffset: Float) {
        bookDao.upsertProgress(ReadingProgress(
            bookId = bookId,
            currentPage = currentPage.coerceAtLeast(0),
            scrollOffset = scrollOffset,
            lastReadAt = System.currentTimeMillis()
        ))
    }

    override suspend fun markBookOpened(bookId: Long) {
        bookDao.updateLastOpenedAt(bookId = bookId, timestamp = System.currentTimeMillis())
    }

    // ─── Private helpers ─────────────────────────────────────

    private fun resolveDisplayName(uri: Uri): String {
        if (uri.scheme == "file") return uri.path?.let(::File)?.name ?: DEFAULT_FILE_NAME
        return context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: DEFAULT_FILE_NAME
    }

    private fun String.toBookTitle(): String {
        return substringBeforeLast('.', this)
            .replace(Regex("[\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Untitled" }
    }

    private fun createDestinationFile(extension: String): File {
        File(context.filesDir, BOOKS_DIRECTORY).mkdirs()
        return File(File(context.filesDir, BOOKS_DIRECTORY), "${UUID.randomUUID()}.$extension")
    }

    private fun createCoverDestinationFile(): File {
        File(context.filesDir, COVERS_DIRECTORY).mkdirs()
        return File(File(context.filesDir, COVERS_DIRECTORY), "${UUID.randomUUID()}.png")
    }

    private fun copyUriToInternalFile(uri: Uri, destination: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open selected file")

        // Guard against extremely large files (500MB limit)
        var totalBytes = 0L
        val maxSize = 500L * 1024 * 1024

        input.use { i ->
            destination.outputStream().use { o ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (i.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    if (totalBytes > maxSize) {
                        destination.delete()
                        throw IOException("File too large (>500 MB). Please use a smaller file.")
                    }
                    o.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    private fun countLines(file: File): Int = file.useLines { it.count() }

    private fun readPdfMetadata(pdfFile: File): PdfMetadata {
        val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(fd)
            try {
                val count = renderer.pageCount
                if (count <= 0) throw IOException("PDF has no readable pages")
                val cover = runCatching { renderFirstPageCover(renderer) }.getOrNull()
                return PdfMetadata(count, cover)
            } finally { renderer.close() }
        } finally { fd.close() }
    }

    private fun renderFirstPageCover(renderer: PdfRenderer): String {
        val page = renderer.openPage(0)
        try {
            val tw = 600
            val th = (tw * (page.height.coerceAtLeast(1).toFloat() / page.width.coerceAtLeast(1).toFloat())).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            try {
                Canvas(bmp).drawColor(AndroidColor.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val f = createCoverDestinationFile()
                f.outputStream().use { if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Cover save failed") }
                return f.absolutePath
            } finally { bmp.recycle() }
        } finally { page.close() }
    }

    private data class PdfMetadata(val pageCount: Int, val coverPath: String?)

    private companion object {
        const val BOOKS_DIRECTORY = "books"
        const val COVERS_DIRECTORY = "covers"
        const val DEFAULT_FILE_NAME = "Untitled.txt"
    }
}

class BookImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
