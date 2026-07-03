# Luminar Reader — Phase 1: Multi-format text file support

## What's new

The app now supports importing and reading **all common text-based file formats** alongside PDFs.

### Supported formats

| Format | Extensions | Reader mode |
|--------|-----------|-------------|
| PDF | `.pdf` | Page-flip (existing) |
| Markdown | `.md`, `.markdown`, `.mkd` | Scroll — rendered with headings, bold, italic, code blocks, lists, quotes, links |
| Plain text | `.txt`, `.text` | Scroll — paragraph-grouped |
| HTML | `.html`, `.htm`, `.xhtml` | Scroll — shown as text |
| JSON | `.json`, `.jsonl` | Scroll — monospace code view |
| XML | `.xml`, `.svg` | Scroll — monospace code view |
| CSV | `.csv`, `.tsv` | Scroll — monospace code view |
| Log | `.log` | Scroll — paragraph-grouped |
| Code | `.kt`, `.java`, `.py`, `.js`, `.ts`, `.go`, `.rs`, `.swift`, `.dart`, `.c`, `.cpp`, `.sh`, `.sql`, `.yaml`, `.toml`, `.css`, `.php`, `.rb`, `.scala`, etc. (50+ extensions) | Scroll — monospace code view |

### How it works

1. **Import button** — Same FAB, now opens a file picker that accepts all supported types (not just PDF)
2. **Library** — Books show a format badge (e.g. "MARKDOWN", "CODE") in the top-right corner of the cover
3. **Reader** — Text files open in a vertical-scroll reader with:
   - Full Markdown rendering (headings, bold, italic, strikethrough, inline code, fenced code blocks, blockquotes, bullet/numbered lists, links, horizontal rules)
   - Theme-aware colors (AMOLED dark, Sepia, Light)
   - Code blocks get themed monospace background
   - Text is selectable
   - Scroll position is saved and restored
4. **Controls** — Tap to show top bar (back, title, format label, theme toggle) + bottom bar (format info for text, page slider for PDF)
5. **Intent filters** — App can receive `text/*` and `application/json` from "Open with" in other apps

### Files changed (NO build files touched)

**New files:**
- `data/model/ScrollMode.kt` — Scroll/Pages enum
- `presentation/reader/TextReaderView.kt` — Full Markdown parser + text renderer

**Modified files:**
- `data/model/BookFormat.kt` — Expanded enum with all formats, `fromExtension()`, `fromMimeType()`, `isTextBased`
- `data/local/db/Converters.kt` — Updated default fallback
- `data/repository/BookRepository.kt` — Added `importFile()`, `readTextContent()`
- `data/repository/BookRepositoryImpl.kt` — Generic file import, text content reading, line counting
- `domain/usecase/ImportBookUseCase.kt` — Now calls `importFile()` instead of `importPdf()`
- `presentation/library/LibraryViewModel.kt` — `ImportFile` event, generalized import
- `presentation/library/LibraryScreen.kt` — `OpenDocument` picker, format badges, updated labels
- `presentation/reader/ReaderViewModel.kt` — Text content loading, scroll position tracking, `ScrollPositionChanged` event
- `presentation/reader/ReaderScreen.kt` — Branches between `PdfReaderView` and `TextReaderView`, format info in controls
- `AndroidManifest.xml` — Added intent filters for `text/*` and `application/json`

### What's NOT changed
- All Gradle/build files untouched
- Room DB version stays at 1 (no schema change — `BookFormat` is stored as a string enum name, new values are backward compatible)
- PDF reading unchanged
- Theme system unchanged
