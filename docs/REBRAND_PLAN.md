# Luminar Reader — Rebrand Plan: "Every Format's Native App"

## The Vision

When someone opens a file in Luminar, it should feel like they opened
the **best dedicated app** for that format. Not a generic text dump —
a **tailored experience**.

> "One app that replaces Word, Excel, Kindle, VS Code, Comic Reader,
> and 25 other apps — and each one feels native."

---

## Format-Specific Rendering Modes

### 📄 Mode 1: DOCUMENT MODE (DOCX, ODT, RTF, DOC)
*Should feel like: Microsoft Word / Google Docs*

| Feature | Status | What to build |
|---------|--------|---------------|
| Headings with proper hierarchy | ✅ Done | — |
| Bold/italic/underline/strikethrough | ✅ Done | — |
| Bullet & numbered lists with indent | ✅ Done | — |
| Tables with headers + borders | ✅ Done | — |
| XML entity decoding | ✅ Done | — |
| Page-like card layout | ❌ | Wrap text in white "paper" cards with shadow, margins, page breaks |
| Document header (title, author, page count) | ❌ | Extract metadata from DOCX core.xml and display at top |
| Font respecting (serif for docs) | ❌ | Use serif font for documents, sans for UI |
| Paragraph spacing | ❌ | Proper line-height and para margins matching Word |
| Print/export feel | ❌ | White background forced in doc mode regardless of theme (like real paper) |

### 📊 Mode 2: SPREADSHEET MODE (XLSX, ODS, CSV)
*Should feel like: Microsoft Excel / Google Sheets*

