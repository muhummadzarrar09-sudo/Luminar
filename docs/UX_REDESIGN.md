# UX Redesign — Bottom-First Navigation

## Research-Backed Changes

Based on UX research from 6 sources:

> "Primary actions belong in the bottom two-thirds of the screen" — Mobile UX Best Practices 2026

> "Top navbars are for labels and status, not tap targets" — Fora Soft

> "The top corners are the 'red zone' — hardest to reach one-handed" — Elaris Software

> "Bottom tab bar with 3-5 destinations is the gold standard" — Phone Simulator

## What Changed

### Before (top-heavy)
```
┌──────────────────────────────┐
│ Luminar  [🔍] [☰] [⚙]  ←RED│  All actions crammed into
│                              │  unreachable top-right corner
│                              │
│        (content)             │
│                              │
│                         [+]  │  FAB = one action only
└──────────────────────────────┘
```

### After (thumb-friendly)
```
┌──────────────────────────────┐
│ Luminar              (spin)  │  Title only, no buttons
│                              │
│        (content)             │
│                              │
│                              │
│ [🔍] [☰] [＋] [📊] [⚙️]    │  Everything in thumb zone
└──────────────────────────────┘
```

### Bottom Navigation Bar — 5 items
| Position | Icon | Label | Action |
|----------|------|-------|--------|
| 1 (left) | 🔍 / ✕ | Search / Close | Toggle library search |
| 2 | ☰ / ⊞ | List / Grid | Toggle view mode |
| 3 (center) | Gold ＋ circle | Import | Open file picker (with haptic) |
| 4 | 📊 | Stats | Open settings (where stats live) |
| 5 (right) | ⚙️ | Settings | Open settings screen |

### Top Bar — Simplified
- Title "Luminar" only (26sp, down from 30sp)
- Import spinner shows when importing
- **No buttons** — everything moved to bottom
- Search field replaces title when search is active (same as before)

### FAB Removed
The floating action button is gone — replaced by the center Import button in the bottom bar. This:
- Frees up content space (FAB overlapped book cards)
- Makes import always accessible (no scroll-hide)
- Looks more professional (FAB feels prototype-y)

### Reader Progress Bar
Thin 2dp gold line at the very bottom of the reader — always visible, Kindle-style percentage indicator.

### Document Rendering Overhaul
- DOCX: bold, italic, underline, strikethrough, lists, tables now properly extracted
- XLSX: proper Markdown tables with headers
- TextReaderView: new Table block type with styled grid rendering

## Files Changed
- `LibraryScreen.kt` — Complete layout restructure: bottom bar replaces top actions + FAB, simplified top bar, `LibraryBottomBar` + `BottomBarItem` composables
- `DocumentParser.kt` — `extractDocxRuns()` for inline styles, `parseDocxTable()` for tables, XLSX Markdown tables
- `TextReaderView.kt` — `TextBlock.Table` type, Markdown table parser, styled table renderer
- `ReaderScreen.kt` — `ReadingProgressBar` at bottom of reader
