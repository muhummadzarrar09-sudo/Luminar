package dev.recto.reader.data

import android.util.Xml
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * Just enough EPUB parsing to populate the library: title, author, cover.
 *
 * This is NOT a reading engine. Readium does the reading in Phase 1's reader
 * screen; hand-rolling that was the previous project's central mistake. But
 * pulling three fields out of the OPF for a library card is a small, bounded
 * job with no rendering involved, and doing it ourselves avoids loading a full
 * publication just to draw a grid cell.
 */
data class EpubMetadata(
    val title: String?,
    val author: String?,
    val coverBytes: ByteArray?
) {
    // ByteArray in a data class needs these; the IDE warns otherwise.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EpubMetadata) return false
        return title == other.title &&
            author == other.author &&
            coverBytes.contentEquals(other.coverBytes)
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (author?.hashCode() ?: 0)
        result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
        return result
    }
}

object EpubMetadataReader {

    private const val MAX_COVER_BYTES = 4 * 1024 * 1024

    /**
     * Reads the EPUB in a single streaming pass. We cannot seek in a SAF
     * InputStream, so we collect what we need as entries go past and resolve
     * the cover at the end.
     */
    fun read(open: () -> InputStream): EpubMetadata {
        var title: String? = null
        var author: String? = null
        var coverHref: String? = null
        var opfDir = ""

        // Candidate images kept while we work out which one is the cover.
        val images = mutableMapOf<String, ByteArray>()

        try {
            open().use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name

                        when {
                            name.endsWith(".opf", ignoreCase = true) -> {
                                opfDir = name.substringBeforeLast('/', "")
                                val opf = parseOpf(zip.readBytes())
                                title = opf.title
                                author = opf.author
                                coverHref = opf.coverHref
                            }

                            isImage(name) -> {
                                if (entry.size in 1..MAX_COVER_BYTES || entry.size == -1L) {
                                    val bytes = zip.readBytes()
                                    if (bytes.size <= MAX_COVER_BYTES) {
                                        images[name] = bytes
                                    }
                                }
                            }
                        }

                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (_: Exception) {
            // A malformed EPUB should degrade to "no metadata", never crash
            // the import. The user still gets the book in their library with
            // a filename-derived title.
        }

        val cover = resolveCover(images, coverHref, opfDir)
        return EpubMetadata(title?.trim()?.ifBlank { null }, author?.trim()?.ifBlank { null }, cover)
    }

    private fun isImage(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") ||
            n.endsWith(".png") || n.endsWith(".webp")
    }

    private fun resolveCover(
        images: Map<String, ByteArray>,
        coverHref: String?,
        opfDir: String
    ): ByteArray? {
        if (images.isEmpty()) return null

        // 1. Exact match on the href the OPF declared.
        if (!coverHref.isNullOrBlank()) {
            val target = if (opfDir.isEmpty()) coverHref else "$opfDir/$coverHref"
            val normalised = normalise(target)
            images.entries.firstOrNull { normalise(it.key) == normalised }?.let { return it.value }

            // 2. Match on filename alone, for EPUBs with sloppy relative paths.
            val leaf = coverHref.substringAfterLast('/')
            images.entries.firstOrNull { it.key.substringAfterLast('/') == leaf }?.let { return it.value }
        }

        // 3. Anything that calls itself a cover.
        images.entries
            .firstOrNull { it.key.substringAfterLast('/').contains("cover", ignoreCase = true) }
            ?.let { return it.value }

        // 4. Give up and use the largest image, which is nearly always the cover.
        return images.maxByOrNull { it.value.size }?.value
    }

    /** Collapses "a/b/../c" and leading "./" so href comparisons work. */
    private fun normalise(path: String): String {
        val out = mutableListOf<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> Unit
                // removeAt(lastIndex), NOT removeLast(): with compileSdk 35+
                // and minSdk below 35, Kotlin resolves removeLast() to the new
                // java.util.SequencedCollection method, which does not exist on
                // Android 14 and below -> NoSuchMethodError at runtime.
                ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
                else -> out.add(part)
            }
        }
        return out.joinToString("/")
    }

    private data class Opf(val title: String?, val author: String?, val coverHref: String?)

    /**
     * Pulls dc:title, dc:creator and the cover image href out of the OPF.
     *
     * Two ways to declare a cover, and real EPUBs use both:
     *   EPUB 3: <item properties="cover-image" href="...">
     *   EPUB 2: <meta name="cover" content="some-id"> plus a matching item id
     */
    private fun parseOpf(bytes: ByteArray): Opf {
        var title: String? = null
        var author: String? = null
        var coverImageHref: String? = null
        var coverMetaId: String? = null
        val itemsById = mutableMapOf<String, String>()

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(bytes.inputStream(), null)

            var event = parser.eventType
            var pending: String? = null

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.substringAfterLast(':').lowercase()
                        when (tag) {
                            "title" -> if (title == null) pending = "title"
                            "creator" -> if (author == null) pending = "creator"

                            "meta" -> {
                                val n = parser.getAttributeValue(null, "name")
                                if (n.equals("cover", ignoreCase = true)) {
                                    coverMetaId = parser.getAttributeValue(null, "content")
                                }
                            }

                            "item" -> {
                                val id = parser.getAttributeValue(null, "id")
                                val href = parser.getAttributeValue(null, "href")
                                val props = parser.getAttributeValue(null, "properties")
                                if (id != null && href != null) itemsById[id] = href
                                if (props != null && props.contains("cover-image") && href != null) {
                                    coverImageHref = href
                                }
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            when (pending) {
                                "title" -> title = text
                                "creator" -> author = text
                            }
                        }
                        pending = null
                    }

                    XmlPullParser.END_TAG -> pending = null
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // Partial metadata is fine.
        }

        val href = coverImageHref ?: coverMetaId?.let { itemsById[it] }
        return Opf(title, author, href)
    }
}
