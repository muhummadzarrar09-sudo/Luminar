# Phase 3 — Search Within Document

## What's new

Full find-in-text search for all text-based file formats. Type a query, see every match highlighted in the document, and jump between them.

### Features

#### 🔍 Search bar
- Appears at the top of the reader when you tap the search icon (🔎) in the top controls
- Clean inline text field with placeholder text ("Search in document…")
- Themed to match current reader theme (AMOLED / Sepia / Light)
- Press Enter/Search on keyboard to jump to next match
- Close button (✕) to dismiss and clear highlights

#### ✨ Match highlighting
- All matches highlighted with a gold tint throughout the entire document
- **Current match** highlighted with a stronger gold to distinguish it from other matches
- Works across all block types: headings, paragraphs, code blocks, quotes, lists, plain text
- Case-insensitive matching
- Minimum 2 characters to trigger search (avoids noise)

#### ▲▼ Match navigation
- Previous (▲) / Next (▼) buttons to cycle through matches
- Match counter shows "3 / 17" format (current / total)
- "No matches" label when query has no results
- Wraps around: going past the last match returns to the first

#### 📜 Auto-scroll
- Jumping to a match automatically scrolls the document to that block
- Smooth animated scroll via `animateScrollToItem`

### Architecture

**Search is block-based**: the Markdown/text parser produces a list of `TextBlock` items. When blocks are parsed, their plain text is sent to the ViewModel via `onBlocksParsed()`. The ViewModel scans these texts for the search query and stores matching block indices. The UI then:
1. Highlights all text containing the query in every visible block
2. Uses a stronger highlight for the block that is the "current match"
3. Scrolls to the current match block when navigating ▲/▼

**State flow**: `ReaderUiState` carries `isSearchActive`, `searchQuery`, `searchMatchBlockIndices`, `currentMatchIndex`, and `scrollToBlockIndex`. The scroll-to request is consumed after the UI acts on it to prevent re-scrolling.

### Files changed (NO build files touched)

**New files:**
- `res/drawable/ic_search_24.xml` — Material Symbols search icon
- `res/drawable/ic_close_24.xml` — Material Symbols close/X icon

**Modified:**
- `ReaderViewModel.kt` — Search state in `ReaderUiState`, 5 new events (`OpenSearch`, `CloseSearch`, `UpdateSearchQuery`, `NextMatch`, `PreviousMatch`), `onBlocksParsed()` / `consumeScrollToBlock()` public methods, search logic
- `TextReaderView.kt` — New params (`searchQuery`, `isSearchActive`, `currentMatchBlockIndex`, `scrollToBlockIndex`, `onBlocksParsed`, `onScrollToBlockConsumed`), `highlightText()` / `highlightAnnotatedString()` functions, reports block texts to ViewModel, scroll-to-match support
- `ReaderScreen.kt` — Search icon in top controls (text-based files only), `SearchBar` composable (TextField + ▲▼ + match count + close), wired to ViewModel events

**NOT changed:**
- Build files, database, navigation, theme, settings, library, PDF reader
