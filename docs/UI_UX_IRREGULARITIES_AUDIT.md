# Luminar Reader — UI/UX Irregularities & Performance Audit

*Conducted on: July 14, 2026*
*Target: Resolution of hidden visual, functional, and performance anomalies before release*

---

## Executive Summary

While Luminar Reader has a highly premium feature list and follows Clean Architecture, a deep code-level audit of your presentation layer reveals **six major irregularities**. These anomalies currently block core features from working, cause visual clashes in Light Mode, and trigger severe UI stuttering (jank) in comic reading.

Addressing these issues is critical to ensure that your first public testers experience a fluid, high-fidelity app rather than a "broken prototype."

This audit isolates each issue, shows the exact file/lines where they live, and provides a clear, zero-coding **action plan** to resolve them.

---

## 🚨 THE 6 CRITICAL IRREGULARITIES

### 1. The Table of Contents is Visually complete but Fully Non-Functional
*   **The Code:** `ReaderScreen.kt` (around line 614)
*   **The Issue:** 
    ```kotlin
    onClick = {
        showToc = false
        // TODO: Jump to chapter block index
        onInteraction()
    }
    ```
    The Table of Contents dropdown displays beautiful chapter titles, but clicking any of them does absolutely nothing. The click simply closes the drawer.
*   **Why it hurts UX:** Users opening an EPUB or document expect to jump directly to a chapter. Tapping a chapter and having nothing happen makes the app feel completely broken.
*   **The Plan to Fix:**
    1. Define a `ReaderEvent.GoToChapter(chapterIndex: Int)` event in `ReaderViewModel.kt`.
    2. When clicked, have the ViewModel search your parsed `blockTexts` for the level-1 Markdown header (`# Chapter Title`) matching that chapter.
    3. Update the `scrollToBlockIndex` state. This will trigger the Compose `LaunchedEffect(scrollToBlockIndex)` inside `TextReaderView.kt` which animates the scroll directly to that block.

---

### 2. Main-Thread Synchronous Image Decoding in Comic Mode (Severe Paging Jank)
*   **The Code:** `ComicReaderView.kt` (around line 98)
*   **The Issue:** 
    ```kotlin
    val bitmap = remember(comicFile.absolutePath, entryPath) {
        runCatching {
            val bytes = getImageBytes(comicFile, entryPath)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    ```
    `getImageBytes` is a synchronous file system ZIP-extraction call. This, along with `BitmapFactory.decodeByteArray`, is executed **synchronously on the Main Thread (UI Thread)** inside a Compose `remember` block.
*   **Why it hurts UX:** Unzipping and decoding high-resolution JPEGs can take 150ms–300ms. Running this on the main thread freezes the UI during swipes, creating severe lag and dropping frames.
*   **The Plan to Fix:**
    1. Shift image loading and bitmap decoding into a background coroutine using `produceState` or a `LaunchedEffect`.
    2. Display a sub-loading shimmer for the single card while decoding, keeping the main `HorizontalPager` container buttery-smooth (60–120 FPS).
    3. Implement a simple 3-page bitmap cache (Current, Previous, Next) and recycle off-screen bitmaps to prevent memory spikes.

---

### 3. Dead Progress Bar & Locked Chapter Label in Text Reader (EPUB, TXT, DOCX, Code)
*   **The Code:** `ReaderScreen.kt:356`, `ReaderViewModel.kt:642`
*   **The Issue:** 
    The gold Kindle-style `ReadingProgressBar` at the bottom of the screen relies on `uiState.currentPage` and `uiState.totalPages`. However, as the user scrolls through any text-based format, `onScrollPositionChanged(position)` only updates `scrollPosition`. It **never recalculates the current page or active chapter index.**
*   **Why it hurts UX:** 
    *   The reading progress bar stays locked at `0%` forever during scroll.
    *   The reader title bar displays "Ch. 1 of 12" even if the user scrolls all the way to the end of the book.
