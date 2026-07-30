package dev.recto.reader.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A highlight, a note, or a bookmark.
 *
 * All three are the same shape, so they share a table and differ by [kind]:
 *  - HIGHLIGHT: a coloured span of text
 *  - NOTE:      a highlight with something written against it
 *  - BOOKMARK:  a saved position, no selection
 *
 * Positions are stored as absolute character offsets into the book's
 * concatenated chapter text, exactly like reading positions. That is the whole
 * reason this works: page numbers change when you resize the text, character
 * offsets do not. A highlight made at 18sp is still on the right words at
 * 30sp, in landscape, after a re-pagination.
 */
@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId"), Index(value = ["bookId", "startChar"])]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val bookId: Long,

    /** HIGHLIGHT, NOTE or BOOKMARK. Stored as a string so adding a kind later
     *  does not need a migration. */
    val kind: String,

    /** Absolute character offset into the whole book. */
    val startChar: Int,

    /** Exclusive end. Equal to startChar for a bookmark. */
    val endChar: Int,

    /** The selected words, kept so the notebook can show them without
     *  re-opening and re-parsing the EPUB. */
    val selectedText: String,

    val note: String? = null,

    /** Index into HighlightColour.entries. */
    val colour: Int = 0,

    /** Chapter this landed in, for grouping in the notebook. */
    val chapterIndex: Int = 0,
    val chapterTitle: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class AnnotationKind { HIGHLIGHT, NOTE, BOOKMARK }

@Dao
interface AnnotationDao {

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY startChar ASC")
    fun observeForBook(bookId: Long): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AnnotationEntity>>

    @Query("SELECT COUNT(*) FROM annotations WHERE bookId = :bookId")
    fun observeCountForBook(bookId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: AnnotationEntity): Long

    @Query("UPDATE annotations SET note = :note, kind = :kind, updatedAt = :at WHERE id = :id")
    suspend fun updateNote(
        id: Long,
        note: String?,
        kind: String,
        at: Long = System.currentTimeMillis()
    )

    @Query("UPDATE annotations SET colour = :colour, updatedAt = :at WHERE id = :id")
    suspend fun updateColour(id: Long, colour: Int, at: Long = System.currentTimeMillis())

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * One annotation by id.
     *
     * Needed by undo: the row has to be read BEFORE it is deleted, because
     * afterwards there is nothing left to look up and the snackbar has to be
     * able to put it back exactly as it was.
     */
    @Query("SELECT * FROM annotations WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): AnnotationEntity?

    /** Snapshot of everything, for backup. */
    @Query("SELECT * FROM annotations ORDER BY bookId ASC, startChar ASC")
    suspend fun allOnce(): List<AnnotationEntity>

    /**
     * One book's annotations, once. Restore reads these to skip duplicates,
     * and doing it per book rather than per annotation is the difference
     * between one query and five hundred.
     */
    @Query("SELECT * FROM annotations WHERE bookId = :bookId")
    suspend fun forBookOnce(bookId: Long): List<AnnotationEntity>

    /**
     * Anything overlapping the given range. Used to spot an existing highlight
     * under a fresh selection, so re-selecting the same words edits rather
     * than stacks a second highlight on top.
     */
    @Query(
        """
        SELECT * FROM annotations
        WHERE bookId = :bookId
          AND kind != 'BOOKMARK'
          AND startChar < :endChar
          AND endChar > :startChar
        ORDER BY startChar ASC
        """
    )
    suspend fun overlapping(bookId: Long, startChar: Int, endChar: Int): List<AnnotationEntity>

    @Query(
        "SELECT * FROM annotations WHERE bookId = :bookId AND kind = 'BOOKMARK' " +
            "AND startChar = :startChar LIMIT 1"
    )
    suspend fun bookmarkAt(bookId: Long, startChar: Int): AnnotationEntity?
}
