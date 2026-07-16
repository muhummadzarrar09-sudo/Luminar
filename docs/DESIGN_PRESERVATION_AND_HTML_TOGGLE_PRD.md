# PRD — Design Preservation & Togglable HTML View (SSS+ Grade)

*Prepared on: July 14, 2026*
*Target: Implementing visual design preservation for documents and a hybrid HTML code/preview switcher*

---

## Part 1: Preserving "The Design" (Formatting & Layout Preservation)

Currently, Luminar parses Word documents (`DOCX`/`ODT`) and E-books (`EPUB`/`MOBI`) into flattened, single-column plain text paragraphs. While highly readable, this approach strips away crucial structural cues—such as **horizontal line separators, page breaks, text alignments (centered poetry, right-aligned signatures), and bold section headers**—making documents look plain and generic.

To elevate this to **SSS+ standard**, we can utilize Luminar's existing Markdown rendering capabilities in `TextReaderView` to map Word and E-book layout styles directly into native Compose elements.

---

### 📐 1. Word Document (`DOCX` / `ODT`) Structure Mapping

To prevent DOCX files from rendering as flat text dumps, we will map three layout parameters from the underlying OpenXML format into Markdown:

#### A. Page Breaks & Horizontal Lines (`---` mapping)
*   **The Issue:** Microsoft Word saves page breaks or structural borders using specialized tags. Currently, these are skipped, merging separate sections into a single paragraph flow.
*   **The OpenXML mapping:**
    *   **Page break tag:** `<w:br w:type="page"/>` or `<w:lastRenderedPageBreak/>`.
    *   **Horizontal line separator:** `<w:pBdr><w:bottom w:val="single".../></w:pBdr>`.
*   **The Solution:** Update `docxXmlToText` inside `DocumentParser.kt` to scan for these tags. When matched, append `\n\n---\n\n` in the output stream. This parses directly as a native `TextBlock.Divider` inside `TextReaderView`, rendering as a beautiful Material 3 `HorizontalDivider` line.

#### B. Text Alignment Preservation (Center, Right, Justified)
*   **The Issue:** Center-aligned quote blocks, right-aligned dates, or justified book chapters are all forced to align-left, stripping the original formatting context.
*   **The OpenXML mapping:** Alignments are saved in paragraph properties: `<w:jc w:val="center"/>`, `<w:jc w:val="right"/>`, or `<w:jc w:val="both"/>` (Justified).
*   **The Solution:**
    1. Extract alignment tokens inside `docxXmlToText` and append HTML/Markdown-compatible alignment tags or annotate paragraph text blocks with formatting markers (e.g. `[align=center]`).
    2. Map these inside `TextReaderView` to Compose typography layout parameters: `TextAlign.Center`, `TextAlign.End`, or `TextAlign.Justify`, keeping structural layouts intact.

#### C. Styled Blockquotes & Margins
*   **The Issue:** Citations or blockquotes render as standard text paragraphs, lacking indent styling.
*   **The OpenXML mapping:** Indented quotes are saved in paragraph indents: `<w:ind w:left="720"/>` (where 720 represents twentieths of a point).
*   **The Solution:** If the left indent value exceeds a specific threshold, format the parsed paragraph as a Markdown Blockquote (`> Quote text`). This triggers a styled vertical Gold bar, an italic font, and indent padding inside `TextReaderView`.

---

### 📖 2. E-Book (`EPUB` / `MOBI` / `HTML`) Structural Dividers

*   **The Issue:** Authors use horizontal rule divider tags (`<hr/>`) to indicate a major time skip, scene shift, or perspective change in novels. These lines are completely stripped, causing scenes to crash into each other awkwardly.
*   **The Solution:** 
    *   Update `stripHtmlToMarkdown()` in `DocumentParser.kt` and `htmlToPlainText()` in `EpubParser.kt`.
    *   Replace any `<hr[^>]*>` tag with a clear Markdown divider `\n\n---\n\n` instead of stripping it.
    *   This instantly creates visual scene breaks throughout EPUBs, MOBIs, and HTML files!