*   **The Plan to Fix:**
    1. Since text-based files are split into parsed text blocks, the ViewModel should calculate chapter boundaries by scanning blocks for `# Chapter Title` markers.
    2. When `onScrollPositionChanged(firstVisibleBlockIndex)` is triggered, the ViewModel must check which chapter boundary range the block falls into.
    3. Automatically update `uiState.currentPage` and `uiState.currentChapterIndex` to the correct scrolling values, bringing the progress bar and chapter indicators to life.

---

### 4. Eviction of Text-To-Speech (TTS) During Background Multitasking
*   **The Code:** `TtsController.kt`
*   **The Issue:** 
    `TtsController` is a singleton utilizing the Android application context, but it has no connection to an Android `Foreground Service` or `MediaSession`.
*   **Why it hurts UX:** If a user locks their screen or switches apps to multitask while listening to a book, the Android OS will quickly flag the application process as inactive and evict it from memory, abruptly stopping the spoken audio.
*   **The Plan to Fix:**
    1. Bind `TtsController` actions to a background-running Android `Foreground Service` (`TtsService`).
    2. Render a system notification with media controls (Play, Pause, Skip, Stop). This guarantees background audio play and lets users control playback from the lock screen.

---

### 5. Jarring Visual Clash of Cover Placeholders in Light Theme
*   **The Code:** `LibraryScreen.kt` (around lines 1100–1110)
*   **The Issue:** 
    Books without an embedded cover image utilize `CoverPlaceholder`, which features a hardcoded dark-gold and near-black gradient background (`Color(0xFF111111)`, `Color(0xFF3B3014)`).
*   **Why it hurts UX:** While this looks highly premium in AMOLED Dark Mode, when the user switches to Light Theme, these massive near-black rectangles create a jarring, visually aggressive smear in the middle of a clean, light grid.
*   **The Plan to Fix:**
    1. Make `CoverPlaceholder` theme-aware.
    2. In Light Mode, swap the near-black background for a warm, light gold-tinted paper background, soft gray gradients, and charcoal-colored lettering, maintaining cohesive lightness.

---

### 6. Missing Pinch-To-Zoom and Double-Tap Gestures in PDF Reader
*   **The Code:** `ReaderScreen.kt` (around lines 368–375, `PdfReaderView`)
*   **The Issue:** 
    Your custom PDF viewer uses `mhiew` Android PDF Viewer wrapped in a Compose `AndroidView`, but it does not enable or handle pinch-to-zoom or double-tap zoom scales in its native configuration block.
*   **Why it hurts UX:** Reading technical manuals, schematics, or multi-column PDFs is impossible if users cannot zoom in to inspect tiny fonts or figures.
*   **The Plan to Fix:**
    1. Configure `.enableDoubletap(true)` and `.enableAntialiasing(true)` inside the `PDFView` builder chain in `PdfReaderView`.
    2. Enable standard swipe navigation and bind scroll gestures to provide a highly interactive PDF reading experience.

---

## 🛠️ RECOMMENDED RESOLUTION ORDER

By fixing these irregularities, you'll eliminate the elements that make the app feel like a "work-in-progress" and deliver a flawless, commercial-grade product.

| Priority | Issue | Complexity | Impact |
|---|---|---|---|
| 1 | **TOC Chapter Jumping (Issue 1)** | Low | High (Fixes broken navigation) |
| 2 | **Active Scroll Progress & Chapter Update (Issue 3)** | Medium | Critical (Animates bottom bar & headers) |
| 3 | **Background Async Comic Decoding (Issue 2)** | Medium | Huge (Removes layout freeze/jank entirely) |
| 4 | **Theme-Aware Cover Placeholders (Issue 5)** | Low | High (Preserves light mode beauty) |
| 5 | **PDF Pinch-to-Zoom (Issue 6)** | Low | Medium (Essential utility) |
| 6 | **TTS Foreground Service (Issue 4)** | High | High (Premium background audio) |
