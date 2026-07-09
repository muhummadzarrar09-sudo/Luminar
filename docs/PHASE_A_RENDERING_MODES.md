# Phase A — Format-Specific Rendering Modes

## What Changed

Luminar now detects the file format and switches to a **tailored rendering mode** that makes each format feel like its dedicated native app.

### The RenderingMode System

New `RenderingMode` enum derived from `BookFormat.renderingMode`:

```
DOCX/ODT/RTF/DOC  → DOCUMENT mode    (Word-like)
XLSX/ODS/CSV       → SPREADSHEET mode (Excel-like)
PPTX/ODP/PPT       → PRESENTATION mode
EPUB/MOBI/AZW3/FB2 → EBOOK mode      (Kindle-like)
CODE/JSON/XML/HTML  → CODE mode       (VS Code-like)
MD                  → MARKDOWN mode   (Obsidian-like)
CBZ/CBR/CBT         → COMIC mode
TXT/LOG             → PLAIN_TEXT mode
PDF                 → PDF mode        (native viewer)
```

### 📄 DOCUMENT Mode (Word-like)

What the user sees when opening a DOCX:

- **Paper cards**: Each paragraph wrapped in a white card with shadow, creating a "paper on desk" feel
- **Serif font**: Body text uses `FontFamily.Serif` (like Times New Roman in Word)
- **Wider paragraph spacing**: 28sp line height (vs 24sp default), 8dp vertical padding
- **Word-like heading colors**: Blue headings instead of gold
  - AMOLED: `#6BA3D6` (soft blue)
  - Sepia: `#5B3A00` (dark brown)
  - Light: `#1F3864` (navy)
- **Paper background**: Light gray canvas behind white cards (even in AMOLED mode, uses dark gray instead of pure black)
- **Bullet/list indentation**: Proper 12dp margins
- **Dark text on paper**: Even in AMOLED theme, text is light gray on dark card (readable paper feel)

### 💻 CODE Mode (VS Code-like)

What the user sees when opening a .kt, .py, .js file:

- **File info header bar**: Shows "247 lines" and "8,432 chars" in a subtle bar at the top
- **Monospace font**: (already existed)
- **Code block background**: (already existed)

### How it works

1. `BookFormat` has a new `renderingMode` computed property
2. `TextReaderView` receives `renderingMode` parameter
3. `RenderBlock` changes fonts, colors, spacing, and padding based on mode
4. `DocumentPaperCard` wraps each block in document mode
5. `CodeFileHeader` shows line/char count in code mode

### Files

**New:**
- `data/model/RenderingMode.kt` — 9-value enum

**Modified:**
- `data/model/BookFormat.kt` — `renderingMode` computed property mapping each format
- `presentation/reader/TextReaderView.kt` — `renderingMode` parameter, `DocumentPaperCard`, `CodeFileHeader`, mode-aware fonts/colors/spacing
- `presentation/reader/ReaderScreen.kt` — Passes `renderingMode` from book's original format
