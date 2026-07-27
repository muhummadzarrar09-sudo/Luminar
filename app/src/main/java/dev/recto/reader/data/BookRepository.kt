package dev.recto.reader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import dev.recto.reader.data.db.BookDao
import dev.recto.reader.data.db.BookEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

sealed interface ImportResult {
    data class Added(val id: Long, val title: String) : ImportResult
    data class Duplicate(val title: String) : ImportResult
    data class Unsupported(val name: String) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

class BookRepository(
    private val context: Context,
    private val dao: BookDao
) {

    fun observeBooks(): Flow<List<BookEntity>> = dao.observeAll()

    fun observeCurrent(): Flow<BookEntity?> = dao.observeCurrent()

    suspend fun byId(id: Long): BookEntity? = dao.byId(id)

    suspend fun touch(id: Long) = dao.touch(id)

    suspend fun saveProgress(id: Long, progress: Float, locator: String?) =
        dao.saveProgress(id, progress.coerceIn(0f, 1f), locator)

    suspend fun setFinished(id: Long, finished: Boolean) = dao.setFinished(id, finished)

    suspend fun delete(book: BookEntity) = withContext(Dispatchers.IO) {
        book.coverPath?.let { runCatching { File(it).delete() } }
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(book.sourceUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        dao.delete(book)
    }

    /**
     * Imports a picked document.
     *
     * We do NOT copy the book into app storage. A 300 MB PDF copied on import
     * is 300 MB of the user's phone gone for no reason. Instead we take a
     * persistable read permission on the SAF URI, which survives reboots, and
     * only extract the cover.
     */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            dao.bySourceUri(uri.toString())?.let {
                return@withContext ImportResult.Duplicate(it.title)
            }

            val (displayName, sizeBytes) = queryDocument(uri)
            val mime = context.contentResolver.getType(uri)
            val format = BookFormat.detect(displayName, mime)

            if (format == BookFormat.UNKNOWN) {
                return@withContext ImportResult.Unsupported(displayName ?: "this file")
            }

            val fallbackTitle = (displayName ?: "Untitled")
                .substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .ifBlank { "Untitled" }

            var title = fallbackTitle
            var author: String? = null
            var coverPath: String? = null

            if (format == BookFormat.EPUB) {
                val meta = EpubMetadataReader.read {
                    context.contentResolver.openInputStream(uri)
                        ?: error("Cannot open ${uri.lastPathSegment}")
                }
                meta.title?.let { title = it }
                author = meta.author
                coverPath = meta.coverBytes?.let { saveCover(it) }
            }

            val id = dao.insert(
                BookEntity(
                    title = title,
                    author = author,
                    sourceUri = uri.toString(),
                    format = format.name,
                    coverPath = coverPath,
                    sizeBytes = sizeBytes
                )
            )

            if (id == -1L) {
                ImportResult.Duplicate(title)
            } else {
                ImportResult.Added(id, title)
            }
        } catch (e: Exception) {
            ImportResult.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    private fun queryDocument(uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !c.isNull(nameIdx)) name = c.getString(nameIdx)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                }
            }
        }
        if (name == null) name = uri.lastPathSegment
        return name to size
    }

    private fun saveCover(bytes: ByteArray): String? = runCatching {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "cover_${System.currentTimeMillis()}_${bytes.size}.img")
        file.writeBytes(bytes)
        file.absolutePath
    }.getOrNull()
}
