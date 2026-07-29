package dev.recto.reader.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A cached dictionary response.
 *
 * This is what makes an online dictionary feel offline. Vocabulary repeats
 * heavily inside a single book - an author who reaches for "peregrination"
 * once reaches for it again - so after the first lookup the word is instant
 * and works with no signal.
 *
 * [payload] is the raw API JSON. Storing the response rather than a parsed
 * shape means the parser can improve later without invalidating the cache.
 */
@Entity(
    tableName = "lookup_cache",
    indices = [Index(value = ["source", "term"], unique = true)]
)
data class LookupCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** DICTIONARY or WIKIPEDIA. */
    val source: String,
    /** Lower-cased lookup key. */
    val term: String,
    val payload: String,
    val fetchedAt: Long = System.currentTimeMillis()
)

/**
 * A word saved for study, with SM-2 scheduling.
 *
 * SM-2 is the SuperMemo algorithm Anki is built on. Three fields drive it:
 *
 *  - [easeFactor]  how easy this card is for you, starting at 2.5. Getting it
 *                  wrong drags it down, so hard words come back sooner.
 *  - [intervalDays] how long until the next review.
 *  - [repetitions]  consecutive correct answers; a lapse resets it to zero.
 */
@Entity(
    tableName = "vocabulary",
    indices = [
        Index(value = ["word"], unique = true),
        Index(value = ["dueAt"])
    ]
)
data class VocabularyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val word: String,

    /** Short definition, so the list and flashcards work without the network. */
    val definition: String,
    val partOfSpeech: String? = null,
    val phonetic: String? = null,

    /** The sentence you met the word in - context is most of what makes a
     *  word stick, so it is worth carrying around. */
    val contextSentence: String? = null,
    val bookId: Long? = null,
    val bookTitle: String? = null,

    val addedAt: Long = System.currentTimeMillis(),

    // --- SM-2 state ---
    val easeFactor: Float = 2.5f,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val dueAt: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long? = null,
    val timesReviewed: Int = 0,
    val timesCorrect: Int = 0
)

@Dao
interface LookupDao {

    @Query("SELECT * FROM lookup_cache WHERE source = :source AND term = :term LIMIT 1")
    suspend fun cached(source: String, term: String): LookupCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cache(entry: LookupCacheEntity)

    @Query("DELETE FROM lookup_cache WHERE fetchedAt < :before")
    suspend fun evictOlderThan(before: Long)

    // --- vocabulary ---

    @Query("SELECT * FROM vocabulary ORDER BY addedAt DESC")
    fun observeVocabulary(): Flow<List<VocabularyEntity>>

    @Query("SELECT COUNT(*) FROM vocabulary WHERE dueAt <= :now")
    fun observeDueCount(now: Long = System.currentTimeMillis()): Flow<Int>

    /** Cards to review, soonest-due first. */
    @Query("SELECT * FROM vocabulary WHERE dueAt <= :now ORDER BY dueAt ASC LIMIT :limit")
    suspend fun dueCards(now: Long = System.currentTimeMillis(), limit: Int = 30):
        List<VocabularyEntity>

    @Query("SELECT * FROM vocabulary WHERE word = :word LIMIT 1")
    suspend fun byWord(word: String): VocabularyEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun save(entity: VocabularyEntity): Long

    @Query(
        """
        UPDATE vocabulary
        SET easeFactor = :ease,
            intervalDays = :interval,
            repetitions = :reps,
            dueAt = :dueAt,
            lastReviewedAt = :now,
            timesReviewed = timesReviewed + 1,
            timesCorrect = timesCorrect + :correct
        WHERE id = :id
        """
    )
    suspend fun updateSchedule(
        id: Long,
        ease: Float,
        interval: Int,
        reps: Int,
        dueAt: Long,
        correct: Int,
        now: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM vocabulary WHERE id = :id")
    suspend fun delete(id: Long)
}
