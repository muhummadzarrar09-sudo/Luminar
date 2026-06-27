package com.luminar.reader.data.repository

import com.luminar.reader.data.local.db.DictionaryDao
import com.luminar.reader.data.model.DictionaryCache
import com.luminar.reader.network.DictEntry
import com.luminar.reader.network.DictionaryApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DictionaryResult(
    val entry: DictEntry?,
    val isOfflineError: Boolean = false
)

@Singleton
class DictionaryRepository @Inject constructor(
    private val dictionaryDao: DictionaryDao,
    private val apiService: DictionaryApiService
) {
    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, DictEntry::class.java)
    private val adapter = moshi.adapter<List<DictEntry>>(listType)

    suspend fun lookup(word: String): DictionaryResult = withContext(Dispatchers.IO) {
        val cleanWord = word.trim().lowercase()
        if (cleanWord.isEmpty()) return@withContext DictionaryResult(null)

        val now = System.currentTimeMillis()
        val sevenDays = 7L * 24 * 60 * 60 * 1000

        val cached = dictionaryDao.getCache(cleanWord)
        if (cached != null && (now - cached.fetchedAt) < sevenDays) {
            val parsed = runCatching { adapter.fromJson(cached.responseJson)?.firstOrNull() }.getOrNull()
            if (parsed != null) return@withContext DictionaryResult(parsed)
        }

        try {
            val entries = apiService.getEntry(cleanWord)
            val first = entries.firstOrNull()
            if (first != null) {
                val json = adapter.toJson(entries)
                dictionaryDao.insertCache(DictionaryCache(cleanWord, json, now))
                return@withContext DictionaryResult(first)
            }
            DictionaryResult(null)
        } catch (e: Exception) {
            if (cached != null) {
                val parsed = runCatching { adapter.fromJson(cached.responseJson)?.firstOrNull() }.getOrNull()
                return@withContext DictionaryResult(parsed, isOfflineError = parsed == null)
            }
            DictionaryResult(null, isOfflineError = true)
        }
    }
}
