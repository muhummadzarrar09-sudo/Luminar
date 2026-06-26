// app/src/main/java/com/luminar/reader/data/repository/BookRepositoryImpl.kt
package com.luminar.reader.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
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

import com.luminar.reader.data.epub.EpubBookLoader

@Singleton
class BookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val epubBookLoader: EpubBookLoader
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> = bookDao.getAllBooks()

    override fun getBookById(id: Long): Flow<Book?> = bookDao.getBookById(id)

    override fun getAllProgress(): Flow<List<ReadingProgress>> = bookDao.getAllProgress()

    override fun getProgress(bookId: Long): Flow<ReadingProgress?> = bookDao.getProgress(bookId)

    override suspend fun importPdf(uri: Uri): Long = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri)
        val destination = createPdfDestinationFile()

        try {
            copyUriToInternalFile(uri, destination)
            val metadata = readPdfMetadata(destination)

            val book = Book(
                title = displayName.toBookTitle(),
                filePath = destination.absolutePath,
                coverPath = metadata.coverPath,
                format = BookFormat.PDF,
                totalPages = metadata.pageCount
            )

            bookDao.insertBook(book)
        } catch (cancellation: CancellationException) {
            destination.delete()
            throw cancellation
        } catch (throwable: Throwable) {
            destination.delete()
            throw BookImportException(
                message = "Unable to import PDF: ${throwable.message ?: "Unknown error"}",
                cause = throwable
            )
        }
    }

    override suspend fun importEpub(uri: Uri): Long = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri)
        val destination = createEpubDestinationFile()

        try {
            copyUriToInternalFile(uri, destination)
            val metadata = epubBookLoader.loadMetadata(destination)

            val book = Book(
                title = metadata.title ?: displayName.toBookTitle(),
                filePath = destination.absolutePath,
                coverPath = metadata.coverPath,
                format = BookFormat.EPUB,
                totalPages = 1
            )

            bookDao.insertBook(book)
        } catch (cancellation: CancellationException) {
            destination.delete()
            throw cancellation
        } catch (throwable: Throwable) {
            destination.delete()
            throw BookImportException(
                message = "Unable to import EPUB: ${throwable.message ?: "Unknown error"}",
                cause = throwable
            )
        }
    }

    override suspend fun deleteBook(book: Book) {
        withContext(Dispatchers.IO) {
            bookDao.deleteBook(book)
            File(book.filePath).delete()
            book.coverPath?.let { File(it).delete() }
        }
    }

    override suspend fun saveProgress(
        bookId: Long,
        currentPage: Int,
        scrollOffset: Float,
        epubCfi: String?
    ) {
        bookDao.upsertProgress(
            ReadingProgress(
                bookId = bookId,
                currentPage = currentPage.coerceAtLeast(0),
                scrollOffset = scrollOffset,
                lastReadAt = System.currentTimeMillis(),
                epubCfi = epubCfi
            )
        )
    }

    override suspend fun markBookOpened(bookId: Long) {
        bookDao.updateLastOpenedAt(
            bookId = bookId,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun resolveDisplayName(uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.path?.let(::File)?.name ?: DEFAULT_FILE_NAME
        }

        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (displayNameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(displayNameIndex)
            } else {
                null
            }
        } ?: DEFAULT_FILE_NAME
    }

    private fun String.toBookTitle(): String {
        val withoutExtension = substringBeforeLast('.', this)
        return withoutExtension
            .replace(Regex("[\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Untitled PDF" }
    }

    private fun createPdfDestinationFile(): File {
        val booksDirectory = File(context.filesDir, BOOKS_DIRECTORY).apply { mkdirs() }
        return File(booksDirectory, "${UUID.randomUUID()}.pdf")
    }

    private fun createEpubDestinationFile(): File {
        val booksDirectory = File(context.filesDir, BOOKS_DIRECTORY).apply { mkdirs() }
        return File(booksDirectory, "${UUID.randomUUID()}.epub")
    }

    private fun createCoverDestinationFile(): File {
        val coversDirectory = File(context.filesDir, COVERS_DIRECTORY).apply { mkdirs() }
        return File(coversDirectory, "${UUID.randomUUID()}.png")
    }

    private fun copyUriToInternalFile(uri: Uri, destination: File) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open selected file")

        inputStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun readPdfMetadata(pdfFile: File): PdfMetadata {
        val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)

        try {
            val renderer = PdfRenderer(descriptor)

            try {
                val pageCount = renderer.pageCount
                if (pageCount <= 0) {
                    throw IOException("PDF has no readable pages")
                }

                val coverPath = runCatching { renderFirstPageCover(renderer) }.getOrNull()
                return PdfMetadata(pageCount = pageCount, coverPath = coverPath)
            } finally {
                renderer.close()
            }
        } finally {
            descriptor.close()
        }
    }

    private fun renderFirstPageCover(renderer: PdfRenderer): String {
        val page = renderer.openPage(0)

        try {
            val safePageWidth = page.width.coerceAtLeast(1)
            val safePageHeight = page.height.coerceAtLeast(1)
            val targetWidth = 600
            val targetHeight = (targetWidth * (safePageHeight.toFloat() / safePageWidth.toFloat()))
                .roundToInt()
                .coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

            try {
                Canvas(bitmap).drawColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val coverFile = createCoverDestinationFile()
                coverFile.outputStream().use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw IOException("Unable to save cover thumbnail")
                    }
                }

                return coverFile.absolutePath
            } finally {
                bitmap.recycle()
            }
        } finally {
            page.close()
        }
    }

    private data class PdfMetadata(
        val pageCount: Int,
        val coverPath: String?
    )

    private companion object {
        const val BOOKS_DIRECTORY = "books"
        const val COVERS_DIRECTORY = "covers"
        const val DEFAULT_FILE_NAME = "Untitled.pdf"
    }
}

class BookImportException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
