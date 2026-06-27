package com.luminar.reader.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class DictDefinition(
    val definition: String,
    val example: String?
)

@JsonClass(generateAdapter = true)
data class DictMeaning(
    val partOfSpeech: String,
    val definitions: List<DictDefinition>
)

@JsonClass(generateAdapter = true)
data class DictEntry(
    val word: String,
    val phonetic: String?,
    val meanings: List<DictMeaning>
)

interface DictionaryApiService {
    @GET("api/v2/entries/en/{word}")
    suspend fun getEntry(@Path("word") word: String): List<DictEntry>
}
