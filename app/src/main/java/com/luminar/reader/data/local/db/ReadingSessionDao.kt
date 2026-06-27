package com.luminar.reader.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.luminar.reader.data.model.ReadingSession
import kotlinx.coroutines.flow.Flow

data class BookMinutes(val bookId: Long, val minutes: Int)

@Dao
interface ReadingSessionDao {
    @Insert
    suspend fun startSession(session: ReadingSession): Long

    @Query("UPDATE reading_sessions SET endedAt = :endedAt, pagesRead = :pagesRead WHERE id = :id")
    suspend fun endSession(id: Long, endedAt: Long, pagesRead: Int)

    @Query("""
        SELECT COALESCE(SUM((COALESCE(endedAt, startedAt) - startedAt) / 60000), 0) 
        FROM reading_sessions 
        WHERE bookId = :bookId AND startedAt >= (strftime('%s', 'now', 'start of day') * 1000)
    """)
    fun getTodayMinutes(bookId: Long): Flow<Int>

    @Query("""
        SELECT COALESCE(SUM((COALESCE(endedAt, startedAt) - startedAt) / 60000), 0) 
        FROM reading_sessions 
        WHERE bookId = :bookId
    """)
    fun getTotalMinutesForBook(bookId: Long): Flow<Int>

    @Query("""
        SELECT bookId, COALESCE(SUM((COALESCE(endedAt, startedAt) - startedAt) / 60000), 0) AS minutes 
        FROM reading_sessions 
        WHERE startedAt >= (strftime('%s', 'now', 'start of day') * 1000) 
        GROUP BY bookId
    """)
    fun getAllBooksTodayMinutes(): Flow<List<BookMinutes>>

    @Query("""
        SELECT * FROM reading_sessions 
        WHERE startedAt >= (strftime('%s', 'now', '-7 days') * 1000) 
        ORDER BY startedAt DESC
    """)
    fun getSessionsThisWeek(): Flow<List<ReadingSession>>

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<ReadingSession>>
}
