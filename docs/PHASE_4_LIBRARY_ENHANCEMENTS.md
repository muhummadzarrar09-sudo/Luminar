# Phase 4 — Library Enhancements

## What's new

The library screen transforms from a simple grid into a full-featured file manager with search, sort, filter, and two view modes.

### Features

#### 🔍 Library search
- Tap the search icon in the top bar → title bar becomes a search field
- Real-time filtering as you type — matches against book titles (case-insensitive)
- Close button (✕) clears search and restores normal title
- "No matching files" message when filter returns empty results

#### 🏷️ Format filter chips
- Horizontal scrollable row of filter chips: **All**, **PDF**, **Markdown**, **Code**, **Text**, **Other**
- Each chip shows the count of files in that category (e.g. "Code (12)")
- Chips with zero items are hidden (except "All")
- Tapping a chip instantly filters the library
- Gold highlight on the active chip

#### 📊 Sort options
- Sort dropdown with 4 options:
  - **Recent** — last opened or added (default, matches original behavior)
  - **Title** — alphabetical A→Z
  - **Added** — newest imports first
  - **Size** — largest files first
- "Sort: Recent" button in the toolbar opens a dropdown menu
- File count displayed: "12 files" label updates with filter

#### 📏 File size display
- Every book card now shows file size beneath the title (e.g. "1.2 MB", "48 KB")
- List view shows format, file size, and reading progress % in a metadata row
- Computed lazily with `remember` — no repeated disk I/O

#### ⊞/☰ Grid/List toggle
- Toggle button in the top bar switches between:
  - **Grid view** (⊞) — the existing 2-column card grid with covers
  - **List view** (☰) — compact single-column list with small thumbnails, title, format badge, file size, and progress %
- List cards show a 48dp cover thumbnail (or first-letter fallback)
- Long-press to delete works in both views

### Architecture

All filtering, sorting, and searching is **client-side** — computed in `LibraryUiState.books` as a derived property from `allBooks`. No database queries or schema changes. The ViewModel stores the raw book list in `allBooks`, and the computed `books` property applies filters → search → sort in sequence.

New enums in ViewModel: `SortOrder`, `FormatFilter`, `ViewMode`
New events: `SetSortOrder`, `SetFormatFilter`, `UpdateSearchQuery`, `ToggleSearch`, `ToggleViewMode`

### Files changed (NO build files touched)

**Modified:**
- `LibraryViewModel.kt` — `SortOrder`, `FormatFilter`, `ViewMode` enums; derived `books` property with filter/search/sort pipeline; `formatCounts` for chip labels; 5 new events
- `LibraryScreen.kt` — Full rewrite: `LibrarySearchField`, `LibraryToolbar` (filter chips + sort dropdown), `LibraryList` (list view), `LibraryGrid` (unchanged), file size on cards, search/list toggle buttons in TopAppBar, "No matching files" empty state, `formatFileSize()` helper

**NOT changed:**
- Build files, database, navigation, reader, settings, theme
