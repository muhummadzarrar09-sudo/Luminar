package dev.recto.reader.ui.reader

import androidx.compose.ui.text.TextLayoutResult

/**
 * Where the selected words actually sit on screen, and where a toolbar
 * pointing at them should go.
 *
 * Two coordinate spaces are in play and mixing them up puts the card in the
 * wrong place:
 *
 *  - [boundsOf] returns whatever space the caller's `dx`/`dy` are in. The
 *    reader passes the text block's WINDOW position, so it gets window space.
 *  - [place] expects bounds in CONTAINER space - relative to the Box the
 *    toolbar is positioned in. The reader converts by subtracting that Box's
 *    own window position.
 *
 * Going via the window rather than assuming the container sits at the origin
 * keeps this correct if anything is ever wrapped around the reader.
 *
 * This is deliberately plain Kotlin with no Compose UI dependency beyond
 * TextLayoutResult. Placement is fiddly, every edge case is a rectangle
 * problem, and rectangle problems are far easier to reason about when they
 * are not tangled up in a composable.
 */

/**
 * The vertical extent of a selection, plus the horizontal point the toolbar
 * should aim at on each side.
 *
 * The two anchors differ because a selection running across several lines
 * starts and ends in different places. Pointing at the middle of the whole
 * block would aim at a spot the reader never touched - typically the middle
 * of a fully-selected line between the two ends.
 */
data class SelectionBounds(
    /** Top of the first selected line. */
    val top: Float,
    /** Bottom of the last selected line. */
    val bottom: Float,
    /** Centre of the selected run on the first line. */
    val topAnchorX: Float,
    /** Centre of the selected run on the last line. */
    val bottomAnchorX: Float
)

/** A resolved position for the toolbar. */
data class ToolbarPlacement(
    val x: Int,
    val y: Int,
    /** True when the card sits above the selection and its caret points down. */
    val above: Boolean,
    /** Caret tip, relative to the card's own left edge. */
    val caretX: Float
)

object SelectionAnchor {

    /**
     * Measures a selection.
     *
     * @param from  start offset within this page's text
     * @param to    end offset, exclusive
     * @param dx    x of the text block within the reader Box
     * @param dy    y of the text block within the reader Box
     */
    fun boundsOf(
        layout: TextLayoutResult,
        from: Int,
        to: Int,
        dx: Float,
        dy: Float
    ): SelectionBounds? {
        val length = layout.layoutInput.text.length
        if (length == 0) return null

        val start = from.coerceIn(0, length)
        val end = to.coerceIn(0, length)
        if (end <= start) return null

        val firstLine = layout.getLineForOffset(start)
        // end is exclusive, so the last selected character is at end - 1.
        // Using `end` directly puts a selection ending exactly at a line
        // break on the following line, and the toolbar then points at a
        // line containing none of the selection.
        val lastLine = layout.getLineForOffset((end - 1).coerceAtLeast(start))

        val startX = layout.getHorizontalPosition(start, usePrimaryDirection = true)
        val endX = layout.getHorizontalPosition(end, usePrimaryDirection = true)

        val topAnchor: Float
        val bottomAnchor: Float
        if (firstLine == lastLine) {
            val mid = (startX + endX) / 2f
            topAnchor = mid
            bottomAnchor = mid
        } else {
            // First line runs from the selection start to the line's end;
            // last line runs from the line's start to the selection end.
            topAnchor = (startX + layout.getLineRight(firstLine)) / 2f
            bottomAnchor = (layout.getLineLeft(lastLine) + endX) / 2f
        }

        return SelectionBounds(
            top = layout.getLineTop(firstLine) + dy,
            bottom = layout.getLineBottom(lastLine) + dy,
            topAnchorX = topAnchor + dx,
            bottomAnchorX = bottomAnchor + dx
        )
    }

    /**
     * Chooses a spot for the toolbar.
     *
     * The rules, in order:
     *
     *  1. Above the selection if the card fits there.
     *  2. Otherwise below, if it fits there.
     *  3. Otherwise below anyway.
     *
     * Rule 3 is the interesting one. When a selection is so tall that neither
     * side has room - selecting a whole page, say - something has to be
     * covered. Covering the END of the selection is much better than covering
     * the start: you can still see what you began selecting, and the first
     * line is what identifies the passage. Preferring whichever side had more
     * space, which is the obvious approach, lands the card over the opening
     * words about half the time.
     *
     * @param containerWidth   width of the reader Box
     * @param containerHeight  height of the reader Box
     * @param toolbarWidth     measured width of the card
     * @param toolbarHeight    measured height of the card, caret included
     * @param topBlocked       pixels reserved at the top by insets and chrome
     * @param bottomBlocked    pixels reserved at the bottom by insets and chrome
     * @param leftBlocked      pixels reserved at the left, e.g. a landscape
     *                         navigation bar or a display cutout
     * @param rightBlocked     pixels reserved at the right
     * @param gap              breathing room between card and selection
     * @param margin           minimum distance from the container edges
     * @param caretInset       how far the caret must stay from the card's ends
     */
    fun place(
        bounds: SelectionBounds,
        containerWidth: Int,
        containerHeight: Int,
        toolbarWidth: Int,
        toolbarHeight: Int,
        topBlocked: Int,
        bottomBlocked: Int,
        leftBlocked: Int,
        rightBlocked: Int,
        gap: Int,
        margin: Int,
        caretInset: Float
    ): ToolbarPlacement {
        // The band the card is allowed to occupy: inside the margins, and
        // clear of whatever chrome is currently on screen. This is what makes
        // the toolbar behave when you select text with the top bar open -
        // otherwise it tucks underneath and you lose the colour swatches.
        val lo = maxOf(margin, topBlocked)
        val hi = minOf(containerHeight - margin, containerHeight - bottomBlocked)

        val roomAbove = bounds.top - gap - lo
        val roomBelow = hi - (bounds.bottom + gap)

        val above = when {
            roomAbove >= toolbarHeight -> true
            roomBelow >= toolbarHeight -> false
            else -> false
        }

        val rawY = if (above) {
            bounds.top - gap - toolbarHeight
        } else {
            bounds.bottom + gap
        }

        // coerceIn throws if the range is empty, which happens on a short
        // screen where the card is taller than the free band.
        val yMax = maxOf(lo, hi - toolbarHeight)
        val y = rawY.coerceIn(lo.toFloat(), yMax.toFloat())

        val anchorX = if (above) bounds.topAnchorX else bounds.bottomAnchorX

        // In landscape the navigation bar sits on one side, so the usable
        // band is not symmetric. Centring on the container and hoping would
        // push the card under the bar on that edge.
        val xLo = maxOf(margin, leftBlocked)
        val xHi = minOf(containerWidth - margin, containerWidth - rightBlocked)

        val rawX = anchorX - toolbarWidth / 2f
        val xMax = maxOf(xLo, xHi - toolbarWidth)
        val x = rawX.coerceIn(xLo.toFloat(), xMax.toFloat())

        // The caret follows the anchor, but has to stay clear of the card's
        // rounded corners or it grows out of the side of the curve.
        val inset = minOf(caretInset, toolbarWidth / 2f)
        val caret = (anchorX - x).coerceIn(inset, toolbarWidth - inset)

        return ToolbarPlacement(
            x = x.toInt(),
            y = y.toInt(),
            above = above,
            caretX = caret
        )
    }
}