---

## Part 2: Togglable HTML View (Preview vs. Source Code)

Importing `.html` files is a common task for developers, web designers, and power users. Displaying *only* raw HTML source code is disappointing for reading articles, while displaying *only* stripped text is disappointing for viewing layouts.

We will build a **Hybrid HTML Mode Switcher** that lets users toggle between **Preview Mode** and **Code Mode** on the fly.

```
       HTML Reader Toolbar Controls:
  ┌──────────────────────────────────────────────┐
  │ [←] title.html             [⚙]  [Mode: 🌐/💻] │  Toggle Button
  └──────────────────────────────────────────────┘
```

---

### 📱 1. User Interface Design (The Toggle Button)

Add a visual toggle button inside `ReaderTopControls` (at the top-right toolbar of `ReaderScreen.kt`).
*   **Visibility Condition:** The button **only** displays if the current file format is `BookFormat.HTML`.
*   **Visual Icons:**
    *   **Preview Mode active:** Displays a **`ic_view_list_24` (or terminal icon 💻)** representing "Switch to Source Code".
    *   **Code Mode active:** Displays a **`ic_auto_stories_48` (or globe icon 🌐)** representing "Switch to Rendered Preview".

---

### ⚙️ 2. Architectural State & Event Pipeline

To keep the application modular and maintain Clean Architecture, we will pass events through the ViewModel:

```
  ReaderScreen (User Taps Toggle)
         │
         v  [Emit Event]
  ReaderEvent.ToggleHtmlViewMode
         │
         v  [ViewModel Updates State]
  ReaderState (Swaps isHtmlPreviewMode: Boolean)
         │
         v  [Compose Recomposes UI]
  TextReaderView (Displays Rendered Markdown OR Syntax-Highlighted Code)
```

#### A. ViewModel Events & State (`ReaderViewModel.kt`)
*   **New State Parameter:** `isHtmlPreviewMode: Boolean = true` (persisted in DataStore or remembered per session).
*   **New Event:** `ReaderEvent.ToggleHtmlViewMode`.
*   **The Switcher Logic:**
    *   When `ToggleHtmlViewMode` is received, toggle `isHtmlPreviewMode` on/off.
    *   Update the `textContent` inside your state:
        *   If `isHtmlPreviewMode` is **True**: Load the file, process it through `stripHtmlToMarkdown(htmlText)`, and set `renderFormat = BookFormat.MARKDOWN`.
        *   If `isHtmlPreviewMode` is **False**: Load the file as raw UTF-8 string, and set `renderFormat = BookFormat.XML` (which triggers VS Code-style syntax highlighting).

---

### 🎨 3. Unified Reader View Swapping (`ReaderScreen.kt`)

In your main screen body, route the rendering mode cleanly:

```kotlin
val renderFormat = when {
    uiState.isEpub -> BookFormat.MARKDOWN
    uiState.isDocument -> BookFormat.MARKDOWN
    uiState.isHtml -> if (uiState.isHtmlPreviewMode) BookFormat.MARKDOWN else BookFormat.XML
    else -> book.format
}

val renderingMode = when {
    uiState.isHtml -> if (uiState.isHtmlPreviewMode) RenderingMode.MARKDOWN else RenderingMode.CODE
    else -> book.format.renderingMode
}
```

---

## 📈 SSS+ IMPLEMENTATION IMPACT

By building this hybrid pipeline, you solve two major design complaints simultaneously:

1.  **DOCX/EPUB documents maintain their original visual rhythm:** Dividers represent page transitions, quote margins provide visual hierarchy, and paragraph alignment keeps layouts looking identical to their printed origins.
2.  **HTML becomes a powerhouse format:** Developers can audit source code directly inside Luminar (with beautiful syntax highlighting and line numbers), then swap to rendered preview mode to read it like a fully-formatted article.
