package com.luminar.reader.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luminar.reader.data.model.BookToc
import kotlinx.coroutines.flow.Flow

@Dao
interface BookTocDao {
    @Query("SELECT * FROM book_toc WHERE bookId = :bookId ORDER BY sortOrder ASC")
    fun getTocForBook(bookId: Long): Flow<List<BookToc>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BookToc>)

    @Query("DELETE FROM book_toc WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)
}
