// app/src/main/java/com/luminar/reader/data/model/BookFormat.kt
package com.luminar.reader.data.model

enum class BookFormat(val displayName: String) {
    PDF("PDF"),
    EPUB("EPUB"),
    MARKDOWN("Markdown"),
    TXT("Text"),
    HTML("HTML"),
    XML("XML"),
    JSON("JSON"),
    CSV("CSV"),
    LOG("Log"),
    CODE("Code");

    val isTextBased: Boolean
        get() = this != PDF && this != EPUB

    companion object {
        fun fromExtension(extension: String): BookFormat {
            return when (extension.lowercase()) {
                "pdf" -> PDF
                "epub" -> EPUB
                "md", "markdown", "mkd", "mkdn" -> MARKDOWN
                "txt", "text" -> TXT
                "htm", "html", "xhtml" -> HTML
                "xml", "svg", "plist" -> XML
                "json", "jsonl", "geojson" -> JSON
                "csv", "tsv" -> CSV
                "log" -> LOG
                "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx",
                "c", "cpp", "h", "hpp", "cs", "go", "rs", "rb",
                "swift", "dart", "lua", "sh", "bash", "zsh",
                "bat", "ps1", "sql", "r", "m", "mm",
                "yaml", "yml", "toml", "ini", "cfg", "conf",
                "properties", "env", "gradle",
                "css", "scss", "sass", "less",
                "php", "pl", "pm", "scala", "clj", "ex", "exs",
                "hs", "elm", "ml", "fs", "fsx",
                "makefile", "cmake", "dockerfile",
                "gitignore", "gitattributes", "editorconfig" -> CODE
                else -> TXT
            }
        }

        fun fromMimeType(mimeType: String): BookFormat {
            return when {
                mimeType == "application/pdf" -> PDF
                mimeType == "application/epub+zip" -> EPUB
                mimeType == "text/markdown" -> MARKDOWN
                mimeType == "text/html" || mimeType == "application/xhtml+xml" -> HTML
                mimeType == "text/xml" || mimeType == "application/xml" -> XML
                mimeType == "application/json" -> JSON
                mimeType == "text/csv" -> CSV
                mimeType.startsWith("text/") -> TXT
                else -> TXT
            }
        }

        val IMPORTABLE_MIME_TYPES = arrayOf(
            "application/pdf",
            "application/epub+zip",
            "text/*",
            "application/json",
            "application/xml",
            "application/xhtml+xml",
            "*/*"
        )
    }
}
