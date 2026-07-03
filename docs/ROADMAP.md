# Luminar Reader — Development Roadmap

## 6 Phases (0–5)

| Phase | Title | Status | Summary |
|-------|-------|--------|---------|
| **0** | Bug fixes | ✅ Done | System bar restoration, app icon, theme not applied to Library |
| **1** | Multi-format text support | ✅ Done | Import & read .md, .txt, .json, .xml, .csv, .html, .log, 50+ code extensions. Markdown rendering with headings, bold/italic, code blocks, lists, quotes, links |
| **2** | Reader personalisation | ✅ Done | Font size (6 scales: Tiny→Massive), word/char count display, scroll mode preference persisted in DataStore, font controls in reader bottom bar, TEXT READER section in Settings |
| **3** | Search within document | ✅ Done | Find-in-text with gold highlighting, ▲▼ match navigation, match counter, search bar overlay, case-insensitive, auto-scroll to matches |
| **4** | Library enhancements | ✅ Done | Sort (4 modes), format filter chips with counts, search library by title, grid/list toggle, file size on cards, compact list view |
| **5** | EPUB support | ✅ Done | Full EPUB parsing (ZIP → OPF → XHTML → Markdown), cover extraction, chapter navigation, HTML entity decoding, all reader features (search, fonts, themes) |

---

---

## 🎉 All 6 phases complete!

Every phase has been built with **zero build file changes** — no Gradle, no TOML, no properties files modified. All features are pure Kotlin/Compose code, XML resources, and PNG assets.

---

### Phase 3 — Search within document
- Search bar appears in reader top controls
- Real-time match highlighting in TextReaderView
- Previous/next match navigation
- Match count display
- Works for all text-based formats
- Case-sensitive toggle

### Phase 4 — Library enhancements
- Sort books by: title, date added, last opened, file size
- Filter chips: All, PDF, Markdown, Code, Text, Other
- Search/filter library by title
- Toggle between grid (current) and list view
- Show file size on book cards
- Swipe-to-delete on list view

### Phase 5 — EPUB support
- Parse .epub files (ZIP container → OPF manifest → XHTML content)
- Chapter-based navigation with table of contents sidebar
- Render XHTML chapters using the existing text rendering pipeline
- Handle embedded images (extract from zip, display inline)
- Basic CSS styling (fonts, margins, emphasis)
- Progress tracking per chapter
