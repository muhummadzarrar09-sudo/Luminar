# Phase 2 — Reader Personalisation

## What's new

The text reader is now fully personalised. Users control font size directly from the reader, see document stats, and their preferences persist across sessions.

### Features

#### 🔤 Font size control (6 scales)
| Scale | Multiplier | Description |
|-------|-----------|-------------|
| Tiny | 75% | Compact reading — fits more on screen |
| Small | 88% | Slightly smaller than default |
| Normal | 100% | Default size |
| Large | 115% | Easier on the eyes |
| Huge | 135% | Large text |
| Massive | 160% | Accessibility |

- **A−** / **A+** buttons in the reader bottom bar
- Current scale label shown between buttons
- Scales apply to ALL text elements: paragraphs, headings, code blocks, quotes, lists
- Heading size ratios preserved (H1 stays proportionally larger than body)
- Setting persists in DataStore — reopening any text file uses your last choice

#### 📊 Word & character count
- Shown in the reader bottom bar beneath font controls
- Formatted with K/M suffixes for large files (e.g. "12.5K words · 68.3K chars")
- Computed on file load, zero overhead during reading

#### ⚙️ Settings screen — TEXT READER section
New section in Settings between READING and ABOUT:
- **Font scale picker** — all 6 options as radio buttons with descriptions
- **Default scroll mode** — Vertical scroll vs Paged, persisted in DataStore

### Files changed (NO build files touched)

**Modified:**
- `data/model/ScrollMode.kt` — Added `FontScale` enum (6 levels, `next()`/`previous()`, multiplier)
- `data/local/datastore/UserPreferencesRepository.kt` — Added `fontScale` and `defaultScrollMode` to `UserPreferences`, persistence methods, DataStore keys
- `presentation/reader/ReaderViewModel.kt` — `fontScale` + `wordCount`/`charCount` in state, `IncreaseFontSize`/`DecreaseFontSize` events, preference wiring
- `presentation/reader/ReaderScreen.kt` — Font size A−/A+ buttons in bottom controls, word/char count display, `formatCount()` helper
- `presentation/reader/TextReaderView.kt` — Accepts `FontScale`, applies `scale` multiplier to all font sizes and line heights
- `presentation/settings/SettingsViewModel.kt` — `FontScaleSelected`/`ScrollModeSelected` events, DataStore wiring
- `presentation/settings/SettingsScreen.kt` — New "TEXT READER" section with font scale radio buttons and scroll mode picker

**NOT changed:**
- All Gradle/build files
- Room database schema
- PDF reader
- Navigation
- Theme system
