# Luminar Reader — Reading Engine & Document Parser Audit ("Birth Defects" Scan)

*Conducted on: July 14, 2026*
*Target: Transitioning the core document parser (DocumentParser.kt) from prototype hacks to robust SSS+ engineering*

---

## Executive Summary

A deep code-level scan of the reading engine (`DocumentParser.kt`, `EpubParser.kt`, and `TextReaderView.kt`) reveals why the reader currently feels unstable or "buggy." 

To maintain the "zero external binary dependencies" rule, the engine utilizes custom Regular Expressions to parse complex XML document formats (`DOCX`, `XLSX`, `PPTX`, `FB2`, `XPS`). While creative, parsing relational XML structures with linear regex is highly fragile.

The scan has isolated **five critical "birth defects"** in the parsing engine. These cause severe multilingual rendering crashes, completely break PPTX slide extraction, swallow HTML files into raw source tag logs, and dump binary garbage for compressed help files (`CHM`).

Resolving these issues is the single most important step to achieving an **SSS+ Reading Experience**.

---

## 🚨 THE 5 ENGINE BIRTH DEFECTS

### 1. The Multilingual Silent Failure in Office Docs (`DOCX`, `XLSX`, `PPTX`, `XPS`)
*   **Where it lives:** `DocumentParser.kt` (`decodeXmlEntities` vs `decodeHtmlEntities`)
*   **The Bug:** 
    Your Office doc parsers pass text runs through `decodeXmlEntities(text)`. However, `decodeXmlEntities` **does not support decimal or hexadecimal numeric XML entities**:
    ```kotlin
    private fun decodeXmlEntities(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&") // MUST be last
    }
    ```
*   **Why it is catastrophic:** 
    Microsoft Office applications frequently encode non-ASCII characters—including Chinese, Japanese, Arabic, Cyrillic, math symbols, curly quotes (`&#x201c;`/`&#x201d;`), and em-dashes—as decimal (`&#20013;`) or hexadecimal (`&#x4e2d;`) entities.
    Because your decoder completely ignores these, **non-English documents and technical papers will render as an ugly, unreadable soup of raw codes (e.g. `&#20013;&#22269;`).**
*   **The Plan to Fix (SSS+):** 
    Swap out `decodeXmlEntities` entirely. Force DOCX, XLSX, and PPTX parsers to use `decodeHtmlEntities` (which already has robust regex handlers for numeric and hex unicode decoding).

---

### 2. Pure HTML Page Import Displays Raw Source Code
*   **Where it lives:** `BookFormat.kt` (extension mapping) & `TextReaderView.kt` (`parseTextBlocks`)
*   **The Bug:** 
    Luminar successfully imports `.html` or `.htm` files as `BookFormat.HTML`. However, in `TextReaderView.kt`'s `parseTextBlocks()`, HTML is not included in the `isCode` flag. It falls through to the plain text paragraph line-splitter:
    ```kotlin
    val isCode = format == BookFormat.CODE || format == BookFormat.JSON || format == BookFormat.XML
    // HTML is skipped, falls to else plain-text parser!
    ```
*   **Why it is catastrophic:** 
    The reader does not parse the HTML structure. It displays the **raw, unformatted source HTML code** (complete with `<html>`, `<head>`, `<style>`, `<div class="...">`, `<script>`, etc.) directly to the user as plain text paragraphs.
*   **The Plan to Fix (SSS+):** 
    1. Inside `DocumentParser.kt`'s `parseToTextUnsafe`, intercept the html extension:
       ```kotlin
       "html", "htm", "xhtml" -> stripHtmlToMarkdown(file.readText(Charsets.UTF_8))
       ```
    2. Your existing `stripHtmlToMarkdown` method is highly robust. By routing HTML through it on import, HTML files will instantly display as clean, structured, and beautiful Markdown paragraphs in `TextReaderView`!

---

### 3. PPTX Slides Swallow Spaced/Preserved Paragraphs Entirely
*   **Where it lives:** `DocumentParser.kt` (around line 396)
*   **The Bug:** 
    PowerPoint slide text runs are extracted using this strict regex:
    ```kotlin
    val textRegex = Regex("<a:t>(.*?)</a:t>", RegexOption.DOT_MATCHES_ALL)
    ```
    In the Microsoft OpenXML specification, PowerPoint slides frequently save text runs with whitespace preservation attributes, rendering the tag as `<a:t xml:space="preserve">text</a:t>` whenever leading or trailing spaces are involved.
