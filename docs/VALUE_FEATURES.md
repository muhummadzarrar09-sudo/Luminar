# Value Features — What Makes $4.99 Worth It

## What was added

### 1. 📖 "Continue Reading" Hero Card
**The #1 feature Kindle has.** A large card at the top of the library showing:
- Cover thumbnail (or first-letter fallback)
- "Continue reading" gold label
- Book title
- Progress bar (filled percentage)
- Format badge + percentage text ("47% · EPUB")
- Arrow indicator → tap to resume reading

Automatically shows the most recently opened book. If no book has been opened yet, the card doesn't appear.

### 2. 📚 Recently Opened Row
Horizontal scrollable row of the last 6 opened books (excluding the "continue" book), shown below the hero card:
- Small 56×76dp cover thumbnails
- Tap any to open instantly
- Scrollable — no wasted space

### 3. 🔖 Bookmark List with Navigation
**Before:** Could add bookmarks but couldn't see or navigate them.

**After:** The bookmark button now shows a count badge (e.g. "🔖 3"). Tapping it opens a dropdown menu with:
- "Add bookmark here" / "Remove bookmark" toggle at the top
- Full list of all bookmarks for the current book
- Tap any bookmark → jumps to that page/position instantly

### 4. ⏱️ Session Reading Timer
The bottom controls in the text reader now show elapsed reading time:
- "12.5K words · 68.3K chars · **5m reading**"
- Updates as you read
- Only shows after 1+ minutes (no "0m" clutter)

### 5. 📊 Reading Progress Bar (from previous)
Thin gold progress line at the very bottom — always visible, Kindle-style.

### 6. 📄 Document Table Rendering (from previous)
DOCX/XLSX tables now render as proper styled grids with borders and gold headers.

## Why these features justify $4.99

| Feature | Kindle ($11.99/mo) | Moon+ ($6.99) | ReadEra ($14.99) | Luminar ($4.99) |
|---------|-------------------|---------------|-------------------|-----------------|
| Continue Reading | ✅ | ✅ | ✅ | ✅ |
| Recently Opened | ✅ | ❌ | ✅ | ✅ |
| Bookmark list + nav | ✅ | ✅ | ✅ | ✅ |
| Session timer | ❌ | ❌ | ❌ | ✅ |
| 30 format support | ❌ (13) | ❌ (12) | ❌ (11) | ✅ |
| In-doc search | ✅ | ✅ | ❌ | ✅ |
| Code file reading | ❌ | ❌ | ❌ | ✅ |
| TTS | ✅ | ✅ | ✅ | ✅ |
| No ads ever | ❌ (promos) | ✅ | ✅ | ✅ |
| No subscription | ❌ | ✅ | ✅ | ✅ |
| Error reporting | ❌ | ❌ | ❌ | ✅ |
| AMOLED theme | ❌ | ✅ | ✅ | ✅ |
| Comic reader | ❌ | ✅ | ❌ | ✅ |
| **Price** | **$143.88/yr** | **$6.99** | **$14.99** | **$4.99** |

## Files Changed
- `LibraryScreen.kt` — `ContinueReadingCard`, `RecentlyOpenedRow` composables, added above filter toolbar
- `ReaderScreen.kt` — Bookmark dropdown with list + count badge, session timer in bottom controls, `onGoToBookmark` wired through controls chain
