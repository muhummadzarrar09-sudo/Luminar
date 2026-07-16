# Luminar Reader — Consolidated Roadmap (Phases 7–11)

*Compiled on: July 14, 2026*
*Objective: Unifying the Strategic Plan, UI/UX Repairs, and Document Formatting PRDs into a clear, sequential development pipeline.*

---

## Roadmap Overview

This consolidated roadmap maps all of our previous audits and product requirement documents into **five sequential development phases (Phases 7 through 11)**. 

By executing this progressive, step-by-step pipeline, you will systematically resolve your hidden engine bugs, eliminate UI main-thread jank, preserve complex document layouts, and deliver world-class premium features like background TTS and decentralized sync.

```
  ┌──────────────────────────────────────────────────────────────┐
  │  PHASE 7: Critical UI/UX Repair & Chapter Navigation         │
  └──────────────────────────────┬───────────────────────────────┘
                                 v
  ┌──────────────────────────────────────────────────────────────┐
  │  PHASE 8: High-Performance Comic Engine & Layout Preservation│
  └──────────────────────────────┬───────────────────────────────┘
                                 v
  ┌──────────────────────────────────────────────────────────────┐
  │  PHASE 9: Hybrid HTML Reader & Theme-Aware Visuals           │
  └──────────────────────────────┬───────────────────────────────┘
                                 v
  ┌──────────────────────────────────────────────────────────────┐
  │  PHASE 10: Multilingual Decodings & SQLite FTS5 Search        │
  └──────────────────────────────┬───────────────────────────────┘
                                 v
  ┌──────────────────────────────────────────────────────────────┐
  │  PHASE 11: Foreground Services (Background TTS & Cloud Sync) │
  └──────────────────────────────────────────────────────────────┘
```

---

## 📅 Phase 7: Critical UI/UX Repair & Chapter Navigation
*Objective: Revive broken/placeholder functionalities and restore basic, intuitive reader utilities.*

| Parameter | Specification |
|---|---|
| **Effort / Complexity** | Low-Medium |
| **User Impact** | Extremely High (Resolves the most obvious broken-feeling bugs) |
| **Files to Edit** | `ReaderScreen.kt`, `ReaderViewModel.kt`, `TextReaderView.kt` |

### 🛠️ Execution Plan:
1.  **Functional Chapter Jumping (TOC Revival):**
    *   Introduce `ReaderEvent.GoToChapter(chapterIndex: Int)` inside `ReaderViewModel.kt`.
    *   When clicked, have the ViewModel scan the list of parsed `blockTexts` for the Level-1 Markdown heading (`# Chapter Title`) corresponding to that chapter's title.
    *   Update `scrollToBlockIndex` with that block's index. The existing `LaunchedEffect` in `TextReaderView.kt` will automatically animate the scroll to the target block.
2.  **Live Progress Tracking & Active Chapter Headers:**
    *   Currently, the top header bar displays "Ch. 1 of 12" and the bottom progress bar stays stuck at `0%` forever as the user scrolls through any text-based format.
    *   In the ViewModel, scan the parsed blocks to map which block index ranges correspond to which chapter indices (e.g., Chapter 1: blocks 0–40, Chapter 2: blocks 41–95).
    *   In `onScrollPositionChanged(firstVisibleBlockIndex)`, check which chapter range the block index falls into and dynamically update `uiState.currentPage` and `uiState.currentChapterIndex`, immediately animating your gold progress bar in real-time as the user scrolls.
3.  **PDF Pinch-To-Zoom Gestures:**
    *   Configure `.enableDoubletap(true)` and `.enableAntialiasing(true)` inside the native configuration block of the `AndroidView` wrapper in `PdfReaderView` (inside `ReaderScreen.kt`), allowing users to zoom in and read small text.

---

## 📅 Phase 8: High-Performance Comic Engine & Layout Preservation
*Objective: Eliminate scroll jank in Comic Mode and preserve complex document layouts (page breaks, alignments, dividers).*

| Parameter | Specification |
|---|---|
| **Effort / Complexity** | Medium |
| **User Impact** | Very High (Buttery-smooth scrolling and premium document formatting) |
| **Files to Edit** | `ComicReaderView.kt`, `DocumentParser.kt`, `EpubParser.kt`, `TextReaderView.kt` |

### 🛠️ Execution Plan:
1.  **Asynchronous Comic Page Loading:**
    *   Remove synchronous file unzipping and `BitmapFactory.decodeByteArray` from the Main (UI) thread inside `ComicPage`'s `remember` block.
    *   Shift decoding tasks entirely to background threads (`Dispatchers.IO`) utilizing a Compose `produceState` or background `LaunchedEffect`.
    *   Render a subtle shimmer loading block while the image is loading, keeping page swipes at 60–120 FPS.
2.  **DOCX Page Breaks and Dividers:**
    *   Update `docxXmlToText` inside `DocumentParser.kt` to scan for page break markers (`<w:br w:type="page"/>` or `<w:lastRenderedPageBreak/>`) and section separators.
    *   When matched, append a Markdown divider `\n\n---\n\n`. This parses directly as a native `TextBlock.Divider` in `TextReaderView`, rendering as a beautiful Material 3 `HorizontalDivider` line.
