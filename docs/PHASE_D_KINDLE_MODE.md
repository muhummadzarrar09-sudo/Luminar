# Phase D — Kindle Mode Enhancements

## What Changed

EPUB, MOBI, AZW3, and FB2 e-books now have a proper Kindle-like reading experience with chapter awareness, reading time estimation, and a table of contents.

### Features

#### 📖 Chapter Progress Indicator
Bottom controls now show:
```
Ch. 3 of 12                           ~2h 15m left
```
- Gold chapter counter on the left
- Estimated time remaining on the right
- Only appears when there are multiple chapters

#### ⏱️ Estimated Reading Time
Based on **230 WPM average reading speed**:
- Calculates words remaining from current progress
- Shows "~Xm left" for under an hour
- Shows "~Xh Ym left" for longer books
- Updates as you scroll through the book

#### 📑 Table of Contents (TOC)
New 📑 button in the reader top controls when reading ebooks:
- Shows a dropdown listing ALL chapter titles
- Current chapter highlighted in gold + bold
- Tap any chapter to jump to it
- Only appears when book has 2+ chapters

### How it looks

**Reader bottom controls (ebook):**
```
┌──────────────────────────────────┐
│ [A−]    Font: Normal      [A+]  │  Font controls
│ Ch. 3 of 12            ~45m left│  Chapter + time
│ 42.1K words  ·  238.5K chars    │  Stats
└──────────────────────────────────┘
```

**TOC dropdown:**
```
┌────────────────────┐
│ Chapter 1: Intro   │
│ ★ Chapter 2: Rise  │  ← Current (gold, bold)
│ Chapter 3: Fall    │
│ Chapter 4: Finale  │
└────────────────────┘
```

### Architecture

- `ReaderUiState` now carries `chapterTitles: List<String>` and `currentChapterIndex: Int`
- `chapterProgressLabel`, `timeRemainingLabel`, `estimatedMinutesLeft` are computed properties — zero overhead
- Chapter titles extracted from EPUB during `loadEpubChapters`
- 230 WPM constant for time estimation (matches industry standard for adult fiction)

### Files Changed
- `ReaderViewModel.kt` — `chapterTitles`, `currentChapterIndex` state fields, computed properties for progress/time labels, chapter data populated from EPUB loader
- `ReaderScreen.kt` — Chapter progress row in bottom controls, TOC dropdown button in top controls, time remaining display