*   **Why it is catastrophic:** 
    Because your regex requires *exactly* `<a:t>` with no attributes, **any slide text utilizing space preservation fails to match and is silently swallowed.** The user will open their presentation and find entire slides or major text blocks completely missing.
*   **The Plan to Fix (SSS+):** 
    Align the PPTX regex with the robust pattern you already use in your DOCX parser. Upgrade `textRegex` to allow wildcard namespaces and attributes:
    ```kotlin
    val textRegex = Regex("<a:t[^>]*>([\\s\\S]*?)</a:t>", RegexOption.DOT_MATCHES_ALL)
    ```

---

### 4. The Illusion of CHM Help File Parsing (Garbled Binary Matches)
*   **Where it lives:** `DocumentParser.kt` (around line 850)
*   **The Bug:** 
    Microsoft's Compiled HTML Help (`CHM`) format is actually a complex, multi-directory binary filesystem database (ITSS format).
    To parse it without external native libraries, your code reads the raw binary CHM file as an ISO_8859_1 String and runs a plain-text regex on it:
    ```kotlin
    val text = String(bytes, Charsets.ISO_8859_1)
    val bodyRegex = Regex("<body[^>]*>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE)
    ```
*   **Why it is catastrophic:** 
    Real-world CHM files compress their embedded HTML files using the proprietary Microsoft LZX algorithm. Running a plain-text regex directly over raw, LZX-scrambled binary bytes **matches absolutely nothing**.
    The parser will either fail silently, return a blank screen, or fall back to dumping garbled, non-printable binary strings (`extractTextFromBinary`), making the text look like scrambled cipher garbage.
*   **The Plan to Fix (SSS+):** 
    Since writing a complete LZX/ITSS decompressor in pure Kotlin is extremely heavy, protect your launch:
    *   Intercept `.chm` imports with a clean "Best-Effort decompressor" system warning dialog.
    *   Advise the user that CHM is a legacy, proprietary Windows format and provide a highly useful step-by-step tip on how to convert CHM → EPUB/PDF using [Calibre] directly before reading.

---

### 5. Multilingual Localized Headings Ignored in Word Documents (`DOCX`)
*   **Where it lives:** `DocumentParser.kt` (around line 23)
*   **The Bug:** 
    Word headings are detected using this strict regex:
    ```kotlin
    val HEADING_STYLE = Regex("""<w:pStyle\s+w:val="Heading(\d)"""")
    ```
*   **Why it is catastrophic:** 
    Word Heading style names are localized by Microsoft Word based on the document creator's system language. In French Word, headings are saved as `w:val="Titre1"`; in German, they are saved as `w:val="Überschrift1"`.
    Because your regex expects the exact English word `"Heading"`, **any Word document created on a non-English machine loses all heading sizes and renders them as standard paragraphs**, destroying document readability.
*   **The Plan to Fix (SSS+):** 
    Map Word paragraph heading styles by cross-referencing heading level properties (`w:outlineLvl`) instead of relying on the localized style name string, ensuring 100% accurate document structures regardless of language.

---

## 🛠️ THE SSS+ ENGINE ACTION MATRIX

To take your reading engine from a "prototype feeling" to a bulletproof, commercial-grade product, execute these non-coding structural changes in your next dev pass:

| Priority | Birth Defect | Complexity | User Impact | Fix Difficulty |
|---|---|---|---|---|
| **1** | **Office Multilingual Entities (Bug 1)** | Low | High | **Extremely Easy** (Swap `decodeXmlEntities` to `decodeHtmlEntities`) |
| **2** | **HTML Raw Source Clutter (Bug 2)** | Low | High | **Easy** (Route `.html` imports through `stripHtmlToMarkdown`) |
| **3** | **PPTX Swallowed Paragraphs (Bug 3)** | Low | High | **Extremely Easy** (Upgrade `<a:t>` regex to `<a:t[^>]*>`) |
| **4** | **DOCX Non-English Headings (Bug 5)** | Medium | Medium | **Medium** (Match `w:outlineLvl` tag instead of localized title) |
| **5** | **CHM Compression Block (Bug 4)** | High | Medium | **Easy** (Add conversion guidance dialog on import) |
