package com.luminar.reader.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luminar.reader.data.model.Bookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getBookmarksForBook(bookId: Long): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND pdfPage = :page LIMIT 1")
    fun getBookmarkForPage(bookId: Long, page: Int): Flow<Bookmark?>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND epubCfi = :cfi LIMIT 1")
    fun getBookmarkForCfi(bookId: Long, cfi: String): Flow<Bookmark?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(b: Bookmark): Long

    @Update
    suspend fun updateBookmark(b: Bookmark)

    @Delete
    suspend fun deleteBookmark(b: Bookmark)
}
