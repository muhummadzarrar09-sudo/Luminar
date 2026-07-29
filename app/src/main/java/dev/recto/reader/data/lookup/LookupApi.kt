package dev.recto.reader.data.lookup

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dictionary and Wikipedia lookups.
 *
 * Deliberately plain HttpURLConnection and org.json rather than Retrofit or
 * Ktor. Two GET requests returning small JSON documents do not justify a
 * networking stack, a serialization library and their transitive dependencies
 * - that would be several MB of APK for something the platform already does.
 *
 * Both endpoints were verified live before this was written:
 *
 *  - api.dictionaryapi.dev  - no key, Wiktionary-backed, returns definitions
 *    grouped by part of speech plus IPA and audio URLs.
 *  - en.wikipedia.org REST  - no key, 500 requests/hour per IP anonymous,
 *    returns a clean extract and thumbnail.
 */
object LookupApi {

    private const val DICT_BASE = "https://api.dictionaryapi.dev/api/v2/entries/en/"
    private const val WIKI_BASE = "https://en.wikipedia.org/api/rest_v1/page/summary/"

    /**
     * Wikimedia asks clients to identify themselves. An anonymous or absent
     * User-Agent can get rate-limited harder or blocked outright.
     */
    private const val USER_AGENT =
        "Recto/0.1 (Android ebook reader; https://github.com/muhummadzarrar09-sudo/Recto)"

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    /**
     * Fetches the raw dictionary JSON. The repository caches the body before
     * parsing, so a parser improvement later does not invalidate the cache.
     */
    suspend fun defineWordRaw(word: String): RawResult = withContext(Dispatchers.IO) {
        val clean = word.trim().lowercase()
        if (clean.isEmpty()) {
            return@withContext RawResult.Miss(empty = true, message = "No word selected")
        }
        when (val r = getString(DICT_BASE + URLEncoder.encode(clean, "UTF-8"))) {
            is HttpResult.Ok -> RawResult.Ok(r.body)
            is HttpResult.NotFound ->
                RawResult.Miss(empty = true, message = "No dictionary entry for \"$clean\"")
            is HttpResult.Error -> RawResult.Miss(empty = false, message = r.message)
        }
    }

    suspend fun wikipediaRaw(term: String): RawResult = withContext(Dispatchers.IO) {
        val clean = term.trim()
        if (clean.isEmpty()) {
            return@withContext RawResult.Miss(empty = true, message = "Nothing to look up")
        }
        // The REST API wants underscores for spaces, path-encoded, but real
        // titles can contain slashes which must survive.
        val title = clean.replace(' ', '_')
        val url = WIKI_BASE + URLEncoder.encode(title, "UTF-8").replace("%2F", "/")
        when (val r = getString(url)) {
            is HttpResult.Ok -> RawResult.Ok(r.body)
            is HttpResult.NotFound ->
                RawResult.Miss(empty = true, message = "No Wikipedia article for \"$clean\"")
            is HttpResult.Error -> RawResult.Miss(empty = false, message = r.message)
        }
    }

    fun parseCachedDictionary(word: String, body: String): DictionaryEntry =
        parseDictionary(word, body)

    fun parseCachedWikipedia(body: String): WikipediaSummary? = parseWikipedia(body)

    // --- http ---------------------------------------------------------------

    private sealed interface HttpResult {
        data class Ok(val body: String) : HttpResult
        data object NotFound : HttpResult
        data class Error(val message: String) : HttpResult
    }

