package dev.recto.reader.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A book in the library.
 *
 * [sourceUri] is the SAF document URI the user picked. We take a persistable
 * read permission on it so it survives reboots. [coverPath] points at a JPEG
 * we extracted into app-private storage, because we cannot rely on the source
 * file staying reachable.
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["sourceUri"], unique = true),
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
     * Opaque resume position. For EPUB this will be a Readium locator JSON
     * string; for PDF, a page index. Stored as text so the reader engine owns
     * its own format and the database does not need to care.
     */
    val locator: String? = null,

    val isFinished: Boolean = false
)
