// app/src/main/java/com/luminar/reader/presentation/reader/EpubReaderView.kt
package com.luminar.reader.presentation.reader

import com.luminar.reader.data.epub.EpubChapter
import com.luminar.reader.data.model.AppTheme
import com.luminar.reader.data.model.BookFormat
import com.luminar.reader.data.model.FontScale

/**
 * Converts EPUB chapters into a single Markdown-style string,
 * then delegates to [TextReaderView] for rendering.
 *
 * Each chapter becomes:
 *   # Chapter Title
 *   (chapter text content, already with markdown-style formatting
 *    injected by EpubParser's htmlToPlainText)
 *   ---
 */
fun epubChaptersToMarkdown(chapters: List<EpubChapter>): String {
    return buildString {
        for ((index, chapter) in chapters.withIndex()) {
            append("# ")
            appendLine(chapter.title)
            appendLine()
            appendLine(chapter.textContent)
            if (index < chapters.lastIndex) {
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }
}
