package dev.recto.reader.ui.reader

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

/**
 * One rendered page: a slice of a chapter's text that fits the screen.
 */
data class Page(
    val chapterIndex: Int,
    val startChar: Int,
    val endChar: Int,
    val text: String
)

/**
 * Splits chapters into screen-sized pages by actually measuring text.
 *
 * This is what separates a real reader from a scrolling text view. We ask
 * Compose's TextMeasurer to lay the chapter out at the exact page width, then
 * walk the resulting line metrics to find page boundaries. No guessing at
 * characters-per-page, so pagination is correct for any font size, margin or
 * screen.
 *
 * Performance note: exactly ONE layout pass per chapter, not one per page.
 * The naive version re-measures the remaining text for every page, which is
 * O(n^2) and visibly hangs on a full-length novel. Here we measure once with
 * unbounded height and slice using getLineTop/getLineBottom, which is linear.
 */
object Paginator {

    fun paginate(
        chapters: List<CharSequence>,
        measurer: TextMeasurer,
        style: TextStyle,
        widthPx: Int,
        heightPx: Int
    ): List<Page> {
        if (widthPx <= 0 || heightPx <= 0) return emptyList()

        val pages = mutableListOf<Page>()

        chapters.forEachIndexed { chapterIndex, raw ->
            val text = raw.toString()
            if (text.isBlank()) return@forEachIndexed

            val layout: TextLayoutResult = try {
                measurer.measure(
                    text = text,
                    style = style,
                    // Unbounded height: we want every line laid out so we can
                    // slice it ourselves.
                    constraints = Constraints(maxWidth = widthPx)
                )
            } catch (_: Throwable) {
                // A chapter we cannot lay out should not take the book down.
                return@forEachIndexed
            }

            if (layout.lineCount == 0) return@forEachIndexed

            var line = 0
            while (line < layout.lineCount) {
                val pageTop = layout.getLineTop(line)

                // Extend while the next line still fits under the page height.
                // Starting at `line` guarantees at least one line per page, so
                // the loop always advances even if a single line is oversized.
                var last = line
                while (
                    last + 1 < layout.lineCount &&
                    layout.getLineBottom(last + 1) - pageTop <= heightPx
                ) {
                    last++
                }

                val start = layout.getLineStart(line)
                val end = layout.getLineEnd(last, visibleEnd = false)
                    .coerceIn(start, text.length)

                if (end > start) {
                    pages += Page(
                        chapterIndex = chapterIndex,
                        startChar = start,
                        endChar = end,
                        text = text.substring(start, end).trim('\n')
                    )
                }

                line = last + 1
            }
        }

        return pages
    }
}
