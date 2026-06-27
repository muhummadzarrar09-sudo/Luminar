// app/src/main/java/com/luminar/reader/data/local/db/BookDao.kt
package com.luminar.reader.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.luminar.reader.data.model.Book
import com.luminar.reader.data.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY COALESCE(lastOpenedAt, addedAt) DESC")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    fun getBookById(id: Long): Flow<Book?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBook(book: Book): Long

    @Delete
    suspend fun deleteBook(book: Book)

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId LIMIT 1")
    fun getProgress(bookId: Long): Flow<ReadingProgress?>

    @Query("SELECT * FROM reading_progress")
    fun getAllProgress(): Flow<List<ReadingProgress>>

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgress)

    @Query("UPDATE books SET lastOpenedAt = :timestamp WHERE id = :bookId")
    suspend fun updateLastOpenedAt(bookId: Long, timestamp: Long)

    @Query("UPDATE books SET indexingProgress = :progress WHERE id = :bookId")
    suspend fun updateIndexingProgress(bookId: Long, progress: Int)
}
