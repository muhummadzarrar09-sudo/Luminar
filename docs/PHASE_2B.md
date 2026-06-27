# Luminar — Phase 2B Sprint Notes & Progress

## Executive Summary
Phase 2B elevates Luminar Reader into an interactive study companion by introducing rich text annotation (Highlights + User Notes), quick place saving (Bookmarks Drawer), a detailed reading analytics dashboard (Stats Screen), and instant in-book dictionary definitions.

---

## Completed Features

### Feature 1: Highlights & Notes (PDF + EPUB)
- **What Was Built**:
  - Created standardized `Highlight` database model tracking format-scoped targeting (PDF page indices + bounding rectangle coordinates vs EPUB CFI location strings + selected text excerpts).
  - Defined preset ARGB annotation swatches (`HighlightYellow`, `HighlightBlue`, `HighlightPink`, `HighlightGreen`).
  - Position-based rectangular highlight mode for PDF documents and Readium DecorableNavigator text selection callbacks for EPUBs.
  - Built `HighlightsPanel` modal bottom sheet displaying colored swatch borders, excerpt previews, italicized user note notes, tap-to-jump navigation, and swipe-to-delete dismissal.
- **Stack & Database Changes**:
  - **Room Migration v6 → v7**: Created `highlights` relational table with cascading deletion on `books.id`.

### Feature 2: Bookmarks Drawer & Quick Toggle
- **What Was Built**:
  - Standardized `Bookmark` database model capturing instant reading positions across PDF pages or EPUB CFIs.
  - Added instant TopAppBar bookmark toggle icon (`ic_auto_stories` filled vs outline) inside both `ReaderScreen` and `EpubReaderScreen`. Piped reactive state directly from Room DAOs.
  - Implemented `BookmarksDrawer` right-edge swipe sheet rendering newest-first saved places, swipe-to-delete with undo, and inline label renaming.
- **Stack & Database Changes**:
  - **Room Migration v7 → v8**: Created `bookmarks` table with cascading foreign key bound to `books.id`.

---

## Remaining Features in Phase 2B Sprint
- **Feature 3**: Full Reading Stats Dashboard chart screen.
- **Feature 4**: Offline Dictionary Lookup (EPUB only).