| Feature | Status | What to build |
|---------|--------|---------------|
| Table with headers | ✅ Done | — |
| Multi-sheet tabs | ✅ Done (# Sheet N) | — |
| Proper grid rendering | ✅ Done | — |
| Horizontal scroll for wide tables | ❌ | Make tables horizontally scrollable when cols > screen width |
| Cell highlighting on tap | ❌ | Tap a cell to highlight the row/col |
| Column auto-width | ❌ | Calculate max width per column for alignment |
| Sticky header row | ❌ | Keep header visible while scrolling down |
| Sheet tab bar | ❌ | Bottom tab bar showing Sheet 1, Sheet 2, etc. (tap to jump) |
| Number formatting | ❌ | Detect numbers and right-align them |
| Row numbers | ❌ | Show row index on left side |

### 📖 Mode 3: EBOOK MODE (EPUB, MOBI, AZW3, FB2)
*Should feel like: Kindle*

| Feature | Status | What to build |
|---------|--------|---------------|
| Chapter navigation | ✅ Done (chapters as H1) | — |
| Full text rendering | ✅ Done | — |
| Font scaling | ✅ Done | — |
| 3 themes | ✅ Done | — |
| TTS | ✅ Done | — |
| Bookmarks | ✅ Done | — |
| Reading progress bar | ✅ Done | — |
| Search | ✅ Done | — |
| Table of Contents drawer | ❌ | Slide-out TOC panel showing all chapters, tap to jump |
| Chapter % indicator | ❌ | "Chapter 3 of 12 — 42%" in controls |
| Estimated time remaining | ❌ | "~2h 15m left" based on reading speed |
| Page turn animation | ❌ | Optional swipe animation for paged mode |

### 💻 Mode 4: CODE MODE (50+ extensions)
*Should feel like: VS Code*

| Feature | Status | What to build |
|---------|--------|---------------|
| Monospace rendering | ✅ Done | — |
| Dark code background | ✅ Done | — |
| Theme-aware colors | ✅ Done | — |
| Line numbers | ❌ | Show line numbers on left gutter |
| Syntax highlighting | ❌ | Color keywords/strings/comments per language |
| Code font (JetBrains Mono style) | ❌ | Use monospace with ligatures if available |
| Copy button | ❌ | Floating "Copy all" button |
| File info header | ❌ | Show filename, language, line count at top |
| Word wrap toggle | ❌ | Toggle between wrapped and horizontal scroll |

### 📑 Mode 5: PRESENTATION MODE (PPTX, ODP)
*Should feel like: PowerPoint*

| Feature | Status | What to build |
|---------|--------|---------------|
| Slide-by-slide text | ✅ Done | — |
| Slide dividers | ✅ Done | — |
| Slide card layout | ❌ | Render each slide as a card with number badge |
| Slide counter | ❌ | "Slide 3 of 24" in controls |
| Swipe between slides | ❌ | Horizontal pager like comic reader |

### 📚 Mode 6: COMIC MODE (CBZ)
*Should feel like: a comic reader app*

| Feature | Status | What to build |
|---------|--------|---------------|
| Image-per-page viewer | ✅ Done | — |
| Horizontal swipe | ✅ Done | — |
| Pinch-to-zoom | ✅ Done | — |
| Page counter | ✅ Done | — |
| Double-page spread | ❌ | Landscape mode shows 2 pages side by side |
| Manga mode (right-to-left) | ❌ | RTL reading for manga |

### 📝 Mode 7: MARKDOWN MODE
*Should feel like: Obsidian / Typora*

| Feature | Status | What to build |
|---------|--------|---------------|
| Full Markdown rendering | ✅ Done | — |
| Headings, bold, italic, lists | ✅ Done | — |
| Code blocks with background | ✅ Done | — |
| Block quotes | ✅ Done | — |
| Links | ✅ Done | — |
| Tables | ✅ Done | — |
| Horizontal rules | ✅ Done | — |
| Task lists (checkboxes) | ❌ | `- [ ] item` renders as checkbox |
| Image references | ❌ | `![alt](url)` shows image if available |
| Math/LaTeX blocks | ❌ | Basic math rendering |

---

## Execution Phases

### Phase A — "Paper Mode" for Documents (HIGH IMPACT) ✅ DONE
1. ✅ Detect document formats → apply serif font + white card layout
2. ✅ Page-like rendering: content wrapped in card with shadow + margins
3. ✅ Code file header (line count, char count)
4. ✅ Proper paragraph spacing + Word-like heading colors

### Phase B — "Spreadsheet Mode" for Data Files (HIGH IMPACT) ✅ DONE
1. ✅ Horizontal scrollable tables (>4 cols auto-scroll)
2. ✅ Sticky header feel (thick separator, green tint)
3. ✅ Row numbers column
4. ✅ Number right-alignment + monospace
5. ✅ Zebra striping (alternating row backgrounds)
6. ✅ CSV/TSV parser (auto-detect delimiter, proper table output)

### Phase C — "IDE Mode" for Code Files (MEDIUM IMPACT) ✅ DONE
1. ✅ Line numbers in left gutter (auto-sized)
2. ✅ Syntax highlighting (130+ keywords, Dracula-inspired)
3. ✅ File info header bar (line count + char count)
4. ✅ Smart comment detection (skips strings)

### Phase D — "Kindle Mode" Enhancements (MEDIUM IMPACT) ✅ DONE
1. ✅ Table of Contents dropdown (📑 button, chapter list, current highlighted)
2. ✅ Chapter progress ("Ch. 3 of 12")
3. ✅ Estimated reading time ("~2h 15m left" at 230 WPM)

### Phase E — Polish (LOWER IMPACT) ✅ DONE
1. ✅ Task list checkbox rendering (`- [ ]` → ☐, `- [x]` → ☑)
2. Manga RTL and slide cards deferred to post-launch

### Phase F — Voice Enhancements 🔜
### Phase G — UI/UX Polish 🔜

---

## Branding

**Tagline options:**
- "Every file. Its native experience."
- "One app. Every format. Zero compromise."
- "The only reader you'll ever need."
- "30 formats. Each one feels like home."

**Play Store description:**
> Luminar Reader opens PDF, EPUB, DOCX, XLSX, PPTX, MOBI, CBZ, Markdown,
> code files, and 20+ more formats — and each one renders like its
> dedicated app. Documents look like Word. Spreadsheets feel like Excel.
> E-books rival Kindle. Code reads like VS Code. One app, $4.99, no
> subscriptions, no ads, ever.
