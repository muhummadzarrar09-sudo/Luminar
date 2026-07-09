// app/src/main/java/com/luminar/reader/data/model/BookFormat.kt
package com.luminar.reader.data.model

enum class BookFormat(val displayName: String) {
    PDF("PDF"),
    EPUB("EPUB"),
    DOCX("Word"),
    XLSX("Excel"),
    PPTX("PowerPoint"),
    ODT("Writer"),
    ODS("Calc"),
    ODP("Impress"),
    RTF("RTF"),
    FB2("FB2"),
    DOC("Word 97"),
    XLS("Excel 97"),
    PPT("PPT 97"),
    MOBI("MOBI"),
    AZW3("Kindle"),
    DJVU("DjVu"),
    CBZ("Comic"),
    CBR("Comic RAR"),
    CBT("Comic TAR"),
    CHM("Help"),
    XPS("XPS"),
    PDB("Palm"),
    MARKDOWN("Markdown"),
    TXT("Text"),
    HTML("HTML"),
    XML("XML"),
    JSON("JSON"),
    CSV("CSV"),
    LOG("Log"),
    CODE("Code");

    val isTextBased: Boolean
        get() = this != PDF && this != EPUB && !isDocumentFormat && !isComicBook

    val isDocumentFormat: Boolean
        get() = this in listOf(DOCX, XLSX, PPTX, ODT, ODS, ODP, RTF, FB2,
            DOC, XLS, PPT, MOBI, AZW3, DJVU, CHM, XPS, PDB)

    val isComicBook: Boolean
        get() = this in listOf(CBZ, CBR, CBT)

    val renderingMode: RenderingMode
        get() = when (this) {
            DOCX, ODT, RTF, DOC -> RenderingMode.DOCUMENT
            XLSX, ODS, CSV -> RenderingMode.SPREADSHEET
            PPTX, ODP, PPT -> RenderingMode.PRESENTATION
            EPUB, MOBI, AZW3, FB2, PDB -> RenderingMode.EBOOK
            MARKDOWN -> RenderingMode.MARKDOWN
            CODE, JSON, XML, HTML -> RenderingMode.CODE
            CBZ, CBR, CBT -> RenderingMode.COMIC
            PDF -> RenderingMode.PDF
            else -> RenderingMode.PLAIN_TEXT
        }

    companion object {
        fun fromExtension(extension: String): BookFormat {
            return when (extension.lowercase()) {
                "pdf" -> PDF
                "epub" -> EPUB
                "docx" -> DOCX
                "xlsx" -> XLSX
                "pptx" -> PPTX
                "odt" -> ODT
                "ods" -> ODS
                "odp" -> ODP
                "rtf" -> RTF
                "fb2" -> FB2
                "doc" -> DOC
                "xls" -> XLS
                "ppt" -> PPT
                "mobi", "prc" -> MOBI
                "azw3", "azw", "kfx" -> AZW3
                "djvu", "djv" -> DJVU
                "cbz" -> CBZ
                "cbr" -> CBR
                "cbt" -> CBT
                "chm" -> CHM
                "xps", "oxps" -> XPS
                "pdb" -> PDB
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
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCX
                mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> XLSX
                mimeType == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> PPTX
                mimeType == "application/vnd.oasis.opendocument.text" -> ODT
                mimeType == "application/vnd.oasis.opendocument.spreadsheet" -> ODS
                mimeType == "application/vnd.oasis.opendocument.presentation" -> ODP
                mimeType == "application/rtf" || mimeType == "text/rtf" -> RTF
                mimeType == "application/msword" -> DOC
                mimeType == "application/vnd.ms-excel" -> XLS
                mimeType == "application/vnd.ms-powerpoint" -> PPT
                mimeType == "application/x-mobipocket-ebook" -> MOBI
                mimeType == "application/vnd.amazon.mobi8-ebook" -> AZW3
                mimeType == "application/vnd.comicbook+zip" -> CBZ
                mimeType == "application/vnd.comicbook-rar" -> CBR
                mimeType == "application/x-chm" || mimeType == "application/vnd.ms-htmlhelp" -> CHM
                mimeType == "application/oxps" || mimeType == "application/vnd.ms-xpsdocument" -> XPS
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
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            "application/rtf",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/x-mobipocket-ebook",
            "application/vnd.comicbook+zip",
            "application/x-chm",
            "application/vnd.ms-htmlhelp",
            "application/oxps",
            "application/vnd.ms-xpsdocument",
            "text/*",
            "application/json",
            "application/xml",
            "application/xhtml+xml",
            "application/octet-stream"
        )
    }
}
