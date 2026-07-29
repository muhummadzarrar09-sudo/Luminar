package dev.recto.reader.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A stretch of reading.
 *
 * One row per visit to the reader, closed when you leave or the app goes to
 * the background. [dayKey] is the local calendar day as yyyyMMdd, stored
 * alongside the timestamp so streaks and heatmaps can be computed in SQL
 * without every query having to reason about time zones.
 *
 * No foreign key to books on purpose: deleting a book should not rewrite your
 * reading history. The hours you spent are still hours you spent.
 */
@Entity(
    tableName = "reading_sessions",
    indices = [Index("dayKey"), Index("startedAt"), Index("bookId")]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val bookId: Long,
    val bookTitle: String,

    val startedAt: Long,
    val endedAt: Long,

    /** Local calendar day as yyyyMMdd, e.g. 20260728. */
    val dayKey: Int,

    val millisRead: Long,
    val pagesTurned: Int = 0,
    /** Characters advanced through, a rough proxy for words read. */
    val charsRead: Int = 0
)

/** One day's totals, for the heatmap and the daily goal. */
data class DailyTotal(
    val dayKey: Int,
    val millisRead: Long,
    val pagesTurned: Int
)

/** Per-book totals for the "most read" list. */
data class BookTotal(
    val bookId: Long,
    val bookTitle: String,
    val millisRead: Long,
    val sessions: Int
)

@Dao
interface ReadingSessionDao {

    @Insert
    suspend fun insert(session: ReadingSessionEntity): Long

    @Query(
        """
        SELECT dayKey,
               SUM(millisRead) AS millisRead,
               SUM(pagesTurned) AS pagesTurned
        FROM reading_sessions
        GROUP BY dayKey
        ORDER BY dayKey DESC
        """
    )
    fun observeDailyTotals(): Flow<List<DailyTotal>>

    @Query(
        """
        SELECT dayKey,
               SUM(millisRead) AS millisRead,
               SUM(pagesTurned) AS pagesTurned
        FROM reading_sessions
        WHERE dayKey = :dayKey
        GROUP BY dayKey
        """
    )
    fun observeDay(dayKey: Int): Flow<DailyTotal?>

    /** Blocking variant for the reminder alarm, which has no coroutine scope. */
    @Query("SELECT COALESCE(SUM(millisRead), 0) FROM reading_sessions WHERE dayKey = :dayKey")
    suspend fun millisOnDay(dayKey: Int): Long

    @Query("SELECT DISTINCT dayKey FROM reading_sessions ORDER BY dayKey DESC LIMIT :limit")
    suspend fun recentDays(limit: Int = 400): List<Int>

    @Query("SELECT DISTINCT dayKey FROM reading_sessions ORDER BY dayKey DESC LIMIT :limit")
    fun observeRecentDays(limit: Int = 400): Flow<List<Int>>

    @Query("SELECT COALESCE(SUM(millisRead), 0) FROM reading_sessions")
    fun observeTotalMillis(): Flow<Long>

    @Query("SELECT COUNT(*) FROM reading_sessions")
    fun observeSessionCount(): Flow<Int>

    @Query(
        """
        SELECT bookId,
               bookTitle,
               SUM(millisRead) AS millisRead,
               COUNT(*) AS sessions
        FROM reading_sessions
        GROUP BY bookId
        ORDER BY millisRead DESC
        LIMIT :limit
        """
    )
    fun observeTopBooks(limit: Int = 5): Flow<List<BookTotal>>

    @Query("DELETE FROM reading_sessions")
    suspend fun clearAll()
}