3.  **Horizontal Rule Scene Splits for EPUB & MOBI:**
    *   Replace `<hr[^>]*>` tags inside `stripHtmlToMarkdown()` and `htmlToPlainText()` with `\n\n---\n\n` instead of stripping them, preserving crucial author perspective/scene shifts.
4.  **Text Alignment & Blockquote Preservation:**
    *   Detect paragraph alignment markers `<w:jc w:val="center|right"/>` and map them to Compose `TextAlign.Center` and `TextAlign.End` inside `TextReaderView`.
    *   Detect Word indents `<w:ind w:left="X"/>` and format those runs as Markdown Blockquotes (`> Quote text`), triggering vertical Gold accent bars.

---

## 📅 Phase 9: Hybrid HTML Reader & Theme-Aware Visuals
*Objective: Deliver a dual-mode HTML view for power users and establish unified Light/Dark mode consistency.*

| Parameter | Specification |
|---|---|
| **Effort / Complexity** | Low-Medium |
| **User Impact** | High (Adds an extremely unique developer utility and visual polish) |
| **Files to Edit** | `ReaderScreen.kt`, `ReaderViewModel.kt`, `LibraryScreen.kt`, `TextReaderView.kt` |

### 🛠️ Execution Plan:
1.  **HTML Mode Switcher UI:**
    *   Add a toggle button inside the top toolbar of `ReaderScreen.kt` (only visible when `book.format == BookFormat.HTML`).
    *   **Visual Icons:** If Preview is active, display a terminal icon 💻 ("Switch to Code"). If Code is active, display a globe icon 🌐 ("Switch to Preview").
2.  **HTML View State Engine:**
    *   Introduce `ReaderEvent.ToggleHtmlViewMode` which toggles a `isHtmlPreviewMode: Boolean` flag in the state.
    *   **If True (Preview Mode):** Load the file, route it through `stripHtmlToMarkdown()`, and set `renderFormat = BookFormat.MARKDOWN`.
    *   **If False (Code Mode):** Load the file as a raw text string and set `renderFormat = BookFormat.XML`, rendering it in a syntax-highlighted dark monospace container with line numbers.
3.  **Theme-Aware Cover Placeholders:**
    *   Refactor `CoverPlaceholder` in `LibraryScreen.kt` to be theme-aware.
    *   In Light Mode, swap the AMOLED pitch-black and dark-gold gradient for a soft, light gold-tinted paper background and dark-charcoal text, preserving Light Mode's visual weight.

---

## 📅 Phase 10: Multilingual Decodings & SQLite FTS5 Search
*Objective: Fix non-English rendering bugs and upgrade search indexing performance.*

| Parameter | Specification |
|---|---|
| **Effort / Complexity** | Low-Medium |
| **User Impact** | Critical (Unlocks global multilingual support and fast relevance search rankings) |
| **Files to Edit** | `DocumentParser.kt`, `AppDatabase.kt` (Room database), `BookDao.kt` |

### 🛠️ Execution Plan:
1.  **Unified Multilingual Entity Decoders:**
    *   Currently, `decodeXmlEntities` is crippled and ignores decimal and hex entities like `&#20013;` (Chinese characters), mathematical symbols, and curly quotes, rendering non-English files unreadable.
    *   Swap out `decodeXmlEntities` inside `DocumentParser.kt`. Force DOCX, XLSX, and PPTX parsers to use your robust, regex-backed `decodeHtmlEntities` (which already decodes decimal and hexadecimal entities perfectly).
2.  **Non-English DOCX Heading Support:**
    *   Your DOCX parser only detects headings if they contain the English style name `"Heading"`.
    *   Modify your heading style detection to check the underlying Word heading layout hierarchy index properties (`w:outlineLvl`) instead of localized name strings, ensuring headings render perfectly on French, German, or Spanish Word documents.
3.  **SQLite FTS5 Database Migration:**
    *   FTS5 is natively supported on 100% of devices running Android API 26+ (your minimum SDK).
    *   Safely migrate your Room search database virtual tables from FTS4 to FTS5.
    *   Unlock the **BM25 Relevance Ranking algorithm** so that in-book search matches are sorted by relevance and matches density, rather than raw sequential order.

---

## 📅 Phase 11: Foreground Services (Background TTS & Cloud Sync)
*Objective: Transition TTS to lockscreen-standard background playing and implement encrypted personal sync.*

| Parameter | Specification |
|---|---|
| **Effort / Complexity** | High |
| **User Impact** | Extremely High (Delivers massive, commercial-grade premium features) |
| **Files to Edit** | `TtsController.kt`, `AndroidManifest.xml`, `TtsService.kt` (New), `SyncRepository.kt` (New) |

### 🛠️ Execution Plan:
1.  **TTS Background Foreground Service:**
    *   Bind your singleton `TtsController` actions to a background-running Android `Foreground Service` (`TtsService`).
    *   Create a persistent notification utilizing a `MediaStyle` structure containing Play, Pause, Skip, and Stop actions. This prevents the Android OS from aggressively killing the reading voice when the screen turns off or the user switches apps.
2.  **Decentralized WebDAV / personal Cloud Sync:**
    *   Build a personal backup and sync repository.
    *   Compile bookmarks, reading progress, and custom tags into a highly compressed, encrypted JSON sync file (`luminar_sync.json.gz`).
    *   Let the user connect their own storage provider—**Google Drive, Dropbox, or any custom WebDAV/Nextcloud server**—and sync reading progress securely without hosting any proprietary cloud servers.