    private fun getString(url: String): HttpResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            when (val code = connection.responseCode) {
                in 200..299 ->
                    HttpResult.Ok(connection.inputStream.bufferedReader().use { it.readText() })

                404 -> HttpResult.NotFound

                429 -> HttpResult.Error("Too many lookups just now. Try again in a moment.")

                else -> HttpResult.Error("Lookup failed (HTTP $code)")
            }
        } catch (e: Exception) {
            HttpResult.Error(friendlyError(e))
        } finally {
            connection?.disconnect()
        }
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "No internet connection"
        is java.net.SocketTimeoutException -> "Lookup timed out"
        is IOException -> "Could not reach the dictionary"
        else -> e.message ?: "Lookup failed"
    }

    // --- parsing ------------------------------------------------------------

    /**
     * dictionaryapi.dev returns an array of entries. We merge them: the same
     * word can appear several times with different etymologies, and a reader
     * wants all the senses in one card, not the first etymology only.
     */
    private fun parseDictionary(word: String, body: String): DictionaryEntry {
        val root = JSONArray(body)

        var phonetic: String? = null
        var audio: String? = null
        var sourceUrl: String? = null
        val byPos = LinkedHashMap<String, MutableList<Sense>>()
        val synonyms = LinkedHashSet<String>()
        val antonyms = LinkedHashSet<String>()

        for (i in 0 until root.length()) {
            val entry = root.optJSONObject(i) ?: continue

            if (phonetic == null) {
                phonetic = entry.optString("phonetic").takeIf { it.isNotBlank() }
            }

            entry.optJSONArray("phonetics")?.let { arr ->
                for (j in 0 until arr.length()) {
                    val p = arr.optJSONObject(j) ?: continue
                    if (phonetic.isNullOrBlank()) {
                        phonetic = p.optString("text").takeIf { it.isNotBlank() }
                    }
                    if (audio == null) {
                        audio = p.optString("audio").takeIf { it.isNotBlank() }
                    }
                }
            }

            if (sourceUrl == null) {
                sourceUrl = entry.optJSONArray("sourceUrls")?.optString(0)
                    ?.takeIf { it.isNotBlank() }
            }

            entry.optJSONArray("meanings")?.let { meanings ->
                for (j in 0 until meanings.length()) {
                    val meaning = meanings.optJSONObject(j) ?: continue
                    val pos = meaning.optString("partOfSpeech").ifBlank { "other" }
                    val list = byPos.getOrPut(pos) { mutableListOf() }

                    meaning.optJSONArray("definitions")?.let { defs ->
                        for (k in 0 until defs.length()) {
                            val d = defs.optJSONObject(k) ?: continue
                            val text = d.optString("definition").takeIf { it.isNotBlank() }
                                ?: continue
                            // Same definition can repeat across etymologies.
                            if (list.none { it.definition == text }) {
                                list.add(
                                    Sense(
                                        definition = text,
                                        example = d.optString("example")
                                            .takeIf { it.isNotBlank() }
                                    )
                                )
                            }
                        }
                    }

                    meaning.optJSONArray("synonyms")?.let { collectInto(it, synonyms) }
                    meaning.optJSONArray("antonyms")?.let { collectInto(it, antonyms) }
                }
            }
        }

        return DictionaryEntry(
            word = root.optJSONObject(0)?.optString("word")?.takeIf { it.isNotBlank() } ?: word,
            phonetic = phonetic,
            audioUrl = audio,
            entries = byPos.map { (pos, senses) ->
                PartOfSpeechEntry(
                    partOfSpeech = pos,
                    // Six senses is plenty on a phone; the long tail on common
                    // words is mostly archaic or highly technical.
                    senses = senses.take(6),
                    synonyms = synonyms.take(8).toList(),
                    antonyms = antonyms.take(6).toList()
                )
            },
            sourceUrl = sourceUrl
        )
    }

    private fun collectInto(array: JSONArray, into: MutableSet<String>) {
        for (i in 0 until array.length()) {
            array.optString(i).takeIf { it.isNotBlank() }?.let { into.add(it) }
        }
    }

    private fun parseWikipedia(body: String): WikipediaSummary? {
        val o = JSONObject(body)

        // Disambiguation pages have no useful extract; treat them as a miss
        // rather than showing "X may refer to:" with no list.
        if (o.optString("type") == "disambiguation") return null

        val extract = o.optString("extract")
        if (extract.isBlank()) return null

        return WikipediaSummary(
            title = o.optString("title").ifBlank { "Wikipedia" },
            description = o.optString("description").takeIf { it.isNotBlank() },
            extract = extract,
            thumbnailUrl = o.optJSONObject("thumbnail")?.optString("source")
                ?.takeIf { it.isNotBlank() },
            pageUrl = o.optJSONObject("content_urls")
                ?.optJSONObject("mobile")
                ?.optString("page")
                ?.takeIf { it.isNotBlank() }
                ?: "https://en.wikipedia.org/wiki/" + o.optString("title").replace(' ', '_')
        )
    }
}
