package com.luminar.reader.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luminar.reader.data.model.Highlight
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getHighlightsForBook(bookId: Long): Flow<List<Highlight>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId AND pdfPage = :page ORDER BY createdAt ASC")
    fun getHighlightsForPage(bookId: Long, page: Int): Flow<List<Highlight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(h: Highlight): Long

    @Update
    suspend fun updateHighlight(h: Highlight)

    @Delete
    suspend fun deleteHighlight(h: Highlight)
}
