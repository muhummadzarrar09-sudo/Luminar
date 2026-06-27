# Luminar — Phase 2A Sprint Notes & Progress

## Executive Summary
Phase 2A expands Luminar from a standalone PDF reader into a dual-format reading suite supporting both PDF (`application/pdf`) and reflowable EPUB (`application/epub+zip`) documents. Furthermore, it introduces universal document navigation via an interactive Table of Contents (TOC) sidebar drawer.

---

## Completed Features

### Feature 1: EPUB Support via Readium2
- **What Was Built**: 
  - Dual-format file import pipeline accepting both PDFs and EPUBs via `ImportBookUseCase` and updated MIME type filters in `LibraryScreen`.
  - `EpubBookLoader` singleton service utilizing Readium Streamer (`org.readium.r2.streamer.Streamer`) to parse `.epub` archives from internal storage, extract publication metadata (title, author), and render cover thumbnail images.
  - Dedicated `EpubReaderScreen` and `EpubReaderViewModel` providing responsive reader overlays, theme switching (`DARK_AMOLED`, `SEPIA`, `LIGHT`), hardware volume button page turns, and screen keep-alive window flags.
  - Reading progress location tracking via EPUB Canonical Fragment Identifiers (CFIs).
- **Stack & Database Changes**:
  - Added Readium Kotlin Toolkit `3.0.0` (`readium-shared`, `readium-streamer`, `readium-navigator`).
  - **Room Migration v1 → v2**: Added nullable `epubCfi TEXT DEFAULT NULL` column to `reading_progress` table.

### Feature 2: Table of Contents (TOC) Sidebar Drawer
- **What Was Built**:
  - Extracted document chapter outlines and bookmarks during import, mapping them into standardized relational database entities.
  - Created `TocDrawer` modal component wrapped in Compose `ModalNavigationDrawer`. Triggered via left-to-right swipe gestures across both PDF and EPUB reader composables.
  - Displays hierarchically indented chapter entries (`level * 16.dp`). Tapping an item instantly routes PDFView (`jumpTo()`) or Readium Navigator to the targeted location. Includes graceful empty states when documents contain no outline data.
- **Stack & Database Changes**:
  - **Room Migration v2 → v3**: Created `book_toc` table with foreign key cascading deletion bound to `books.id`.

### Feature 3: Background Text Indexing & Fast Search
- **What Was Built**:
  - Asynchronous document chunk text extraction and database indexing via WorkManager (`BookAnalysisWorker`). Automatically enqueued immediately after successful PDF or EPUB file import.
  - Added `indexingProgress` field (`0-100`) to `Book` model to track extraction status.
  - Built standalone `SearchScreen` and `SearchViewModel` supporting dual search modes ("This Book" scoped tab vs "All Books" global tab) with 300ms query debouncing.
  - Implemented bold search keyword match highlighting inside excerpt previews (`buildHighlightedString()`).
- **Stack & Database Changes**:
  - **Room Migration v3 → v4**: Added `indexingProgress INTEGER NOT NULL DEFAULT 0` column to `books`. Created `page_content` table and `page_text_fts` virtual FTS4 table index.

---

## Remaining Features in Phase 2A Sprint
- **Feature 4**: Zoom Level Memory tracking per book.
- **Feature 5**: Active Reading Timer and Session Tracking (`ReadingSession` entity).
