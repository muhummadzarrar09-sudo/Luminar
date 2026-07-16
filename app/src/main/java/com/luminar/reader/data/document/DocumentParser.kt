// app/src/main/java/com/luminar/reader/data/document/DocumentParser.kt
package com.luminar.reader.data.document

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses DOCX, XLSX, PPTX, ODT, ODS, ODP, RTF, FB2, MOBI, AZW3,
 * CHM, XPS, PDB, CBZ, CBR, CBT files into plain readable text —
 * no external libraries, pure JDK.
 */
@Singleton
class DocumentParser @Inject constructor() {

    // ─── Pre-compiled regex cache (avoids recompilation per call) ──

    private companion object Patterns {
        val PARAGRAPH = Regex("<w:p[\\s>][\\s\\S]*?</w:p>", RegexOption.DOT_MATCHES_ALL)
        val HEADING_STYLE = Regex("""<w:pStyle\s+w:val="Heading(\d)"""")
        val TEXT_RUN = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
        val SI_BLOCK = Regex("<si>[\\s\\S]*?</si>", RegexOption.DOT_MATCHES_ALL)
        val T_TAG = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
        val ROW_BLOCK = Regex("<row[\\s>][\\s\\S]*?</row>", RegexOption.DOT_MATCHES_ALL)
        val CELL_BLOCK = Regex("<c[\\s][^>]*>[\\s\\S]*?</c>", RegexOption.DOT_MATCHES_ALL)
        val CELL_VALUE = Regex("<v>(.*?)</v>")
        val SLIDE_TEXT = Regex("<a:t>(.*?)</a:t>", RegexOption.DOT_MATCHES_ALL)
        val HTML_TAG = Regex("<[^>]+>")
        val MULTI_NEWLINE = Regex("\n{3,}")
        val MULTI_SPACE = Regex("\\s{3,}")
        val CONTROL_CHARS = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]")
        val NUMERIC_ENTITY = Regex("&#(\\d+);")
        val HEX_ENTITY = Regex("&#x([0-9a-fA-F]+);")
        val HEAD_TAG = Regex("<head[^>]*>[\\s\\S]*?</head>", RegexOption.IGNORE_CASE)
        val STYLE_TAG = Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
        val SCRIPT_TAG = Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
        val BR_TAG = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
        val CLOSE_P = Regex("</p>", RegexOption.IGNORE_CASE)
        val CLOSE_DIV = Regex("</div>", RegexOption.IGNORE_CASE)
        val LI_OPEN = Regex("<li[^>]*>", RegexOption.IGNORE_CASE)
        val BOLD_OPEN = Regex("<(b|strong)[^>]*>", RegexOption.IGNORE_CASE)
        val BOLD_CLOSE = Regex("</(b|strong)>", RegexOption.IGNORE_CASE)
        val ITALIC_OPEN = Regex("<(i|em)[^>]*>", RegexOption.IGNORE_CASE)
        val ITALIC_CLOSE = Regex("</(i|em)>", RegexOption.IGNORE_CASE)
        val BODY_BLOCK = Regex("<body[^>]*>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE)
        val UNICODE_STR = Regex("""UnicodeString\s*=\s*"([^"]+)"""")
        val FB2_BODY = Regex("<body[^>]*>([\\s\\S]*)</body>", RegexOption.DOT_MATCHES_ALL)
        val FB2_TITLE = Regex("<title>\\s*<p>(.*?)</p>\\s*</title>", RegexOption.DOT_MATCHES_ALL)
        val FB2_SUBTITLE = Regex("<subtitle>(.*?)</subtitle>", RegexOption.DOT_MATCHES_ALL)
        // Heading patterns (generated once)
        val H_OPEN = (1..6).map { Regex("<h$it[^>]*>", RegexOption.IGNORE_CASE) }
        val H_CLOSE = (1..6).map { Regex("</h$it>", RegexOption.IGNORE_CASE) }
    }

    fun parseToText(file: File, extension: String): String {
        return try {
            parseToTextUnsafe(file, extension)
        } catch (e: OutOfMemoryError) {
            "# Error\n\nFile is too large to display in memory.\nTry a smaller file or a different format."
        } catch (e: Exception) {
            "# Error\n\nUnable to parse this file: ${e.message ?: "Unknown error"}\n\n" +
                "File: ${file.name}\nFormat: ${extension.uppercase()}"
        }
    }

    private fun parseToTextUnsafe(file: File, extension: String): String {
        // Guard: don't try to read text from files larger than 50MB
        if (file.length() > 50 * 1024 * 1024 && extension.lowercase() !in listOf(
                "docx", "xlsx", "pptx", "odt", "ods", "odp", "epub", "cbz", "xps"
            )
        ) {
            return "# Large File\n\nThis ${extension.uppercase()} file is ${file.length() / (1024 * 1024)} MB.\n\n" +
                "Files larger than 50 MB may cause performance issues."
        }

        return when (extension.lowercase()) {
            "docx" -> parseDocx(file)
            "xlsx" -> parseXlsx(file)
            "pptx" -> parsePptx(file)
            "odt" -> parseOdt(file)
            "ods" -> parseOds(file)
            "odp" -> parseOdp(file)
            "rtf" -> parseRtf(file)
            "fb2" -> parseFb2(file)
            "doc", "xls", "ppt" -> parseLegacyOffice(file, extension)
            "mobi", "prc" -> parseMobi(file)
            "azw3", "azw", "kfx" -> parseMobi(file) // AZW3 uses same PDB container
            "chm" -> parseChm(file)
            "xps", "oxps" -> parseXps(file)
            "pdb" -> parsePdb(file)
            "cbz" -> parseCbzInfo(file)
            "cbr" -> "# CBR Comic Book\n\nThis file uses RAR compression which requires a proprietary library.\n\n**How to read it:**\n1. Rename the file from `.cbr` to `.rar`\n2. Extract it with a RAR tool\n3. Re-compress the folder as `.zip`\n4. Rename the `.zip` to `.cbz`\n5. Import the `.cbz` file into Luminar\n\nAlternatively, use [Calibre](https://calibre-ebook.com) to convert CBR → CBZ."
            "cbt" -> "# CBT Comic Book\n\nThis file uses TAR archiving.\n\n**How to read it:**\n1. Extract the `.cbt` file using a TAR tool\n2. Re-compress the images folder as `.zip`\n3. Rename the `.zip` to `.cbz`\n4. Import the `.cbz` file into Luminar"
            "djvu", "djv" -> "# DjVu Document\n\nDjVu is a specialized image-based format that requires a native rendering library.\n\n**How to read it:**\n- Convert to PDF using an online DjVu-to-PDF converter\n- Or use [DjView](https://djvu.sourceforge.net) on desktop\n- Then import the PDF into Luminar"
            "html", "htm", "xhtml" -> stripHtmlToMarkdown(file.readText(Charsets.UTF_8))
            else -> file.readText(Charsets.UTF_8)
        }
    }

    /**
     * For CBZ files, returns a list of image entry paths inside the ZIP.
     * Used by ComicReaderView to display pages.
     */
    fun getCbzImagePaths(file: File): List<String> {
        val zip = ZipFile(file)
        try {
            return zip.entries().asSequence()
                .filter { !it.isDirectory }
                .map { it.name }
                .filter { name ->
                    val lower = name.lowercase()
                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                    lower.endsWith(".png") || lower.endsWith(".webp") ||
                    lower.endsWith(".gif") || lower.endsWith(".bmp")
                }
                .sorted() // alphabetical = page order for comics
                .toList()
        } finally {
            zip.close()
        }
    }

    /**
     * Extracts a single image from a CBZ file as raw bytes.
     */
    fun getCbzImageBytes(file: File, entryPath: String): ByteArray {
        val zip = ZipFile(file)
        try {
            val entry = zip.getEntry(entryPath)
                ?: throw IllegalArgumentException("Image not found in CBZ: $entryPath")
            return zip.getInputStream(entry).use { it.readBytes() }
        } finally {
            zip.close()
        }
    }

    // ─── DOCX (OOXML Word) ───────────────────────────────────

    private fun parseDocx(file: File): String {
        val zip = ZipFile(file)
        try {
            val docEntry = zip.getEntry("word/document.xml")
                ?: return "(Unable to read DOCX: missing word/document.xml)"

            val xml = zip.getInputStream(docEntry).bufferedReader().readText()
            return docxXmlToText(xml)
        } finally {
            zip.close()
        }
    }

    private fun docxXmlToText(xml: String): String {
        val sb = StringBuilder()

        // ── Extract tables first and replace with placeholders ──
        val tableRegex = Regex("<w:tbl>[\\s\\S]*?</w:tbl>", RegexOption.DOT_MATCHES_ALL)
        val tables = mutableListOf<String>()
        var processedXml = tableRegex.replace(xml) { match ->
            tables.add(parseDocxTable(match.value))
            "\n%%TABLE_${tables.size - 1}%%\n"
        }

        // ── Process paragraphs ──
        for (pMatch in PARAGRAPH.findAll(processedXml)) {
            val paragraph = pMatch.value

            val hasPageBreak = paragraph.contains("<w:br w:type=\"page\"") ||
                paragraph.contains("<w:lastRenderedPageBreak")
            val hasBorder = paragraph.contains("<w:pBdr>")

            if (hasPageBreak || hasBorder) {
                if (sb.isNotEmpty()) {
                    sb.appendLine("---")
                    sb.appendLine()
                }
            }

            // Check for heading style (by localized name or outline level)
            val headingLevel = HEADING_STYLE.find(paragraph)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""<w:outlineLvl\s+w:val="(\d)"""").find(paragraph)?.groupValues?.get(1)?.toIntOrNull()?.plus(1)

            // Check for list (numbered or bullet)
            val isListItem = paragraph.contains("<w:numPr")
            val listLevel = Regex("""<w:ilvl\s+w:val="(\d+)"""")
                .find(paragraph)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            // Extract runs with inline formatting
            val formattedText = extractDocxRuns(paragraph)

            if (formattedText.isNotBlank()) {
                when {
                    headingLevel != null -> {
                        val prefix = "#".repeat(headingLevel.coerceIn(1, 6))
                        sb.appendLine("$prefix $formattedText")
                    }
                    isListItem -> {
                        val indent = "  ".repeat(listLevel)
                        sb.appendLine("${indent}- $formattedText")
                    }
                    else -> {
                        sb.appendLine(formattedText)
                    }
                }
                sb.appendLine()
            }
        }

        // ── Re-insert tables ──
        var result = sb.toString()
        for ((index, table) in tables.withIndex()) {
            result = result.replace("%%TABLE_$index%%", table)
        }

        return result.trim().ifBlank { "(Empty document)" }
    }

    /**
     * Extract text runs from a DOCX paragraph, preserving bold/italic/underline.
     */
    private fun extractDocxRuns(paragraphXml: String): String {
        val runRegex = Regex("<w:r[\\s>][\\s\\S]*?</w:r>", RegexOption.DOT_MATCHES_ALL)
        val sb = StringBuilder()

        for (runMatch in runRegex.findAll(paragraphXml)) {
            val run = runMatch.value

            // Check formatting properties
            val isBold = run.contains("<w:b/>") || run.contains("<w:b ") ||
                run.contains("""w:val="true"""") && run.contains("<w:b")
            val isItalic = run.contains("<w:i/>") || run.contains("<w:i ")
            val isUnderline = run.contains("<w:u ") && !run.contains("""w:val="none"""")
            val isStrike = run.contains("<w:strike/>") || run.contains("<w:strike ")

            // Extract text and decode XML entities
            val text = decodeXmlEntities(
                TEXT_RUN.findAll(run)
                    .map { it.groupValues[1] }
                    .joinToString("")
            )

            if (text.isNotEmpty()) {
                var formatted = text
                if (isBold) formatted = "**$formatted**"
                if (isItalic) formatted = "*$formatted*"
                if (isStrike) formatted = "~~$formatted~~"
                if (isUnderline && !isBold) formatted = "__${formatted}__"
                sb.append(formatted)
            }
        }

        return sb.toString()
    }

    /**
     * Parse a DOCX table into a Markdown-style table.
     */
    private fun parseDocxTable(tableXml: String): String {
        val rowRegex = Regex("<w:tr[\\s>][\\s\\S]*?</w:tr>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<w:tc[\\s>][\\s\\S]*?</w:tc>", RegexOption.DOT_MATCHES_ALL)
        val rows = mutableListOf<List<String>>()

        for (rowMatch in rowRegex.findAll(tableXml)) {
            val cells = mutableListOf<String>()
            for (cellMatch in cellRegex.findAll(rowMatch.value)) {
                val cellText = decodeXmlEntities(
                    TEXT_RUN.findAll(cellMatch.value)
                        .map { it.groupValues[1] }
                        .joinToString(" ")
                        .trim()
                )
                cells.add(cellText.ifBlank { " " })
            }
            if (cells.isNotEmpty()) rows.add(cells)
        }

        if (rows.isEmpty()) return ""

        // Normalize column count
        val maxCols = rows.maxOf { it.size }
        val normalized = rows.map { row ->
            row + List(maxCols - row.size) { " " }
        }

        // Build markdown table
        val sb = StringBuilder()
        sb.appendLine()

        // Header row
        sb.appendLine("| " + normalized[0].joinToString(" | ") + " |")
        sb.appendLine("| " + normalized[0].joinToString(" | ") { "---" } + " |")

        // Data rows
        for (i in 1 until normalized.size) {
            sb.appendLine("| " + normalized[i].joinToString(" | ") + " |")
        }
        sb.appendLine()

        return sb.toString()
    }

    // ─── XLSX (OOXML Spreadsheet) ────────────────────────────

    private fun parseXlsx(file: File): String {
        val zip = ZipFile(file)
        try {
            // Load shared strings
            val sharedStrings = mutableListOf<String>()
            val ssEntry = zip.getEntry("xl/sharedStrings.xml")
            if (ssEntry != null) {
                val ssXml = zip.getInputStream(ssEntry).bufferedReader().readText()
                val siRegex = Regex("<si>[\\s\\S]*?</si>", RegexOption.DOT_MATCHES_ALL)
                for (siMatch in siRegex.findAll(ssXml)) {
                    val texts = decodeXmlEntities(
                        Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                            .findAll(siMatch.value)
                            .map { it.groupValues[1] }
                            .joinToString("")
                    )
                    sharedStrings.add(texts)
                }
            }

            val sb = StringBuilder()
            var sheetIndex = 1

            // Read each sheet
            while (true) {
                val sheetEntry = zip.getEntry("xl/worksheets/sheet$sheetIndex.xml") ?: break
                val sheetXml = zip.getInputStream(sheetEntry).bufferedReader().readText()

                if (sheetIndex > 1) {
                    sb.appendLine("\n---\n")
                }
                sb.appendLine("# Sheet $sheetIndex")
                sb.appendLine()

                // Parse rows into a proper table
                val allRows = mutableListOf<List<String>>()
                val rowRegex = Regex("<row[\\s>][\\s\\S]*?</row>", RegexOption.DOT_MATCHES_ALL)
                for (rowMatch in rowRegex.findAll(sheetXml)) {
                    val cells = mutableListOf<String>()
                    val cellRegex = Regex("<c[\\s][^>]*>[\\s\\S]*?</c>", RegexOption.DOT_MATCHES_ALL)

                    for (cellMatch in cellRegex.findAll(rowMatch.value)) {
                        val cellXml = cellMatch.value
                        val isSharedString = cellXml.contains("""t="s"""")
                        val valueMatch = Regex("<v>(.*?)</v>").find(cellXml)
                        val value = valueMatch?.groupValues?.get(1) ?: ""

                        val cellText = if (isSharedString) {
                            val idx = value.toIntOrNull()
                            if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else value
                        } else {
                            value
                        }
                        cells.add(cellText.ifBlank { " " })
                    }

                    if (cells.any { it.isNotBlank() }) {
                        allRows.add(cells)
                    }
                }

                // Render as Markdown table
                if (allRows.isNotEmpty()) {
                    val maxCols = allRows.maxOf { it.size }
                    val normalized = allRows.map { row ->
                        row + List(maxCols - row.size) { " " }
                    }
                    // Header
                    sb.appendLine("| " + normalized[0].joinToString(" | ") + " |")
                    sb.appendLine("| " + normalized[0].joinToString(" | ") { "---" } + " |")
                    // Data
                    for (i in 1 until normalized.size) {
                        sb.appendLine("| " + normalized[i].joinToString(" | ") + " |")
                    }
                    sb.appendLine()
                }

                sheetIndex++
            }

            return sb.toString().trim().ifBlank { "(Empty spreadsheet)" }
        } finally {
            zip.close()
        }
    }

    // ─── PPTX (OOXML Presentation) ───────────────────────────

    private fun parsePptx(file: File): String {
        val zip = ZipFile(file)
        try {
            val sb = StringBuilder()
            var slideIndex = 1

            while (true) {
                val slideEntry = zip.getEntry("ppt/slides/slide$slideIndex.xml") ?: break
                val slideXml = zip.getInputStream(slideEntry).bufferedReader().readText()

                sb.appendLine("# Slide $slideIndex")
                sb.appendLine()

                // Extract all text from <a:t> tags
                val textRegex = Regex("<a:t>(.*?)</a:t>", RegexOption.DOT_MATCHES_ALL)
                val texts = textRegex.findAll(slideXml)
                    .map { decodeXmlEntities(it.groupValues[1].trim()) }
                    .filter { it.isNotBlank() }
                    .toList()

                for (text in texts) {
                    sb.appendLine(text)
                }

                sb.appendLine()
                sb.appendLine("---")
                sb.appendLine()
                slideIndex++
            }

            return sb.toString().trim().ifBlank { "(Empty presentation)" }
        } finally {
            zip.close()
        }
    }

    // ─── ODT (OpenDocument Text) ─────────────────────────────

    private fun parseOdt(file: File): String {
        return parseOpenDocument(file, isSpreadsheet = false)
    }

    // ─── ODS (OpenDocument Spreadsheet) ──────────────────────

    private fun parseOds(file: File): String {
        return parseOpenDocument(file, isSpreadsheet = true)
    }

    // ─── ODP (OpenDocument Presentation) ─────────────────────

    private fun parseOdp(file: File): String {
        return parseOpenDocument(file, isSpreadsheet = false)
    }

    private fun parseOpenDocument(file: File, isSpreadsheet: Boolean): String {
        val zip = ZipFile(file)
        try {
            val contentEntry = zip.getEntry("content.xml")
                ?: return "(Unable to read document: missing content.xml)"

            val xml = zip.getInputStream(contentEntry).bufferedReader().readText()
            return odXmlToText(xml, isSpreadsheet)
        } finally {
            zip.close()
        }
    }

    private fun odXmlToText(xml: String, isSpreadsheet: Boolean): String {
        var text = xml

        // Convert headings
        for (level in 1..6) {
            val prefix = "#".repeat(level)
            text = text.replace(Regex("<text:h[^>]*text:outline-level=\"$level\"[^>]*>"), "\n$prefix ")
            text = text.replace("</text:h>", "\n")
        }

        // Convert paragraphs
        text = text.replace(Regex("<text:p[^>]*>"), "\n")
        text = text.replace("</text:p>", "\n")

        // Convert list items
        text = text.replace(Regex("<text:list-item[^>]*>"), "\n- ")

        // Table cells
        if (isSpreadsheet) {
            text = text.replace(Regex("<table:table-cell[^>]*>"), "")
            text = text.replace("</table:table-cell>", " | ")
            text = text.replace(Regex("<table:table-row[^>]*>"), "\n")
        }

        // Line breaks
        text = text.replace("<text:line-break/>", "\n")
        text = text.replace(Regex("<text:s[^/]*/?>"), " ")
        text = text.replace(Regex("<text:tab[^/]*/?>"), "\t")

        // Strip remaining XML
        text = text.replace(Regex("<[^>]+>"), "")

        // Decode entities
        text = decodeHtmlEntities(text)

        // Clean up whitespace
        text = text.replace(Regex("\n{3,}"), "\n\n")

        return text.trim().ifBlank { "(Empty document)" }
    }

    // ─── RTF ─────────────────────────────────────────────────

    private fun parseRtf(file: File): String {
        val raw = file.readText(Charsets.ISO_8859_1)
        val sb = StringBuilder()
        var i = 0
        var depth = 0
        var skipGroup = false

        while (i < raw.length) {
            val ch = raw[i]
            when {
                ch == '{' -> {
                    depth++
                    // Skip certain groups
                    val ahead = raw.substring(i, (i + 30).coerceAtMost(raw.length))
                    if (ahead.contains("\\fonttbl") || ahead.contains("\\colortbl") ||
                        ahead.contains("\\stylesheet") || ahead.contains("\\info") ||
                        ahead.contains("\\pict") || ahead.contains("\\header") ||
                        ahead.contains("\\footer")
                    ) {
                        skipGroup = true
                    }
                    i++
                }
                ch == '}' -> {
                    depth--
                    if (depth <= 1) skipGroup = false
                    i++
                }
                skipGroup -> i++
                ch == '\\' -> {
                    // Control word
                    i++
                    if (i >= raw.length) break
                    val next = raw[i]
                    when {
                        next == '\'' -> {
                            // Hex character
                            if (i + 2 < raw.length) {
                                val hex = raw.substring(i + 1, i + 3)
                                val code = hex.toIntOrNull(16)
                                if (code != null) sb.append(code.toChar())
                                i += 3
                            } else i++
                        }
                        next == '\\' || next == '{' || next == '}' -> {
                            sb.append(next); i++
                        }
                        next == '\n' || next == '\r' -> {
                            sb.append('\n'); i++
                        }
                        next.isLetter() -> {
                            val wordStart = i
                            while (i < raw.length && raw[i].isLetter()) i++
                            // Skip optional numeric parameter
                            val numStart = i
                            if (i < raw.length && (raw[i] == '-' || raw[i].isDigit())) {
                                if (raw[i] == '-') i++
                                while (i < raw.length && raw[i].isDigit()) i++
                            }
                            val word = raw.substring(wordStart, numStart.coerceAtMost(raw.length))
                            // Space delimiter
                            if (i < raw.length && raw[i] == ' ') i++

                            when (word) {
                                "par", "line" -> sb.append('\n')
                                "tab" -> sb.append('\t')
                                "endash" -> sb.append('–')
                                "emdash" -> sb.append('—')
                                "bullet" -> sb.append("• ")
                                "lquote" -> sb.append('\u2018')
                                "rquote" -> sb.append('\u2019')
                                "ldblquote" -> sb.append('\u201C')
                                "rdblquote" -> sb.append('\u201D')
                            }
                        }
                        else -> i++
                    }
                }
                ch == '\n' || ch == '\r' -> i++ // ignore raw newlines
                else -> {
                    sb.append(ch); i++
                }
            }
        }

        return sb.toString()
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .ifBlank { "(Empty document)" }
    }

    // ─── FB2 (FictionBook) ───────────────────────────────────

    private fun parseFb2(file: File): String {
        val raw = if (file.name.endsWith(".zip", true)) {
            val zip = ZipFile(file)
            try {
                val entry = zip.entries().asSequence()
                    .firstOrNull { it.name.endsWith(".fb2", true) }
                    ?: return "(No .fb2 file found inside ZIP)"
                zip.getInputStream(entry).bufferedReader().readText()
            } finally { zip.close() }
        } else {
            file.readText(Charsets.UTF_8)
        }

        return fb2XmlToText(raw)
    }

    private fun fb2XmlToText(xml: String): String {
        // Extract <body>...</body>
        val bodyMatch = Regex("<body[^>]*>([\\s\\S]*)</body>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)
        val body = bodyMatch?.groupValues?.get(1) ?: xml

        var text = body

        // Section titles
        text = text.replace(Regex("<title>\\s*<p>(.*?)</p>\\s*</title>", RegexOption.DOT_MATCHES_ALL)) { match ->
            "\n# ${match.groupValues[1].replace(Regex("<[^>]+>"), "").trim()}\n"
        }

        // Subtitles
        text = text.replace(Regex("<subtitle>(.*?)</subtitle>", RegexOption.DOT_MATCHES_ALL)) { match ->
            "\n## ${match.groupValues[1].replace(Regex("<[^>]+>"), "").trim()}\n"
        }

        // Paragraphs
        text = text.replace(Regex("<p[^>]*>"), "\n")
        text = text.replace("</p>", "\n")

        // Emphasis
        text = text.replace(Regex("<emphasis>"), "*")
        text = text.replace("</emphasis>", "*")
        text = text.replace(Regex("<strong>"), "**")
        text = text.replace("</strong>", "**")

        // Empty line
        text = text.replace("<empty-line/>", "\n")
        text = text.replace(Regex("<empty-line[^/]*/?>"), "\n")

        // Epigraph / cite
        text = text.replace(Regex("<epigraph[^>]*>"), "\n> ")
        text = text.replace(Regex("<cite[^>]*>"), "\n> ")

        // Strip all remaining tags
        text = text.replace(Regex("<[^>]+>"), "")

        text = decodeHtmlEntities(text)
        text = text.replace(Regex("\n{3,}"), "\n\n")

        return text.trim().ifBlank { "(Empty book)" }
    }

    // ─── Legacy Office formats ───────────────────────────────

    private fun parseLegacyOffice(file: File, extension: String): String {
        val formatName = when (extension) {
            "doc" -> "DOC (Word 97-2003)"
            "xls" -> "XLS (Excel 97-2003)"
            "ppt" -> "PPT (PowerPoint 97-2003)"
            else -> extension.uppercase()
        }

        // Attempt to extract any readable text from the binary
        val extractedText = extractTextFromBinary(file)

        return buildString {
            appendLine("# $formatName File")
            appendLine()
            if (extractedText.isNotBlank()) {
                appendLine("*Legacy binary format — text extracted with best effort:*")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(extractedText)
            } else {
                appendLine("This is a legacy binary format ($formatName).")
                appendLine()
                appendLine("For best results, save as **${extension.uppercase()}X** format:")
                appendLine("- DOC → DOCX")
                appendLine("- XLS → XLSX")
                appendLine("- PPT → PPTX")
                appendLine()
                appendLine("You can convert using Microsoft Office, LibreOffice, or Google Docs.")
            }
        }
    }

    /**
     * Best-effort text extraction from binary Office files.
     * Scans for printable ASCII/UTF-8 runs in the binary data.
     */
    private fun extractTextFromBinary(file: File): String {
        val bytes = file.readBytes()
        val sb = StringBuilder()
        val currentRun = StringBuilder()

        for (byte in bytes) {
            val ch = byte.toInt().and(0xFF).toChar()
            if (ch.isPrintableText()) {
                currentRun.append(ch)
            } else {
                if (currentRun.length >= 4) {
                    sb.append(currentRun)
                    sb.append(' ')
                }
                currentRun.clear()
            }
        }
        if (currentRun.length >= 4) sb.append(currentRun)

        var text = sb.toString()
        text = MULTI_SPACE.replace(text, "\n\n")
        text = CONTROL_CHARS.replace(text, "")

        return text.trim()
    }

    private fun Char.isPrintableText(): Boolean {
        return this in ' '..'~' || this == '\n' || this == '\r' || this == '\t' ||
            this.code > 127 // non-ASCII (possible UTF-8)
    }

    // ─── MOBI / AZW3 (PalmDOC) ────────────────────────────────

    private fun parseMobi(file: File): String {
        val raf = RandomAccessFile(file, "r")
        try {
            // PDB header: 78 bytes header, then record list
            if (raf.length() < 78) return "(File too small to be a MOBI)"

            // Read PDB header
            val nameBytes = ByteArray(32)
            raf.readFully(nameBytes)
            val bookName = String(nameBytes, Charsets.ISO_8859_1).trimEnd('\u0000').trim()

            raf.seek(76) // offset to numRecords
            val numRecords = raf.readShort().toInt().and(0xFFFF)
            if (numRecords < 2) return "(MOBI file has no content records)"

            // Read record offsets (8 bytes each: 4 offset + 4 attributes)
            val recordOffsets = LongArray(numRecords)
            for (i in 0 until numRecords) {
                recordOffsets[i] = raf.readInt().toLong().and(0xFFFFFFFFL)
                raf.readInt() // skip attributes
            }

            // Record 0 = MOBI header
            raf.seek(recordOffsets[0])
            val record0 = ByteArray(
                if (numRecords > 1) (recordOffsets[1] - recordOffsets[0]).toInt()
                else (raf.length() - recordOffsets[0]).toInt()
            )
            raf.readFully(record0)

            // PalmDOC header at start of record 0
            val compression = record0[0].toInt().and(0xFF).shl(8) or record0[1].toInt().and(0xFF)
            val textLength = record0[4].toInt().and(0xFF).shl(24) or
                record0[5].toInt().and(0xFF).shl(16) or
                record0[6].toInt().and(0xFF).shl(8) or
                record0[7].toInt().and(0xFF)
            val textRecordCount = record0[8].toInt().and(0xFF).shl(8) or record0[9].toInt().and(0xFF)

            // Extract text records
            val textOut = ByteArrayOutputStream(textLength.coerceAtMost(10_000_000))
            val maxRec = (textRecordCount + 1).coerceAtMost(numRecords)

            for (i in 1 until maxRec) {
                val start = recordOffsets[i]
                val end = if (i + 1 < numRecords) recordOffsets[i + 1] else raf.length()
                val recSize = (end - start).toInt().coerceAtMost(8192)
                if (recSize <= 0) continue

                raf.seek(start)
                val recData = ByteArray(recSize)
                raf.readFully(recData)

                val decompressed = when (compression) {
                    1 -> recData // no compression
                    2 -> palmDocDecompress(recData) // PalmDOC LZ77
                    else -> recData // unknown, try raw
                }
                textOut.write(decompressed)
            }

            val rawText = textOut.toString("UTF-8")

            // MOBI text is usually HTML
            val sb = StringBuilder()
            if (bookName.isNotBlank()) {
                sb.appendLine("# $bookName")
                sb.appendLine()
            }
            sb.append(stripHtmlToMarkdown(rawText))

            return sb.toString().trim().ifBlank { "(Unable to extract text from MOBI)" }
        } catch (e: Exception) {
            return "# MOBI File\n\nUnable to parse this MOBI file: ${e.message}\n\n" +
                "This may be a DRM-protected file. Try converting with " +
                "[Calibre](https://calibre-ebook.com) to EPUB first."
        } finally {
            raf.close()
        }
    }

    /**
     * PalmDOC LZ77 decompression.
     */
    private fun palmDocDecompress(input: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(input.size * 2)
        var i = 0
        while (i < input.size) {
            val byte = input[i].toInt().and(0xFF)
            when {
                byte == 0 -> { output.write(0); i++ }
                byte in 1..8 -> {
                    // Copy next 'byte' bytes literally
                    for (j in 0 until byte) {
                        i++
                        if (i < input.size) output.write(input[i].toInt().and(0xFF))
                    }
                    i++
                }
                byte in 9..0x7F -> { output.write(byte); i++ }
                byte in 0x80..0xBF -> {
                    // Two-byte LZ77 match
                    if (i + 1 >= input.size) { i++; continue }
                    val next = input[i + 1].toInt().and(0xFF)
                    val distance = ((byte shl 8) or next).shr(3).and(0x7FF)
                    val length = (next.and(0x07)) + 3
                    val buf = output.toByteArray()
                    val from = buf.size - distance
                    for (j in 0 until length) {
                        val srcIdx = from + j
                        if (srcIdx >= 0 && srcIdx < buf.size) {
                            output.write(buf[srcIdx].toInt().and(0xFF))
                        }
                    }
                    i += 2
                }
                else -> {
                    // byte >= 0xC0: space + (byte XOR 0x80)
                    output.write(' '.code)
                    output.write(byte xor 0x80)
                    i++
                }
            }
        }
        return output.toByteArray()
    }

    // ─── CHM (Compiled HTML Help) ────────────────────────────

    private fun parseChm(file: File): String {
        // CHM is ITSS (Microsoft's Info-Tech Storage System)
        // Simplified approach: scan for HTML content in the binary
        val bytes = file.readBytes()
        val content = extractHtmlFromBinary(bytes)

        return if (content.isNotBlank()) {
            "# CHM Help File\n\n$content"
        } else {
            val text = extractTextFromBinary(file)
            if (text.isNotBlank()) {
                "# CHM Help File\n\n*Extracted text (layout may not be preserved):*\n\n---\n\n$text"
            } else {
                "# CHM Help File\n\nUnable to extract readable content.\n\n" +
                    "**Tip:** Convert CHM to HTML using `hh.exe -decompile` on Windows, " +
                    "or use an online CHM-to-HTML converter."
            }
        }
    }

    /**
     * Scan binary data for embedded HTML documents and extract text from them.
     */
    private fun extractHtmlFromBinary(bytes: ByteArray): String {
        val text = String(bytes, Charsets.ISO_8859_1)
        val htmlBlocks = mutableListOf<String>()

        // Find all <html>...</html> or <body>...</body> blocks
        val bodyRegex = Regex("<body[^>]*>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE)
        for (match in bodyRegex.findAll(text)) {
            val body = match.groupValues[1]
            val cleaned = stripHtmlToMarkdown(body)
            if (cleaned.length > 20) { // skip tiny fragments
                htmlBlocks.add(cleaned)
            }
        }

        return htmlBlocks.joinToString("\n\n---\n\n").trim()
    }

    // ─── XPS / OXPS ─────────────────────────────────────────

    private fun parseXps(file: File): String {
        val zip = ZipFile(file)
        try {
            val sb = StringBuilder()
            var pageIndex = 0

            // XPS pages are in Documents/1/Pages/N.fpage
            // OXPS pages can also be in Documents/1/Pages/N.fpage
            val pageEntries = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { entry ->
                    val name = entry.name.lowercase()
                    name.endsWith(".fpage") || name.endsWith(".xml")
                }
                .filter { entry ->
                    entry.name.lowercase().contains("page")
                }
                .sortedBy { it.name }
                .toList()

            if (pageEntries.isEmpty()) {
                // Fallback: try to find any XML with text content
                val allXml = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".xml") }
                    .sortedBy { it.name }
                    .toList()

                for (entry in allXml) {
                    val xml = zip.getInputStream(entry).bufferedReader().readText()
                    val text = xml.replace(Regex("<[^>]+>"), " ")
                        .replace(Regex("\\s+"), " ").trim()
                    if (text.length > 20) {
                        sb.appendLine(text)
                        sb.appendLine()
                    }
                }
            } else {
                for (entry in pageEntries) {
                    pageIndex++
                    val xml = zip.getInputStream(entry).bufferedReader().readText()

                    // XPS uses <Glyphs UnicodeString="..."/> for text
                    val glyphTexts = Regex("""UnicodeString\s*=\s*"([^"]+)"""")
                        .findAll(xml)
                        .map { decodeXmlEntities(it.groupValues[1]) }
                        .toList()

                    if (glyphTexts.isNotEmpty()) {
                        sb.appendLine("## Page $pageIndex")
                        sb.appendLine()
                        sb.appendLine(glyphTexts.joinToString(" "))
                        sb.appendLine()
                    }
                }
            }

            return sb.toString().trim().ifBlank { "(Empty XPS document)" }
        } finally {
            zip.close()
        }
    }

    // ─── PDB (Palm Database) ─────────────────────────────────

    private fun parsePdb(file: File): String {
        // PDB files can contain various content types
        // Most common for reading: PalmDOC text
        // Try MOBI parser first (MOBI is a PDB variant)
        val mobiResult = runCatching { parseMobi(file) }.getOrNull()
        if (mobiResult != null && mobiResult.length > 50 &&
            !mobiResult.startsWith("(") && !mobiResult.contains("Unable to parse")
        ) {
            return mobiResult
        }

        // Fallback: extract printable text
        val text = extractTextFromBinary(file)
        return if (text.isNotBlank()) {
            "# PDB Document\n\n$text"
        } else {
            "# PDB Document\n\nUnable to extract readable content from this Palm Database file.\n\n" +
                "**Tip:** Convert to EPUB using [Calibre](https://calibre-ebook.com)."
        }
    }

    // ─── CBZ info (metadata for comics) ──────────────────────

    private fun parseCbzInfo(file: File): String {
        val paths = getCbzImagePaths(file)
        return buildString {
            appendLine("# Comic Book (CBZ)")
            appendLine()
            appendLine("**${paths.size} pages** found in this comic.")
            appendLine()
            appendLine("*Open in the reader to view pages as images.*")
        }
    }

    // ─── HTML stripping helper ───────────────────────────────

    private fun stripHtmlToMarkdown(html: String): String {
        var text = html
        text = HEAD_TAG.replace(text, "")
        text = STYLE_TAG.replace(text, "")
        text = SCRIPT_TAG.replace(text, "")

        for (level in 1..6) {
            val prefix = "#".repeat(level)
            text = H_OPEN[level - 1].replace(text, "\n$prefix ")
            text = H_CLOSE[level - 1].replace(text, "\n")
        }

        text = BR_TAG.replace(text, "\n")
        text = text.replace(Regex("<hr[^>]*>", RegexOption.IGNORE_CASE), "\n\n---\n\n")
        text = CLOSE_P.replace(text, "\n\n")
        text = CLOSE_DIV.replace(text, "\n")
        text = LI_OPEN.replace(text, "\n- ")
        text = BOLD_OPEN.replace(text, "**")
        text = BOLD_CLOSE.replace(text, "**")
        text = ITALIC_OPEN.replace(text, "*")
        text = ITALIC_CLOSE.replace(text, "*")
        text = HTML_TAG.replace(text, "")
        text = decodeHtmlEntities(text)
        text = MULTI_NEWLINE.replace(text, "\n\n")

        return text.trim()
    }

    // ─── Helpers ─────────────────────────────────────────────

    /**
     * Decode XML entities in the correct order.
     * MUST decode &amp; LAST to avoid double-decoding (e.g. &amp;lt; → &lt; → <).
     */
    private fun decodeXmlEntities(text: String): String {
        return decodeHtmlEntities(text)
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&amp;", "&") // MUST be last
            .replace(NUMERIC_ENTITY) { match ->
                val code = match.groupValues[1].toIntOrNull()
                if (code != null) runCatching { String(Character.toChars(code)) }.getOrDefault(match.value)
                else match.value
            }
            .replace(HEX_ENTITY) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                if (code != null) runCatching { String(Character.toChars(code)) }.getOrDefault(match.value)
                else match.value
            }
    }
}
