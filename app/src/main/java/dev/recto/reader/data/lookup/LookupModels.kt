package dev.recto.reader.data.lookup

/**
 * One sense of a word, under a part of speech.
 */
data class Sense(
    val definition: String,
    val example: String? = null
)

data class PartOfSpeechEntry(
    val partOfSpeech: String,
    val senses: List<Sense>,
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList()
)

data class DictionaryEntry(
    val word: String,
    /** IPA transcription from the source, e.g. for "serendipity". */
    val phonetic: String? = null,
    /** URL to an mp3 pronunciation, when the source has one. */
    val audioUrl: String? = null,
    val entries: List<PartOfSpeechEntry> = emptyList(),
    val sourceUrl: String? = null
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

data class WikipediaSummary(
    val title: String,
    val description: String? = null,
    val extract: String,
    val thumbnailUrl: String? = null,
    val pageUrl: String
)

/**
 * Every lookup tab is one of these. Loading and error are explicit states
 * rather than nulls, so the card can say what went wrong instead of showing
 * an ambiguous blank.
 */
sealed interface LookupState<out T> {
    data object Idle : LookupState<Nothing>
    data object Loading : LookupState<Nothing>
    data class Success<T>(val data: T) : LookupState<T>
    data class Empty(val message: String) : LookupState<Nothing>
    data class Failed(val message: String) : LookupState<Nothing>
}
