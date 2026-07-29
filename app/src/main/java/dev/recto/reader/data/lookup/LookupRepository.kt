package dev.recto.reader.data.lookup

import dev.recto.reader.data.db.LookupCacheEntity
import dev.recto.reader.data.db.LookupDao
import dev.recto.reader.data.db.VocabularyEntity
import kotlinx.coroutines.flow.Flow

/**
 * Lookups, cache first.
 *
 * A cache hit is the difference between a dictionary that works on the bus
 * and one that does not. Entries never expire on read - a definition from
 * last month is still a definition - but stale rows are swept periodically
 * so the table cannot grow without bound.
 */
class LookupRepository(private val dao: LookupDao) {

    private companion object {
        const val DICTIONARY = "DICTIONARY"
        const val WIKIPEDIA = "WIKIPEDIA"
        const val CACHE_TTL_MS = 90L * 24 * 60 * 60 * 1000
    }

    suspend fun define(word: String): LookupState<DictionaryEntry> {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return LookupState.Empty("No word selected")

        dao.cached(DICTIONARY, key)?.let { hit ->
            runCatching { LookupApi.parseCachedDictionary(key, hit.payload) }
                .getOrNull()
                ?.takeIf { !it.isEmpty }
                ?.let { return LookupState.Success(it) }
        }

        val result = LookupApi.defineWordRaw(key)
        if (result is RawResult.Ok) {
            dao.cache(LookupCacheEntity(source = DICTIONARY, term = key, payload = result.body))
            val parsed = runCatching { LookupApi.parseCachedDictionary(key, result.body) }
                .getOrNull()
            return if (parsed == null || parsed.isEmpty) {
                LookupState.Empty("No definition found for \"$key\"")
            } else {
                LookupState.Success(parsed)
            }
        }
        return (result as RawResult.Miss).state()
    }

    suspend fun wikipedia(term: String): LookupState<WikipediaSummary> {
        val key = term.trim()
        if (key.isEmpty()) return LookupState.Empty("Nothing to look up")

        dao.cached(WIKIPEDIA, key.lowercase())?.let { hit ->
            runCatching { LookupApi.parseCachedWikipedia(hit.payload) }
                .getOrNull()
                ?.let { return LookupState.Success(it) }
        }

        val result = LookupApi.wikipediaRaw(key)
        if (result is RawResult.Ok) {
            dao.cache(
                LookupCacheEntity(source = WIKIPEDIA, term = key.lowercase(), payload = result.body)
            )
            val parsed = runCatching { LookupApi.parseCachedWikipedia(result.body) }.getOrNull()
            return if (parsed == null) {
                LookupState.Empty("No Wikipedia article for \"$key\"")
            } else {
                LookupState.Success(parsed)
            }
        }
        return (result as RawResult.Miss).state()
    }

    suspend fun sweepCache() {
        dao.evictOlderThan(System.currentTimeMillis() - CACHE_TTL_MS)
    }

    // --- vocabulary ---

    fun observeVocabulary(): Flow<List<VocabularyEntity>> = dao.observeVocabulary()

    fun observeDueCount(): Flow<Int> = dao.observeDueCount()

    suspend fun dueCards(limit: Int = 30): List<VocabularyEntity> = dao.dueCards(limit = limit)

    suspend fun isSaved(word: String): Boolean = dao.byWord(word.trim().lowercase()) != null

    suspend fun saveWord(
        word: String,
        definition: String,
        partOfSpeech: String?,
        phonetic: String?,
        contextSentence: String?,
        bookId: Long?,
        bookTitle: String?
    ): Boolean {
        val key = word.trim().lowercase()
        if (key.isEmpty() || definition.isBlank()) return false
        val id = dao.save(
            VocabularyEntity(
                word = key,
                definition = definition,
                partOfSpeech = partOfSpeech,
                phonetic = phonetic,
                contextSentence = contextSentence,
                bookId = bookId,
                bookTitle = bookTitle
            )
        )
        return id != -1L
    }

    suspend fun review(card: VocabularyEntity, recall: Recall) {
        val next = Sm2.schedule(card, recall)
        dao.updateSchedule(
            id = card.id,
            ease = next.easeFactor,
            interval = next.intervalDays,
            reps = next.repetitions,
            dueAt = next.dueAt,
            correct = if (recall == Recall.AGAIN) 0 else 1
        )
    }

    suspend fun deleteWord(id: Long) = dao.delete(id)
}

/** Raw HTTP outcome, so the repository can cache the body before parsing. */
sealed interface RawResult {
    data class Ok(val body: String) : RawResult
    data class Miss(val empty: Boolean, val message: String) : RawResult {
        fun <T> state(): LookupState<T> =
            if (empty) LookupState.Empty(message) else LookupState.Failed(message)
    }
}
