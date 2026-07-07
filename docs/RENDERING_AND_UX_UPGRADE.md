# Document Rendering Overhaul + Kindle-Feel UX

## Phase A: Document Rendering Fixed

### DOCX — Full Style Extraction
**Before:** Only extracted headings + plain text. Bold, italic, underline, lists, tables — all stripped.

**After:**
- **Bold** → `**text**` (detects `<w:b/>`, `<w:b>`)
- **Italic** → `*text*` (detects `<w:i/>`, `<w:i>`)
- **Underline** → `__text__` (detects `<w:u>`, excludes `val="none"`)
- **Strikethrough** → `~~text~~` (detects `<w:strike/>`)
- **Bullet/numbered lists** → `- item` with indent levels (detects `<w:numPr>` + `<w:ilvl>`)
- **Tables** → Full Markdown table with headers + separator + data rows

### XLSX — Proper Tables
**Before:** Rows output as `cell1 | cell2 | cell3` — raw pipe-separated text.

**After:** Proper Markdown table format:
```
| Header 1 | Header 2 | Header 3 |
| --- | --- | --- |
| Data 1 | Data 2 | Data 3 |
```

### Markdown Table Rendering (NEW)
**Before:** `| col | col |` lines were rendered as plain paragraphs.

**After:** Full table rendering:
- Gold header row with bold text
- Border with rounded corners
- Column separators
- Alternating row feel
- Theme-aware colors
- Responsive column widths

## Phase B: Kindle-Feel UX

### Reading Progress Bar
A **thin gold progress line** at the very bottom of the reader — always visible, never intrusive:
- 2dp height, fills left-to-right as you read
- Subtle percentage text above (e.g. "47%") in 30% opacity
- Works for PDF (page-based), text files (scroll-based), comics (page-based)
- Gold fill on near-transparent track
- Appears only when there's content (hides for 0 pages)

This is the same pattern Kindle uses — a thin bar that gives you constant reading progress awareness without cluttering the UI.

## Version

Current build config says `1.0.0` but this is pre-release. Recommended version for this state:

**v0.9.0-rc1** (Release Candidate 1)

To set this, change in `app/build.gradle.kts`:
```kotlin
versionCode = 9
versionName = "0.9.0"
```

When you're ready for Play Store:
```kotlin
versionCode = 100
versionName = "1.0.0"
```

## Files changed

- `DocumentParser.kt` — Complete DOCX rewrite: `extractDocxRuns()` for bold/italic/underline/strike per-run, `parseDocxTable()` for Markdown tables, list detection with indent levels. XLSX now outputs proper Markdown tables.
- `TextReaderView.kt` — New `TextBlock.Table` type, Markdown table parser (`| col | col |` detection), styled table renderer (borders, header highlight, column separators)
- `ReaderScreen.kt` — `ReadingProgressBar` composable at bottom of reader (2dp gold line + percentage text)
