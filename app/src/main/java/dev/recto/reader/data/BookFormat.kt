package dev.recto.reader.data

/**
 * Formats Recto can recognise.
 *
 * Phase 1 only *reads* EPUB. The rest are detected and imported so the library
 * is honest about what you own, and each is marked with whether a reader
 * exists yet. Phase 3 lights the remaining ones up via the convert-to-EPUB
 * pipeline described in BRAINSTORM.md section 3.
 */
enum class BookFormat(
    val label: String,
    val extensions: List<String>,
    val mimeTypes: List<String>,
    /** Recto has a reader for this format. */
    val readable: Boolean,
    /**
     * The format yields plain text we can search and look words up in.
     *
     * False for PDF: pages are rendered as images, exactly as Kindle does,
     * because a PDF is a fixed layout and reflowing extracted text scrambles
     * columns, tables and equations. Readable, but not searchable.
     */
    val hasText: Boolean = readable
) {
    EPUB("EPUB", listOf("epub"), listOf("application/epub+zip"), readable = true),

    PDF("PDF", listOf("pdf"), listOf("application/pdf"), readable = true, hasText = false),

    TXT("Text", listOf("txt", "text"), listOf("text/plain"), readable = true),

    MARKDOWN("Markdown", listOf("md", "markdown"), listOf("text/markdown"), readable = true),

    HTML("HTML", listOf("html", "htm", "xhtml"), listOf("text/html"), readable = true),

    DOCX(
        "Word",
        listOf("docx"),
        listOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        readable = false
    ),

    MOBI("MOBI", listOf("mobi", "azw", "azw3", "prc"), listOf("application/x-mobipocket-ebook"), readable = false),

    FB2("FictionBook", listOf("fb2"), listOf("application/x-fictionbook+xml"), readable = false),

    RTF("RTF", listOf("rtf"), listOf("application/rtf"), readable = false),

    CBZ("Comic", listOf("cbz", "cbr"), listOf("application/vnd.comicbook+zip"), readable = false),

    UNKNOWN("Unknown", emptyList(), emptyList(), readable = false);

    companion object {

        /** Every MIME type we advertise in the file picker and share sheet. */
        val pickerMimeTypes: Array<String> =
            entries.flatMap { it.mimeTypes }.plus("application/octet-stream").toTypedArray()

        fun fromFileName(name: String?): BookFormat {
            val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
            if (ext.isEmpty()) return UNKNOWN
            return entries.firstOrNull { ext in it.extensions } ?: UNKNOWN
        }

        fun fromMimeType(mime: String?): BookFormat {
            if (mime.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { mime in it.mimeTypes } ?: UNKNOWN
        }

        /**
         * Filename wins over MIME type: content resolvers hand back
         * application/octet-stream distressingly often.
         */
        fun detect(name: String?, mime: String?): BookFormat {
            val byName = fromFileName(name)
            return if (byName != UNKNOWN) byName else fromMimeType(mime)
        }
    }
}
