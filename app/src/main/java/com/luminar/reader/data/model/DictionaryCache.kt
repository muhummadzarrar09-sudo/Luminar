package com.luminar.reader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary_cache")
data class DictionaryCache(
    @PrimaryKey val word: String,
    val responseJson: String,
    val fetchedAt: Long = System.currentTimeMillis()
)
