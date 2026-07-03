# Phase 5 — EPUB Support (Final Phase)

## What's new

Full EPUB file support — import, parse, and read `.epub` books with chapter navigation, cover extraction, and the complete reader experience (search, font scaling, themes, progress saving).

### Features

#### 📚 EPUB import
- Tap the import FAB → pick any `.epub` file
- Automatically extracts:
  - **Book title** from OPF metadata (`<dc:title>`)
  - **Cover image** via three strategies: `<meta name="cover">`, `properties="cover-image"`, or any manifest item with "cover" in its ID
  - **Chapter count** from the OPF spine

#### 📖 EPUB reading
- Opens in the same reader as text files — vertical scroll with full formatting
- Each chapter displays as a **H1 heading** followed by its content
- Chapters separated by horizontal dividers (`---`)
- HTML → Markdown conversion preserves:
  - **Headings** (H1–H6 → `#` through `######`)
  - **Bold** (`<b>`, `<strong>` → `**text**`)
  - **Italic** (`<i>`, `<em>` → `*text*`)
  - **Lists** (`<li>` → `- item`)
  - **Paragraphs** (proper spacing)
  - **HTML entities** (all numeric + named entities decoded)
- Content rendered through the existing Markdown rendering pipeline — so all the styling, themes, and font scaling from Phase 1–2 apply automatically

#### 🔍 Search in EPUB
- Works identically to other text formats — search bar, gold highlighting, ▲▼ navigation, match counter
- Searches across ALL chapters simultaneously

#### 🎨 Full reader features
All existing reader features work with EPUB:
- ✅ AMOLED Dark / Sepia / Light themes
- ✅ Font size (Tiny → Massive)
- ✅ Word & character count
- ✅ Scroll position saved & restored
- ✅ Immersive mode
- ✅ Keep screen on
- ✅ Volume button page turning

#### 🏷️ Library integration
- EPUB books show cover art in the library grid
- "EPUB" format badge on cards without covers
- Library filter chip: **EPUB (3)** with count
- Sort by title/date/size works for EPUBs
- File size displayed on cards

### Architecture

**No external libraries** — EPUB parsing uses only JDK's `java.util.zip.ZipFile`:

```
.epub (ZIP) → META-INF/container.xml → find OPF path
           → content.opf → parse manifest (id→href) + spine (reading order)
           → for each spine entry: extract XHTML → strip HTML → inject Markdown markers
           → List<EpubChapter(title, textContent)>
           → concatenate as Markdown → render via TextReaderView
```

The key insight: `EpubParser.htmlToPlainText()` converts XHTML to pseudo-Markdown by replacing HTML tags with Markdown equivalents (`<h1>` → `#`, `<strong>` → `**`, `<li>` → `-`, etc.), so the existing Markdown renderer handles all formatting.

### Files changed (NO build files touched)

**New files:**
- `data/epub/EpubParser.kt` — `EpubParser` singleton: ZIP parsing, OPF reading, manifest/spine extraction, cover image extraction (3 strategies), `htmlToPlainText()` with HTML→Markdown conversion, entity decoding
- `presentation/reader/EpubReaderView.kt` — `epubChaptersToMarkdown()` helper that joins chapters with H1 titles and dividers

**Modified:**
- `data/model/BookFormat.kt` — Added `application/epub+zip` to `IMPORTABLE_MIME_TYPES`
- `data/repository/BookRepository.kt` — Added `readEpubChapters()` method
- `data/repository/BookRepositoryImpl.kt` — EPUB import (parse→extract cover→store), `readEpubChapters()` implementation, injected `EpubParser`
- `presentation/reader/ReaderViewModel.kt` — `epubChapters` + `isEpub` in state, `loadEpubChapters()`, EPUB progress saving, search support
- `presentation/reader/ReaderScreen.kt` — EPUB routing (renders as Markdown through TextReaderView), format label, search/font controls enabled for EPUB
- `presentation/library/LibraryViewModel.kt` — `FormatFilter.EPUB` added to filter chips and counts
- `AndroidManifest.xml` — Added `application/epub+zip` intent filter

**NOT changed:**
- Build files, database schema, PDF reader, navigation, theme, settings
