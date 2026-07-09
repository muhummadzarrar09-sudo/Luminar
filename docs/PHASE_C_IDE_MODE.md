# Phase C — IDE Mode (VS Code-like Code Rendering)

## What Changed

Code files (.kt, .py, .js, .java, .go, .rs, .swift, .c, .cpp, etc.) now render with a proper IDE experience instead of a flat monospace text block.

### Features

#### 🔢 Line Numbers
Every line gets a numbered gutter on the left side:
- Auto-sized gutter width based on total line count
- Right-aligned numbers in the gutter
- Subtle background color distinguishing gutter from code area
- Gutter colors per theme:
  - AMOLED: `#141414` gutter on `#1A1A1A` code
  - Sepia: `#D9CEB2` gutter on `#E2D5B5` code
  - Light: `#E2E2E2` gutter on `#EEEEEE` code

#### 🎨 Syntax Highlighting
Real syntax-aware coloring for ALL supported languages:

| Element | AMOLED | Sepia | Light |
|---------|--------|-------|-------|
| Keywords (`fun`, `class`, `if`, `return`, etc.) | Pink `#FF79C6` | Dark red | Blue |
| Strings (`"hello"`, `'world'`) | Yellow `#F1FA8C` | Dark green | Green |
| Comments (`//`, `#`, `--`) | Muted blue `#6272A4` | Gray | Gray |
| Numbers (`42`, `3.14`, `0xFF`) | Purple `#BD93F9` | Purple | Purple |
| Default text | Light `#F8F8F2` | Brown | Dark gray |

Colors are **Dracula theme inspired** for AMOLED — the same palette developers love in VS Code.

**130+ keywords** recognized across:
- Kotlin, Java, Scala
- Python
- JavaScript, TypeScript
- C, C++, C#
- Rust
- Go
- Swift, Dart
- Ruby, PHP
- SQL, Shell
- Common functions (`print`, `console.log`, etc.)

#### 💡 Smart Comment Detection
- Full-line comments: `//`, `#`, `--`, `;`
- Inline comments: detects `//` and `#` that aren't inside string literals
- Comments rendered in italic + muted color

#### 📋 File Info Header (from Phase A)
Already shows line count + character count at the top of code files.

### How it looks

**Before:**
```
┌──────────────────────────────────┐
│ def hello():                     │  Flat monospace, no colors
│     print("hello world")        │  No line numbers
│                                  │  Can't tell code from text
└──────────────────────────────────┘
```

**After (AMOLED theme):**
```
┌──────────────────────────────────┐
│ 247 lines              8,432 ch │  ← File info header
├──────────────────────────────────┤
│  1 │ def hello():               │  ← "def" pink, rest white
│  2 │     print("hello world")   │  ← "print" pink, string yellow
│  3 │     # this is a comment    │  ← Italic, muted blue
│  4 │     x = 42                 │  ← "42" purple
│  5 │     return x               │  ← "return" pink
└──────────────────────────────────┘
```

### Architecture

The syntax highlighter is a **character-by-character state machine** — no regex, no external library:

1. Check for full-line comment → render entire line in comment style
2. Walk char by char:
   - `"` or `'` or `` ` `` → scan to closing quote → string color
   - Digit (not preceded by letter) → scan number → number color
   - Letter/underscore → scan word → check if keyword → keyword or default color
   - Everything else → default color
3. Inline comment detection skips characters inside string literals

### Files Changed
- `TextReaderView.kt` — IDE-mode code block renderer with per-line layout (gutter + code), `syntaxHighlight()` function (130+ keywords, 5 token types), `findCommentStart()` for smart comment detection
