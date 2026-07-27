package dev.recto.reader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import dev.recto.reader.data.db.BookDao
import dev.recto.reader.data.db.BookEntity
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

sealed interface ImportResult {
    data class Added(val id: Long, val title: String) : ImportResult
    data class Duplicate(val id: Long, val title: String) : ImportResult
    data class Unsupported(val name: String) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

/**
 * How a book arrived, which decides whether we can reference it or must copy.
 */
enum class ImportSource {
    /** System file picker (ACTION_OPEN_DOCUMENT). Persistable, no copy needed. */
    PICKER,

    /** Share sheet or "Open with". Transient permission - must copy now. */
    EXTERNAL
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

    /**
     * Opens a book's bytes, from wherever they actually live.
     * Callers must not care which storage strategy was used.
     */
    fun openBook(book: BookEntity): InputStream {
        book.localPath?.let { path ->
            val file = File(path)
            if (file.exists()) return file.inputStream()
        }
        return context.contentResolver.openInputStream(Uri.parse(book.sourceUri))
            ?: error("Cannot open this file. It may have been moved or deleted.")
    }

    suspend fun delete(book: BookEntity) = withContext(Dispatchers.IO) {
        book.coverPath?.let { runCatching { File(it).delete() } }
        book.localPath?.let { runCatching { File(it).delete() } }
        if (book.localPath == null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(book.sourceUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        dao.delete(book)
    }

    /**
     * Imports a document.
     *
     * The [source] distinction is the whole point:
     *
     * A picker URI can be held forever via takePersistableUriPermission, so we
     * reference the file in place - a 300 MB book copied on import is 300 MB
     * of the user's phone gone for nothing.
     *
     * A share-sheet or view URI cannot. Those grants are scoped to the
     * receiving activity's lifetime, and takePersistableUriPermission throws
     * SecurityException on them. WhatsApp's provider in particular is not
     * even exported for later reads. So for those we copy the bytes into app
     * storage immediately, while we still have permission. Skipping that copy
     * would produce a library entry that opens fine once and then fails
     * forever - the worst possible failure mode.
     */
    suspend fun import(
        uri: Uri,
        source: ImportSource = ImportSource.PICKER
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            var persisted = false
            if (source == ImportSource.PICKER) {
                persisted = runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }.isSuccess
            }

            val (displayName, sizeBytes) = queryDocument(uri)
            val mime = context.contentResolver.getType(uri)
            val format = BookFormat.detect(displayName, mime)

            if (format == BookFormat.UNKNOWN) {
                return@withContext ImportResult.Unsupported(displayName ?: "this file")
            }

            // Exact-URI duplicate check. Picker URIs are stable, so this
            // catches re-picking the same file.
            dao.bySourceUri(uri.toString())?.let {
                return@withContext ImportResult.Duplicate(it.id, it.title)
            }

            // Copy when we cannot hold a durable permission.
            val mustCopy = !persisted
            var localPath: String? = null
            var contentHash: String? = null

            if (mustCopy) {
                val copied = copyIntoStorage(uri, displayName)
                    ?: return@withContext ImportResult.Failed(
                        "Could not read that file. Try saving it to your phone first."
                    )
                localPath = copied.absolutePath
                contentHash = hashOf(copied)

                // Share-sheet URIs are ephemeral, so the URI itself is useless
                // for dedupe - the same book shared twice looks like two
                // different files. Compare content instead.
                val existing = contentHash?.let { dao.byContentHash(it) }
                if (existing != null) {
                    runCatching { copied.delete() }
                    return@withContext ImportResult.Duplicate(existing.id, existing.title)
                }
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
                    localPath?.let { File(it).inputStream() }
                        ?: context.contentResolver.openInputStream(uri)
                        ?: error("Cannot open file")
                }
                meta.title?.let { title = it }
                author = meta.author
                coverPath = meta.coverBytes?.let { saveCover(it) }
            }

            val id = dao.insert(
                BookEntity(
                    title = title,
                    author = author,
                    // For copied books the "source" is our own file, so the
                    // unique index stays meaningful and never collides with a
                    // recycled provider URI.
                    sourceUri = localPath?.let { "file://$it" } ?: uri.toString(),
                    format = format.name,
                    coverPath = coverPath,
                    sizeBytes = if (sizeBytes > 0) sizeBytes else (localPath?.let { File(it).length() } ?: 0),
                    localPath = localPath,
                    contentHash = contentHash
                )
            )

            if (id == -1L) {
                localPath?.let { runCatching { File(it).delete() } }
                ImportResult.Duplicate(0, title)
            } else {
                ImportResult.Added(id, title)
            }
        } catch (e: Exception) {
            ImportResult.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    /**
     * Streams the document into app-private storage. Streamed, not read into
     * a ByteArray: a 200 MB book would otherwise be 200 MB of heap.
     */
    private fun copyIntoStorage(uri: Uri, displayName: String?): File? = runCatching {
        val dir = File(context.filesDir, "books").apply { mkdirs() }
        val safeName = (displayName ?: "book")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .takeLast(80)
        val target = File(dir, "${System.currentTimeMillis()}_$safeName")

        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        } ?: return null

        if (target.length() == 0L) {
            target.delete()
            return null
        }
        target
    }.getOrNull()

    /**
     * SHA-256 of the first 1 MB plus the file length. Full-file hashing on a
     * large book is slow for no benefit; this is more than enough to spot the
     * same file arriving twice.
     */
    private fun hashOf(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (total < 1024 * 1024) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                total += read
            }
        }
        digest.update(file.length().toString().toByteArray())
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

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
