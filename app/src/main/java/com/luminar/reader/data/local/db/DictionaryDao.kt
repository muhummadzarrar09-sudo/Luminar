package com.luminar.reader.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luminar.reader.data.model.DictionaryCache

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary_cache WHERE LOWER(word) = LOWER(:word) LIMIT 1")
    suspend fun getCache(word: String): DictionaryCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: DictionaryCache)
}
