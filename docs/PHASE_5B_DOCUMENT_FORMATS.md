# Phase 5B — Universal Document Format Support

## What's new

Luminar now opens virtually every document format — Office, OpenDocument, RTF, FB2, and legacy formats. All parsed to readable text with zero external libraries.

### Supported formats (total: 23+)

| Format | Extensions | Parser | Quality |
|--------|-----------|--------|---------|
| **PDF** | .pdf | Android PdfRenderer | ★★★★★ Native |
| **EPUB** | .epub | ZipFile → OPF → XHTML | ★★★★★ Full |
| **DOCX** | .docx | ZipFile → word/document.xml | ★★★★☆ Headings + text |
| **XLSX** | .xlsx | ZipFile → sharedStrings + sheets | ★★★★☆ Multi-sheet tables |
| **PPTX** | .pptx | ZipFile → slides XML | ★★★★☆ Per-slide text |
| **ODT** | .odt | ZipFile → content.xml | ★★★★☆ Full text + headings |
| **ODS** | .ods | ZipFile → content.xml | ★★★☆☆ Tables |
| **ODP** | .odp | ZipFile → content.xml | ★★★☆☆ Slide text |
| **RTF** | .rtf | Custom RTF parser | ★★★★☆ Full text extraction |
| **FB2** | .fb2 | XML body extraction | ★★★★☆ Chapters + emphasis |
| **DOC** | .doc | Binary text extraction | ★★☆☆☆ Best effort |
| **XLS** | .xls | Binary text extraction | ★★☆☆☆ Best effort |
| **PPT** | .ppt | Binary text extraction | ★★☆☆☆ Best effort |
| **MOBI** | .mobi | Info message | ★☆☆☆☆ Suggests EPUB convert |
| **DjVu** | .djvu | Info message | ★☆☆☆☆ Suggests PDF convert |
| **Markdown** | .md | Full Markdown renderer | ★★★★★ |
| **Text** | .txt, .log | Paragraph grouping | ★★★★★ |
| **Code** | 50+ extensions | Monospace code view | ★★★★★ |
| **HTML** | .html, .htm | Text extraction | ★★★★☆ |
| **JSON/XML/CSV** | .json, .xml, .csv | Monospace view | ★★★★★ |

### Architecture

`DocumentParser` is a zero-dependency parser using only:
- `java.util.zip.ZipFile` — for DOCX, XLSX, PPTX, ODT, ODS, ODP (all ZIP-based)
- `Regex` — for XML tag extraction
- `String` operations — for RTF control word parsing

Each parser converts to **Markdown-style text** (with `#` headings, `**bold**`, etc.), which the existing TextReaderView renders with full formatting.

**Legacy .doc/.xls/.ppt** files get best-effort binary text extraction — scanning for printable character runs. The user gets a note suggesting conversion to the modern format.

### Files changed

**New:**
- `data/document/DocumentParser.kt` — 350-line parser supporting DOCX, XLSX, PPTX, ODT, ODS, ODP, RTF, FB2, DOC, XLS, PPT, MOBI, DJVU

**Modified:**
- `BookFormat.kt` — 13 new enum entries, `isDocumentFormat` property, new MIME types
- `BookRepository.kt` — Added `readDocumentContent()` method
- `BookRepositoryImpl.kt` — Document import + reading, injected `DocumentParser`
- `ReaderViewModel.kt` — `isDocument`, `usesTextRenderer` properties, `loadDocumentContent()`, unified progress handling
- `ReaderScreen.kt` — Uses `usesTextRenderer` for all non-PDF rendering decisions
- `LibraryViewModel.kt` — "Docs" filter chip with document format counting
- `AndroidManifest.xml` — Intent filters for all Office/OD MIME types
