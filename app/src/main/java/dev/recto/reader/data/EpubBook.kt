package dev.recto.reader.data

import android.util.Xml
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * A chapter's readable text, already stripped of markup.
 */
data class Chapter(
    val href: String,
    val title: String?,
    val text: String
)

data class EpubBook(
    val title: String?,
    val author: String?,
    val chapters: List<Chapter>
) {
    val totalChars: Int by lazy { chapters.sumOf { it.text.length } }
}

/**
 * Loads an EPUB's text content for reading.
 *
 * Scope note: this extracts *text*, not styled HTML. Recto's Phase 2 reader
 * will hand rendering to Readium, which does proper CSS, images, tables and
 * embedded fonts. This exists so Phase 1 delivers a reader you can genuinely
 * finish a novel in, without blocking on Readium's Fragment/WebView stack.
 * For plain prose - which is what public-domain EPUBs overwhelmingly are -
 * extracted text with good typography reads beautifully.
 */
object EpubLoader {

    private const val MAX_CHAPTER_CHARS = 400_000

    fun load(open: () -> InputStream): Result<EpubBook> = runCatching {
        var title: String? = null
        var author: String? = null

        // href -> raw markup, collected in one streaming pass
        val documents = LinkedHashMap<String, String>()
        var spine: List<String> = emptyList()
        var opfDir = ""
        var ncx: String? = null
        var navDoc: String? = null
        var navDocPath: String? = null

        open().use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name.endsWith(".opf", ignoreCase = true) -> {
                            opfDir = name.substringBeforeLast('/', "")
                            val opf = parseSpine(zip.readBytes())
                            title = opf.title
                            author = opf.author
                            spine = opf.hrefs
                        }

                        // EPUB 2 navigation document.
                        name.endsWith(".ncx", ignoreCase = true) -> {
                            ncx = zip.readBytes().toString(Charsets.UTF_8)
                        }

                        isContentDoc(name) -> {
                            val bytes = zip.readBytes()
                            if (bytes.size < 4_000_000) {
                                val markup = bytes.toString(Charsets.UTF_8)
                                documents[name] = markup
                                // EPUB 3 nav documents are ordinary XHTML with
                                // epub:type="toc", so they only reveal
                                // themselves once decoded.
                                if (navDoc == null && markup.contains("epub:type", true) &&
                                    Regex("epub:type\\s*=\\s*[\"'][^\"']*\\btoc\\b").containsMatchIn(markup)
                                ) {
                                    navDoc = markup
                                    navDocPath = name
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        if (documents.isEmpty()) error("No readable content found in this EPUB")

        // Spine order is the author's intended reading order. Fall back to
        // zip order only if the OPF was unusable.
        val ordered: List<Pair<String, String>> = if (spine.isNotEmpty()) {
            spine.mapNotNull { href ->
                val full = if (opfDir.isEmpty()) href else "$opfDir/$href"
                val key = documents.keys.firstOrNull { matches(it, full) || matches(it, href) }
                key?.let { it to documents.getValue(it) }
            }.ifEmpty { documents.toList() }
        } else {
            documents.toList()
        }

        // Titles from the EPUB's own navigation document, which is what the
        // author actually intended the contents list to say. Falling back to
        // the first heading in the markup covers books with no nav doc, and a
        // numbered placeholder covers books with neither.
        val tocTitles: Map<String, String> = buildTocTitles(navDoc, navDocPath, ncx, opfDir)

        val chapters = ordered.mapNotNull { (href, markup) ->
            val text = htmlToText(markup)
            if (text.length < 12) return@mapNotNull null

            val fromToc = tocTitles.entries
                .firstOrNull { matches(href, it.key) }
                ?.value

            Chapter(
                href = href,
                title = fromToc ?: extractHeading(markup),
                text = text.take(MAX_CHAPTER_CHARS)
            )
        }

        if (chapters.isEmpty()) error("This EPUB has no readable text")

        EpubBook(title, author, chapters)
    }

    private fun isContentDoc(name: String): Boolean {
        val n = name.lowercase()
        if (n.startsWith("__macosx") || n.contains("/.")) return false
        return n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm")
    }

    private fun matches(zipName: String, href: String): Boolean {
        val a = normalise(zipName)
        val b = normalise(href.substringBefore('#'))
        return a == b || a.endsWith("/$b") || b.endsWith("/$a")
    }

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

    /**
     * Markup to readable text.
     *
     * Deliberately regex-based rather than a full parser: EPUB content is
     * frequently invalid XML, and a strict parser bails on the whole chapter
     * where this degrades gracefully. Block-level tags become paragraph
     * breaks so the reader can lay out real paragraphs.
     */
    private fun htmlToText(html: String): String {
        var s = html

        s = s.replace(Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?is)<!--.*?-->"), " ")

        // Block boundaries become double newlines.
        s = s.replace(
            Regex("(?i)</(p|div|section|article|h[1-6]|li|blockquote|tr|pre)\\s*>"),
            "\n\n"
        )
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)<hr\\s*/?>"), "\n\n* * *\n\n")

        s = s.replace(Regex("(?s)<[^>]+>"), "")

        s = unescape(s)

        // Collapse runs of spaces but keep paragraph structure.
        s = s.replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        s = s.replace(Regex(" *\\n *"), "\n")
        s = s.replace(Regex("\\n{3,}"), "\n\n")

        return s.trim()
    }

    private fun unescape(s: String): String {
        var out = s
        val named = mapOf(
            "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
            "&quot;" to "\"", "&apos;" to "'", "&mdash;" to "\u2014",
            "&ndash;" to "\u2013", "&hellip;" to "\u2026",
            "&lsquo;" to "\u2018", "&rsquo;" to "\u2019",
            "&ldquo;" to "\u201C", "&rdquo;" to "\u201D"
        )
        for ((k, v) in named) out = out.replace(k, v, ignoreCase = true)

        out = Regex("&#(\\d+);").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 1..0x10FFFF }
                ?.let { String(Character.toChars(it)) } ?: ""
        }
        out = Regex("&#[xX]([0-9a-fA-F]+);").replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.takeIf { it in 1..0x10FFFF }
                ?.let { String(Character.toChars(it)) } ?: ""
        }
        return out
    }

    private fun extractHeading(html: String): String? {
        val m = Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>").find(html) ?: return null
        val text = unescape(m.groupValues[1].replace(Regex("(?s)<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ")
            .trim()
        return text.ifBlank { null }?.take(120)
    }

    private data class SpineInfo(
        val title: String?,
        val author: String?,
        val hrefs: List<String>
    )

    private fun parseSpine(bytes: ByteArray): SpineInfo {
        var title: String? = null
        var author: String? = null
        val manifest = mutableMapOf<String, String>()
        val spineIds = mutableListOf<String>()

        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(bytes.inputStream(), null)

            var event = parser.eventType
            var pending: String? = null

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.substringAfterLast(':').lowercase()) {
                            "title" -> if (title == null) pending = "title"
                            "creator" -> if (author == null) pending = "creator"
                            "item" -> {
                                val id = parser.getAttributeValue(null, "id")
                                val href = parser.getAttributeValue(null, "href")
                                val type = parser.getAttributeValue(null, "media-type")
                                if (id != null && href != null &&
                                    (type == null || type.contains("html"))
                                ) {
                                    manifest[id] = href
                                }
                            }
                            "itemref" -> parser.getAttributeValue(null, "idref")
                                ?.let { spineIds.add(it) }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val t = parser.text?.trim().orEmpty()
                        if (t.isNotEmpty()) {
                            when (pending) {
                                "title" -> title = t
                                "creator" -> author = t
                            }
                        }
                        pending = null
                    }

                    XmlPullParser.END_TAG -> pending = null
                }
                event = parser.next()
            }
        }

        return SpineInfo(title, author, spineIds.mapNotNull { manifest[it] })
    }
    /**
     * Maps content-document href -> chapter title, from whichever navigation
     * document the EPUB provides.
     *
     * EPUB 3 uses an XHTML nav document with epub:type="toc"; EPUB 2 uses a
     * separate .ncx file. Plenty of real books in the wild ship both, or a
     * malformed one, so we try nav first and fall back to ncx.
     */
    private fun buildTocTitles(
        navDoc: String?,
        navDocPath: String?,
        ncx: String?,
        opfDir: String
    ): Map<String, String> {
        val fromNav = navDoc?.let { parseNavDoc(it, navDocPath.orEmpty()) }.orEmpty()
        if (fromNav.isNotEmpty()) return fromNav
        return ncx?.let { parseNcx(it, opfDir) }.orEmpty()
    }

    /** EPUB 3: <nav epub:type="toc"> ... <a href="ch1.xhtml">Chapter One</a> */
    private fun parseNavDoc(markup: String, navPath: String): Map<String, String> {
        val navBlock = Regex(
            "(?is)<nav[^>]*epub:type\\s*=\\s*[\"'][^\"']*\\btoc\\b[^\"']*[\"'][^>]*>(.*?)</nav>"
        ).find(markup)?.groupValues?.get(1) ?: return emptyMap()

        val navDir = navPath.substringBeforeLast('/', "")
        val out = LinkedHashMap<String, String>()

        for (m in Regex("(?is)<a[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>").findAll(navBlock)) {
            val href = m.groupValues[1].substringBefore('#').trim()
            val label = cleanLabel(m.groupValues[2])
            if (href.isBlank() || label.isBlank()) continue
            val full = if (navDir.isEmpty()) href else "$navDir/$href"
            out.putIfAbsent(full, label)
        }
        return out
    }

    /** EPUB 2: <navPoint><navLabel><text>..</text></navLabel><content src=".."/> */
    private fun parseNcx(markup: String, opfDir: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (m in Regex("(?is)<navPoint[^>]*>(.*?)</navPoint>").findAll(markup)) {
            val block = m.groupValues[1]
            val label = Regex("(?is)<text[^>]*>(.*?)</text>")
                .find(block)?.groupValues?.get(1)?.let { cleanLabel(it) } ?: continue
            val src = Regex("(?is)<content[^>]*src\\s*=\\s*[\"']([^\"']+)[\"']")
                .find(block)?.groupValues?.get(1)?.substringBefore('#')?.trim() ?: continue
            if (label.isBlank() || src.isBlank()) continue
            val full = if (opfDir.isEmpty()) src else "$opfDir/$src"
            out.putIfAbsent(full, label)
        }
        return out
    }

    private fun cleanLabel(raw: String): String =
        unescape(raw.replace(Regex("(?s)<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)

}
