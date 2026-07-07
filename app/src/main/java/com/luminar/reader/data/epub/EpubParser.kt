// app/src/main/java/com/luminar/reader/data/epub/EpubParser.kt
package com.luminar.reader.data.epub

import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

data class EpubChapter(
    val title: String,
    val index: Int,
    val textContent: String
)

data class EpubMetadata(
    val title: String?,
    val chapters: List<EpubChapter>,
    val coverPath: String?
)

@Singleton
class EpubParser @Inject constructor() {

    private companion object Patterns {
        val FULL_PATH = Regex("""full-path\s*=\s*"([^"]+)"""")
        val ITEM_ID_HREF = Regex("""<item\s[^>]*id\s*=\s*"([^"]+)"[^>]*href\s*=\s*"([^"]+)"[^>]*/?>""")
        val ITEM_HREF_ID = Regex("""<item\s[^>]*href\s*=\s*"([^"]+)"[^>]*id\s*=\s*"([^"]+)"[^>]*/?>""")
        val ITEMREF = Regex("""<itemref\s[^>]*idref\s*=\s*"([^"]+)"[^>]*/?>""")
        val COVER_META = Regex("""<meta\s[^>]*name\s*=\s*"cover"[^>]*content\s*=\s*"([^"]+)"[^>]*/?>""")
        val COVER_PROPS = Regex("""<item\s[^>]*properties\s*=\s*"cover-image"[^>]*href\s*=\s*"([^"]+)"[^>]*/?>""")
        val HEAD_BLOCK = Regex("<head[^>]*>[\\s\\S]*?</head>", RegexOption.IGNORE_CASE)
        val BR_TAG = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
        val CLOSE_P = Regex("</p>", RegexOption.IGNORE_CASE)
        val CLOSE_DIV = Regex("</div>", RegexOption.IGNORE_CASE)
        val CLOSE_LI = Regex("</li>", RegexOption.IGNORE_CASE)
        val LI_OPEN = Regex("<li[^>]*>", RegexOption.IGNORE_CASE)
        val BOLD_OPEN = Regex("<(b|strong)[^>]*>", RegexOption.IGNORE_CASE)
        val BOLD_CLOSE = Regex("</(b|strong)>", RegexOption.IGNORE_CASE)
        val ITALIC_OPEN = Regex("<(i|em)[^>]*>", RegexOption.IGNORE_CASE)
        val ITALIC_CLOSE = Regex("</(i|em)>", RegexOption.IGNORE_CASE)
        val HTML_TAG = Regex("<[^>]+>")
        val MULTI_NEWLINE = Regex("\n{3,}")
        val NUMERIC_ENTITY = Regex("&#(\\d+);")
        val HEX_ENTITY = Regex("&#x([0-9a-fA-F]+);")
        val TITLE_TAG = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
        val H1_TAG = Regex("<h1[^>]*>(.*?)</h1>", RegexOption.IGNORE_CASE)
        val H_OPEN = (1..6).map { Regex("<h$it[^>]*>", RegexOption.IGNORE_CASE) }
        val H_CLOSE = (1..6).map { Regex("</h$it>", RegexOption.IGNORE_CASE) }
    }

    fun parse(epubFile: File, coversDir: File): EpubMetadata {
        // Basic validation
        if (!epubFile.exists()) throw IllegalArgumentException("EPUB file not found")
        if (epubFile.length() < 100) throw IllegalArgumentException("File too small to be a valid EPUB")
        if (epubFile.length() > 500 * 1024 * 1024) throw IllegalArgumentException("EPUB too large (>500 MB)")

        val zip = ZipFile(epubFile)
        try {
            val opfPath = findOpfPath(zip)
            val opfContent = readZipEntry(zip, opfPath)
            val opfDir = opfPath.substringBeforeLast('/', "")

            val title = extractTag(opfContent, "dc:title")
                ?: extractTag(opfContent, "dc\\:title")

            val manifest = parseManifest(opfContent)
            val spineIds = parseSpine(opfContent)

            // Resolve cover image
            val coverPath = extractCoverImage(zip, opfContent, manifest, opfDir, coversDir)

            // Build chapters from spine order
            val chapters = mutableListOf<EpubChapter>()
            for ((index, spineId) in spineIds.withIndex()) {
                val href = manifest[spineId] ?: continue
                val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href

                val html = runCatching { readZipEntry(zip, fullPath) }.getOrNull() ?: continue
                val text = htmlToPlainText(html)
                if (text.isBlank()) continue

                val chapterTitle = extractHtmlTitle(html)
                    ?: "Chapter ${index + 1}"

                chapters.add(
                    EpubChapter(
                        title = chapterTitle,
                        index = index,
                        textContent = text
                    )
                )
            }

            return EpubMetadata(
                title = title,
                chapters = chapters,
                coverPath = coverPath
            )
        } finally {
            zip.close()
        }
    }

    // ─── container.xml → OPF path ────────────────────────────

    private fun findOpfPath(zip: ZipFile): String {
        val containerEntry = zip.getEntry("META-INF/container.xml")
            ?: throw IllegalStateException("Not a valid EPUB: missing META-INF/container.xml")

        val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
        return FULL_PATH.find(containerXml)?.groupValues?.get(1)
            ?: throw IllegalStateException("Cannot find OPF path in container.xml")
    }

    // ─── OPF parsing ─────────────────────────────────────────

    private fun parseManifest(opf: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (match in ITEM_ID_HREF.findAll(opf)) {
            map[match.groupValues[1]] = match.groupValues[2]
        }
        for (match in ITEM_HREF_ID.findAll(opf)) {
            val href = match.groupValues[1]
            val id = match.groupValues[2]
            if (id !in map) map[id] = href
        }
        return map
    }

    private fun parseSpine(opf: String): List<String> {
        return ITEMREF.findAll(opf).map { it.groupValues[1] }.toList()
    }

    // ─── Cover image extraction ──────────────────────────────

    private fun extractCoverImage(
        zip: ZipFile,
        opf: String,
        manifest: Map<String, String>,
        opfDir: String,
        coversDir: File
    ): String? {
        val coverId = COVER_META.find(opf)?.groupValues?.get(1)

        if (coverId != null) {
            val href = manifest[coverId]
            if (href != null) {
                val path = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
                return extractImage(zip, path, coversDir)
            }
        }

        val coverHref = COVER_PROPS.find(opf)?.groupValues?.get(1)
        if (coverHref != null) {
            val path = if (opfDir.isNotEmpty()) "$opfDir/$coverHref" else coverHref
            return extractImage(zip, path, coversDir)
        }

        // Strategy 3: look for any manifest entry with "cover" in id and image media-type
        for ((id, href) in manifest) {
            if (id.lowercase().contains("cover") &&
                (href.endsWith(".jpg", true) || href.endsWith(".jpeg", true) || href.endsWith(".png", true))
            ) {
                val path = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
                return extractImage(zip, path, coversDir)
            }
        }

        return null
    }

    private fun extractImage(zip: ZipFile, entryPath: String, coversDir: File): String? {
        val entry = zip.getEntry(entryPath) ?: return null
        val ext = entryPath.substringAfterLast('.', "png").lowercase()
        coversDir.mkdirs()
        val outFile = File(coversDir, "${java.util.UUID.randomUUID()}.$ext")
        zip.getInputStream(entry).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outFile.absolutePath
    }

    // ─── HTML → plain text ───────────────────────────────────

    private fun htmlToPlainText(html: String): String {
        var text = html
        text = HEAD_BLOCK.replace(text, "")
        text = BR_TAG.replace(text, "\n")
        text = CLOSE_P.replace(text, "\n\n")
        text = CLOSE_DIV.replace(text, "\n")
        text = CLOSE_LI.replace(text, "\n")

        for (level in 1..6) {
            val prefix = "#".repeat(level)
            text = H_OPEN[level - 1].replace(text, "\n$prefix ")
            text = H_CLOSE[level - 1].replace(text, "\n\n")
        }

        text = LI_OPEN.replace(text, "- ")
        text = BOLD_OPEN.replace(text, "**")
        text = BOLD_CLOSE.replace(text, "**")
        text = ITALIC_OPEN.replace(text, "*")
        text = ITALIC_CLOSE.replace(text, "*")
        text = HTML_TAG.replace(text, "")

        // Decode common HTML entities
        text = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace(NUMERIC_ENTITY) { match ->
                val code = match.groupValues[1].toIntOrNull()
                if (code != null) runCatching { String(Character.toChars(code)) }.getOrDefault(match.value) else match.value
            }
            .replace(HEX_ENTITY) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                if (code != null) runCatching { String(Character.toChars(code)) }.getOrDefault(match.value) else match.value
            }

        text = MULTI_NEWLINE.replace(text, "\n\n")

        return text.trim()
    }

    private fun extractHtmlTitle(html: String): String? {
        val titleMatch = TITLE_TAG.find(html)
        val title = titleMatch?.groupValues?.get(1)?.trim()
        if (!title.isNullOrBlank() && title.length < 200) return title

        val h1Match = H1_TAG.find(html)
        val h1 = h1Match?.groupValues?.get(1)
            ?.replace(HTML_TAG, "")?.trim()
        if (!h1.isNullOrBlank() && h1.length < 200) return h1

        return null
    }

    // ─── Helpers ─────────────────────────────────────────────

    private fun readZipEntry(zip: ZipFile, path: String): String {
        val entry = zip.getEntry(path)
            ?: throw IllegalStateException("EPUB missing entry: $path")
        return zip.getInputStream(entry).bufferedReader().readText()
    }

    private fun extractTag(xml: String, tagName: String): String? {
        val regex = Regex("<$tagName[^>]*>(.*?)</$tagName>", RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }
}
