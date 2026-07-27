package dev.recto.reader.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A book in the library.
 *
 * Two ways a book can live on disk, and the difference matters:
 *
 *  - **Referenced** ([localPath] null). The user picked it with the system
 *    file picker, so we hold a *persistable* SAF permission on [sourceUri]
 *    and read it in place. No copy, no wasted storage.
 *
 *  - **Copied** ([localPath] set). The book arrived through a share sheet or
 *    "Open with" - WhatsApp, Gmail, Telegram, a download notification. Those
 *    URIs grant only transient permission that dies with the activity, and
 *    takePersistableUriPermission() throws on them. The only way to keep the
 *    book readable tomorrow is to copy the bytes into app storage now.
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["sourceUri"], unique = true),
        Index(value = ["contentHash"]),
        Index(value = ["lastOpenedAt"]),
        Index(value = ["addedAt"])
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,
    val author: String?,
    val sourceUri: String,
    val format: String,

    val coverPath: String? = null,
    val sizeBytes: Long = 0,

    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null,

    /** 0.0 .. 1.0 through the book. */
    val progress: Float = 0f,

    /**
     * Opaque resume position. Currently a character offset; when Readium
     * lands this becomes a locator JSON string. Stored as text so the reader
     * engine owns its own format and the database need not care.
     */
    val locator: String? = null,

    val isFinished: Boolean = false,

    /**
     * Absolute path to our own copy of the file, when we had to make one.
     * Null means read [sourceUri] directly.
     */
    val localPath: String? = null,

    /**
     * SHA-256 of the leading bytes plus file length, for copied books only.
     * Share-sheet URIs are ephemeral, so the URI cannot identify a duplicate -
     * the same book shared twice looks like two unrelated files. Content
     * identity can.
     */
    val contentHash: String? = null
)
