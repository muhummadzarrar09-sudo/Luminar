# Phase B — Spreadsheet Mode

## What Changed

CSV, XLSX, and ODS files now render with a proper spreadsheet experience instead of raw text or basic tables.

### New Features

#### 📊 CSV Parser
**Before:** CSV files rendered as a monospace code block — raw comma-separated text.

**After:** CSV/TSV files are automatically parsed into a proper `TextBlock.Table`:
- Auto-detects delimiter (comma vs tab)
- Handles quoted fields
- First row becomes the header
- Column count normalized across all rows

#### 🔢 Row Numbers
Spreadsheet mode shows a **row number column** on the left:
- `#` header for the number column
- Sequential numbers: 1, 2, 3...
- Subtle gray text, centered, 36dp wide

#### 🦓 Zebra Striping
Alternating row backgrounds for readability:
- Every odd row gets a subtle 4% opacity background tint
- Makes scanning across wide tables much easier

#### 📐 Number Right-Alignment
Cells containing numeric values are automatically:
- Right-aligned (like Excel)
- Rendered in monospace font (for digit alignment)
- Detected via `toDoubleOrNull()`

#### ↔️ Horizontal Scroll
Tables with more than 4 columns:
- Become horizontally scrollable
- Each column gets a fixed 120dp width
- Prevents column crushing on narrow screens

#### 📋 Sticky Header Feel
Header row gets special treatment in spreadsheet mode:
- **Thicker separator** (2dp vs 1dp) below header
- **Green-tinted background** (Excel-like)
  - AMOLED: dark green `#1E3A1E`
  - Sepia: warm gold `#E6D5A8`
  - Light: soft green `#E2EFDA`
- **Green header text** matching the background tint

### How it looks

```
┌──────────────────────────────────┐
│ #  │ Name      │ Age  │ City    │  ← Green header, bold
│────┼───────────┼──────┼─────────│  ← Thick separator
│ 1  │ Alice     │   28 │ London  │  ← White row
│ 2  │ Bob       │   35 │ Paris   │  ← Zebra stripe
│ 3  │ Charlie   │   42 │ Tokyo   │  ← White row
│ 4  │ Diana     │   31 │ Berlin  │  ← Zebra stripe
└──────────────────────────────────┘
        Numbers → right-aligned ↗
```

### Files Changed
- `TextReaderView.kt` — Complete table renderer rewrite with spreadsheet features, CSV parser, horizontal scroll, row numbers, zebra striping, number alignment
