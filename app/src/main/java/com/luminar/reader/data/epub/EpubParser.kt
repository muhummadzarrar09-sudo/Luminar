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

    fun parse(epubFile: File, coversDir: File): EpubMetadata {
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

        // Extract full-path from <rootfile full-path="..." />
        val regex = Regex("""full-path\s*=\s*"([^"]+)"""")
        return regex.find(containerXml)?.groupValues?.get(1)
            ?: throw IllegalStateException("Cannot find OPF path in container.xml")
    }

    // ─── OPF parsing ─────────────────────────────────────────

    private fun parseManifest(opf: String): Map<String, String> {
        // id → href mapping
        val map = mutableMapOf<String, String>()
        val regex = Regex("""<item\s[^>]*id\s*=\s*"([^"]+)"[^>]*href\s*=\s*"([^"]+)"[^>]*/?>""")
        val regexAlt = Regex("""<item\s[^>]*href\s*=\s*"([^"]+)"[^>]*id\s*=\s*"([^"]+)"[^>]*/?>""")

        for (match in regex.findAll(opf)) {
            map[match.groupValues[1]] = match.groupValues[2]
        }
        // Some EPUBs have href before id
        for (match in regexAlt.findAll(opf)) {
            val href = match.groupValues[1]
            val id = match.groupValues[2]
            if (id !in map) map[id] = href
        }
        return map
    }

    private fun parseSpine(opf: String): List<String> {
        // <itemref idref="chapter1" />
        val regex = Regex("""<itemref\s[^>]*idref\s*=\s*"([^"]+)"[^>]*/?>""")
        return regex.findAll(opf).map { it.groupValues[1] }.toList()
    }

    // ─── Cover image extraction ──────────────────────────────

    private fun extractCoverImage(
        zip: ZipFile,
        opf: String,
        manifest: Map<String, String>,
        opfDir: String,
        coversDir: File
    ): String? {
        // Strategy 1: <meta name="cover" content="cover-image" />
        val coverIdRegex = Regex("""<meta\s[^>]*name\s*=\s*"cover"[^>]*content\s*=\s*"([^"]+)"[^>]*/?>""")
        val coverId = coverIdRegex.find(opf)?.groupValues?.get(1)

        if (coverId != null) {
            val href = manifest[coverId]
            if (href != null) {
                val path = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
                return extractImage(zip, path, coversDir)
            }
        }

        // Strategy 2: item with properties="cover-image"
        val coverPropsRegex = Regex("""<item\s[^>]*properties\s*=\s*"cover-image"[^>]*href\s*=\s*"([^"]+)"[^>]*/?>""")
        val coverHref = coverPropsRegex.find(opf)?.groupValues?.get(1)
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

        // Remove everything inside <head>...</head>
        text = text.replace(Regex("<head[^>]*>[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), "")

        // Convert block elements to newlines
        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
        text = text.replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("</li>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("</h[1-6]>", RegexOption.IGNORE_CASE), "\n\n")

        // Heading markers: inject markdown-style # for rendering
        for (level in 1..6) {
            val prefix = "#".repeat(level)
            text = text.replace(Regex("<h$level[^>]*>", RegexOption.IGNORE_CASE), "\n$prefix ")
        }

        // List items → bullet
        text = text.replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "- ")

        // Bold → **
        text = text.replace(Regex("<(b|strong)[^>]*>", RegexOption.IGNORE_CASE), "**")
        text = text.replace(Regex("</(b|strong)>", RegexOption.IGNORE_CASE), "**")

        // Italic → *
        text = text.replace(Regex("<(i|em)[^>]*>", RegexOption.IGNORE_CASE), "*")
        text = text.replace(Regex("</(i|em)>", RegexOption.IGNORE_CASE), "*")

        // Strip all remaining tags
        text = text.replace(Regex("<[^>]+>"), "")

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
            .replace(Regex("&#(\\d+);")) { match ->
                val code = match.groupValues[1].toIntOrNull()
                if (code != null) String(Character.toChars(code)) else match.value
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                if (code != null) String(Character.toChars(code)) else match.value
            }

        // Collapse excessive blank lines
        text = text.replace(Regex("\n{3,}"), "\n\n")

        return text.trim()
    }

    private fun extractHtmlTitle(html: String): String? {
        // Try <title> tag
        val titleMatch = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE).find(html)
        val title = titleMatch?.groupValues?.get(1)?.trim()
        if (!title.isNullOrBlank() && title.length < 200) return title

        // Try first <h1>
        val h1Match = Regex("<h1[^>]*>(.*?)</h1>", RegexOption.IGNORE_CASE).find(html)
        val h1 = h1Match?.groupValues?.get(1)
            ?.replace(Regex("<[^>]+>"), "")?.trim()
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
