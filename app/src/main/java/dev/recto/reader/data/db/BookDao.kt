package dev.recto.reader.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY COALESCE(lastOpenedAt, addedAt) DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun byId(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE sourceUri = :uri LIMIT 1")
    suspend fun bySourceUri(uri: String): BookEntity?

    @Query("SELECT * FROM books WHERE contentHash = :hash LIMIT 1")
    suspend fun byContentHash(hash: String): BookEntity?

    @Query(
        "SELECT * FROM books WHERE matchTitle = :title AND matchAuthor = :author LIMIT 1"
    )
    suspend fun byTitleAuthor(title: String, author: String): BookEntity?

    /**
     * Most recently opened book, for the "Continue reading" card.
     * Returns null until something has actually been opened.
     */
    @Query("SELECT * FROM books WHERE lastOpenedAt IS NOT NULL ORDER BY lastOpenedAt DESC LIMIT 1")
    fun observeCurrent(): Flow<BookEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity): Long

    @Query("UPDATE books SET lastOpenedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long = System.currentTimeMillis())

    @Query("UPDATE books SET progress = :progress, locator = :locator, lastOpenedAt = :at WHERE id = :id")
    suspend fun saveProgress(
        id: Long,
        progress: Float,
        locator: String?,
        at: Long = System.currentTimeMillis()
    )

    @Query("UPDATE books SET isFinished = :finished WHERE id = :id")
    suspend fun setFinished(id: Long, finished: Boolean)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int
}
