package com.luminar.reader.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luminar.reader.data.model.PageContent
import com.luminar.reader.data.model.SearchResult
import kotlinx.coroutines.flow.Flow

@Dao
interface PageContentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<PageContent>)

    @Query("""
        SELECT b.id AS bookId, b.title AS bookTitle, p.pageOrChapter AS pageOrChapter, p.content AS excerpt
        FROM page_text_fts f
        JOIN page_content p ON f.rowid = p.id
        JOIN books b ON p.bookId = b.id
        WHERE p.bookId = :bookId AND page_text_fts MATCH :query
    """)
    suspend fun search(bookId: Long, query: String): List<SearchResult>

    @Query("""
        SELECT b.id AS bookId, b.title AS bookTitle, p.pageOrChapter AS pageOrChapter, p.content AS excerpt
        FROM page_text_fts f
        JOIN page_content p ON f.rowid = p.id
        JOIN books b ON p.bookId = b.id
        WHERE page_text_fts MATCH :query
    """)
    suspend fun searchGlobal(query: String): List<SearchResult>

    @Query("SELECT COUNT(*) FROM page_content WHERE bookId = :bookId")
    fun getIndexingStatus(bookId: Long): Flow<Int>
}
