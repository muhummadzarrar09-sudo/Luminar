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

            // 1. Same URI: catches re-picking the same file from the picker.
            dao.bySourceUri(uri.toString())?.let {
                return@withContext ImportResult.Duplicate(it.id, it.title)
            }

            // 2. Same bytes. Hash BEFORE deciding to copy, and hash every
            //    import regardless of source.
            //
            //    This is the bug that let the same book in twice: the hash
            //    used to be computed only for copied files, so a book added
            //    from the picker had no hash at all, and the WhatsApp copy of
            //    it had nothing to match against.
            val contentHash = hashOf { context.contentResolver.openInputStream(uri) }

            contentHash?.let { hash ->
                dao.byContentHash(hash)?.let {
                    return@withContext ImportResult.Duplicate(it.id, it.title)
                }
            }

            // Read metadata now - we need it for the title anyway, and for the
            // third duplicate check below.
            val fallbackTitle = (displayName ?: "Untitled")
                .substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .ifBlank { "Untitled" }

            var title = fallbackTitle
            var author: String? = null
            var coverBytes: ByteArray? = null

            if (format == BookFormat.EPUB) {
                val meta = EpubMetadataReader.read {
                    context.contentResolver.openInputStream(uri)
                        ?: error("Cannot open file")
                }
                meta.title?.let { title = it }
                author = meta.author
                coverBytes = meta.coverBytes
            }

            // 3. Same title and author. Catches genuinely different files of
            //    the same book - a re-download, a differently compressed EPUB,
            //    or the same text from another source. Only trusted when the
            //    metadata came from inside the EPUB, because filename-derived
            //    titles are far too coarse to match on.
            if (format == BookFormat.EPUB && author != null) {
                dao.byTitleAuthor(normaliseForMatch(title), normaliseForMatch(author))
                    ?.let { return@withContext ImportResult.Duplicate(it.id, it.title) }
            }

            // Only now, once we know it is genuinely new, spend the disk.
            var localPath: String? = null
            if (!persisted) {
                val copied = copyIntoStorage(uri, displayName)
                    ?: return@withContext ImportResult.Failed(
                        "Could not read that file. Try saving it to your phone first."
                    )
                localPath = copied.absolutePath
            }

            val coverPath = coverBytes?.let { saveCover(it) }

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
                    sizeBytes = if (sizeBytes > 0) {
                        sizeBytes
                    } else {
                        localPath?.let { File(it).length() } ?: 0
                    },
                    localPath = localPath,
                    contentHash = contentHash,
                    matchTitle = normaliseForMatch(title),
                    matchAuthor = author?.let { normaliseForMatch(it) }
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
     * Lower-cased, punctuation-stripped form used for title/author matching.
     * "The Hobbit" and "the hobbit!" should not be two books.
     */
    private fun normaliseForMatch(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

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
     * SHA-256 of the first 1 MB of content plus the total byte count.
     *
     * Works from any stream, so it can be computed before we decide whether
     * to copy the file. Hashing a whole 200 MB book would be slow for no real
     * gain; the leading megabyte plus the exact length is more than enough to
     * recognise the same file arriving twice.
     */
    private fun hashOf(open: () -> java.io.InputStream?): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        open()?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                if (total < 1024 * 1024) {
                    val take = minOf(read.toLong(), 1024 * 1024 - total).toInt()
                    digest.update(buffer, 0, take)
                }
                total += read
            }
        } ?: return null
        if (total == 0L) return null
        digest.update(total.toString().toByteArray())
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
