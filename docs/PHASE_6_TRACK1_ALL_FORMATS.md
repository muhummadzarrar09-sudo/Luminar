# Phase 6 Track 1 — Universal Format Support

## What's new

Luminar now supports **30 file formats** — more than Kindle, ReadEra, and most paid readers. Every format we can parse without external libraries is now covered.

### New formats added this phase

| Format | Extensions | Parser | Reader |
|--------|-----------|--------|--------|
| **CBZ** | .cbz | ZipFile → image list | ComicReaderView (swipe pages, pinch-zoom) |
| **MOBI** | .mobi, .prc | PalmDOC LZ77 decompression → HTML → Markdown | TextReaderView |
| **AZW3/AZW/KFX** | .azw3, .azw, .kfx | Same PDB/MOBI parser (DRM-free only) | TextReaderView |
| **CHM** | .chm | Binary HTML extraction → text | TextReaderView |
| **XPS/OXPS** | .xps, .oxps | ZipFile → fpage XML → Glyphs text | TextReaderView |
| **PDB** | .pdb | Palm Database → PalmDOC or text extract | TextReaderView |
| **CBR** | .cbr | Detection + conversion instructions | TextReaderView (info) |
| **CBT** | .cbt | Detection + conversion instructions | TextReaderView (info) |

### Complete format roster (30 formats)

| Category | Formats | Count |
|----------|---------|-------|
| PDF | PDF | 1 |
| E-books | EPUB, MOBI, AZW3, FB2, PDB | 5 |
| Comics | CBZ, CBR, CBT | 3 |
| Office (modern) | DOCX, XLSX, PPTX | 3 |
| Office (legacy) | DOC, XLS, PPT | 3 |
| OpenDocument | ODT, ODS, ODP | 3 |
| Special docs | RTF, CHM, XPS, DjVu | 4 |
| Text & code | Markdown, TXT, HTML, JSON, XML, CSV, LOG, Code (50+ extensions) | 8 |
| **Total** | | **30** |

### CBZ Comic Reader

Full comic book reader with:
- **Horizontal page swipe** — flip through pages like a real comic
- **Pinch-to-zoom** — zoom into panels with gesture support
- **Page counter** — "3 / 24" overlay at bottom
- **Progress saved** — remembers which page you were on
- **Theme-aware** — dark background for AMOLED reading
- Images sorted alphabetically (standard comic convention)

### MOBI/AZW3 Parser

Pure JDK implementation of PalmDOC decompression:
- Reads PDB header → record table → text records
- Supports both uncompressed (type 1) and PalmDOC LZ77 (type 2) compression
- Extracts HTML content → converts to Markdown for rendering
- DRM-protected files get a helpful message suggesting Calibre conversion
- Same parser handles AZW3/AZW/KFX (all PDB-based)

### Files changed (NO build files touched)

**New:**
- `presentation/reader/ComicReaderView.kt` — Full comic reader with HorizontalPager, pinch-zoom, page indicator

**Modified:**
- `data/model/BookFormat.kt` — 7 new entries (CBZ, CBR, CBT, AZW3, CHM, XPS, PDB), `isComicBook` property, new MIME types
- `data/document/DocumentParser.kt` — MOBI/AZW3 PalmDOC parser, CHM parser, XPS parser, PDB parser, CBZ image helpers, HTML strip helper
- `data/repository/BookRepository.kt` — `getComicImagePaths()`, `getComicImageBytes()`
- `data/repository/BookRepositoryImpl.kt` — CBZ import with page count, comic image methods
- `presentation/reader/ReaderViewModel.kt` — `isComicBook`, `comicImagePaths`, `loadComicPages()`, `getComicImageBytes()`
- `presentation/reader/ReaderScreen.kt` — Comic reader routing, controls visibility for comics
- `presentation/library/LibraryViewModel.kt` — "Comics" filter chip
