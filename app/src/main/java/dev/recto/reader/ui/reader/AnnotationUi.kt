package dev.recto.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.recto.reader.data.HighlightColour
import dev.recto.reader.data.db.AnnotationEntity
import dev.recto.reader.data.db.AnnotationKind

/**
 * The bar that appears over the page when text is selected: four colours,
 * a note button, and copy.
 *
 * Deliberately a floating bar rather than a bottom sheet - a sheet would
 * cover the very text you just selected, which is exactly what you want to
 * keep looking at while choosing a colour.
 *
 * @param caretX          caret tip measured from this card's left edge, or
 *                        null for no caret at all
 * @param caretAbove      true when the card is above the selection, so the
 *                        caret hangs off the bottom edge pointing down
 * @param defaultColour   index of the colour a plain tap uses, ringed in the
 *                        swatch row
 * @param onDefaultColour long-press a swatch to make it the default
 * @param darkTheme       picks the light or dark variant of each swatch
 */
@Composable
fun SelectionToolbar(
    onColour: (Int) -> Unit,
    onDefine: () -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    caretX: Dp? = null,
    caretAbove: Boolean = true,
    defaultColour: Int = 0,
    onDefaultColour: (Int) -> Unit = {},
    darkTheme: Boolean = false
) {
    // A caret welds the card to the words. Without one a floating panel is
    // just a panel that happens to be nearby; with one it is unmistakably
    // about *that* passage. Kindle, and every text-selection UI worth
    // copying, does this.
    //
    // Drawn as a separate triangle stacked against the card rather than
    // carved into the card's own shape, because a GenericShape covering both
    // would have to re-implement the rounded corners by hand.
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        if (caretX != null && !caretAbove) {
            Caret(offsetX = caretX, pointsDown = false)
        }

        SelectionToolbarCard(
            onColour = onColour,
            onDefine = onDefine,
            onNote = onNote,
            onCopy = onCopy,
            defaultColour = defaultColour,
            onDefaultColour = onDefaultColour,
            darkTheme = darkTheme
        )

        if (caretX != null && caretAbove) {
            Caret(offsetX = caretX, pointsDown = true)
        }
    }
}

/** The little triangle that points from the card to the selected words. */
@Composable
private fun Caret(offsetX: Dp, pointsDown: Boolean) {
    val shape = remember(pointsDown) {
        GenericShape { size, _ ->
            if (pointsDown) {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
            } else {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
            }
            close()
        }
    }

    // A Surface, not a Box with a background, and with the SAME
    // tonalElevation as the card. Material tints a surface by its elevation,
    // so a triangle painted with the raw `surface` colour comes out visibly
    // paler than the card it is supposed to be part of.
    Surface(
        modifier = Modifier
            // Half the caret's width, so the TIP lands on the anchor rather
            // than the triangle's left corner.
            .offset(x = offsetX - CaretWidth / 2)
            .size(width = CaretWidth, height = CaretHeight),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = ToolbarElevation
    ) {}
}

private val CaretWidth = 18.dp
private val CaretHeight = 9.dp

/**
 * Shared by the card and its caret so the two read as one piece of paper.
 * No shadow on the caret - a drop shadow on a 9dp triangle just muddies the
 * point, and the card's own shadow already lifts the whole thing.
 */
private val ToolbarElevation = 3.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectionToolbarCard(
    onColour: (Int) -> Unit,
    onDefine: () -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    defaultColour: Int,
    onDefaultColour: (Int) -> Unit,
    darkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    // Two rows, not one scrolling strip.
    //
    // The old version put four swatches and four text buttons in a single
    // horizontally scrolling Row - nine hit targets in a line, which meant
    // scrolling to reach Cancel and no visual grouping at all. Kindle splits
    // it: colours are one decision, actions are another, and neither should
    // require scrolling.
    //
    // Everything now fits without scroll because the actions are icon-and-
    // label pairs sized to the three that matter, and dismissal moved to
    // tapping the page - which the reader already supports.
    Surface(
        modifier = modifier.widthIn(max = 380.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = ToolbarElevation,
        shadowElevation = 10.dp
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Colours. Larger targets than before - 36dp reads as a
            // deliberate swatch rather than a dot, and clears the 32dp
            // minimum where mis-taps start.
            //
            // The swatches follow the page's own light/dark variants, so what
            // you tap is the colour you get. Showing the light yellow on a
            // Night page and then painting the dark one was quietly wrong.
            val haptics = LocalHapticFeedback.current

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HighlightColour.entries.forEachIndexed { index, colour ->
                    val isDefault = index == defaultColour

                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colour.colorFor(darkTheme))
                            // The default is ringed in the theme's accent at
                            // full width rather than badged with a tick: a
                            // tick sitting on a highlighter colour is hard to
                            // read on yellow and invisible on pink.
                            .border(
                                width = if (isDefault) 2.dp else 1.dp,
                                color = if (isDefault) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                        .copy(alpha = 0.6f)
                                },
                                shape = CircleShape
                            )
                            .combinedClickable(
                                onClick = { onColour(index) },
                                onLongClick = {
                                    // A long-press with no visible result
                                    // feels broken, and the ring is small.
                                    // The tick of haptic feedback is what
                                    // tells you the press registered.
                                    haptics.performHapticFeedback(
                                        HapticFeedbackType.LongPress
                                    )
                                    onDefaultColour(index)
                                }
                            )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Spacer(Modifier.height(6.dp))

            // Actions. Three, evenly weighted, no scroll. Cancel is gone:
            // tapping the page already dismisses a selection, and a Cancel
            // button in a floating bar is the least-wanted item competing for
            // the most-wanted space.
            Row(Modifier.fillMaxWidth()) {
                ToolbarAction("Define", Modifier.weight(1f), onDefine)
                ToolbarAction("Note", Modifier.weight(1f), onNote)
                ToolbarAction("Copy", Modifier.weight(1f), onCopy)
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Write or edit the note attached to a highlight.
 */
@Composable
fun NoteEditorDialog(
    annotation: AnnotationEntity,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(annotation.id) { mutableStateOf(annotation.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note") },
        text = {
            Column {
                // The quoted passage, so you can see what you are annotating.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = annotation.selectedText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Your note") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/**
 * Every highlight, note and bookmark in this book, in reading order.
 * Tapping one jumps to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookSheet(
    annotations: List<AnnotationEntity>,
    onJump: (AnnotationEntity) -> Unit,
    onEditNote: (AnnotationEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notebook",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (annotations.isNotEmpty()) {
                    TextButton(onClick = onExport) { Text("Export") }
                }
            }

            if (annotations.isEmpty()) {
                Text(
                    text = "Nothing saved yet. Long-press any word to highlight it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            } else {
                LazyColumn {
                    items(annotations, key = { it.id }) { a ->
                        AnnotationRow(
                            annotation = a,
                            onJump = { onJump(a) },
                            onEditNote = { onEditNote(a) },
                            onDelete = { onDelete(a.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotationRow(
    annotation: AnnotationEntity,
    onJump: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit
) {
    val isBookmark = annotation.kind == AnnotationKind.BOOKMARK.name
    val swatch =
        if (isBookmark) MaterialTheme.colorScheme.primary
        else HighlightColour.fromIndex(annotation.colour).onLight

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onJump)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(swatch)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = annotation.chapterTitle
                    ?: "Chapter ${annotation.chapterIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isBookmark) {
                Text(
                    text = "Bookmark",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = annotation.selectedText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )

        if (!annotation.note.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = annotation.note,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!isBookmark) {
                TextButton(onClick = onEditNote) {
                    Text(if (annotation.note.isNullOrBlank()) "Add note" else "Edit note")
                }
            }
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        )
    }
}

/** Renders the notebook as Markdown for sharing. */
fun buildExportText(
    bookTitle: String,
    author: String?,
    annotations: List<AnnotationEntity>
): String = buildString {
    appendLine("# $bookTitle")
    if (!author.isNullOrBlank()) appendLine("_${author}_")
    appendLine()
    appendLine("${annotations.size} annotations from Recto")
    appendLine()

    var lastChapter = -1
    annotations.sortedBy { it.startChar }.forEach { a ->
        if (a.chapterIndex != lastChapter) {
            lastChapter = a.chapterIndex
            appendLine()
            appendLine("## ${a.chapterTitle ?: "Chapter ${a.chapterIndex + 1}"}")
            appendLine()
        }
        if (a.kind == AnnotationKind.BOOKMARK.name) {
            appendLine("- Bookmark: ${a.selectedText.take(80)}...")
        } else {
            appendLine("> ${a.selectedText}")
            if (!a.note.isNullOrBlank()) {
                appendLine()
                appendLine(a.note)
            }
        }
        appendLine()
    }
}

/** A tinted overlay colour for the live selection, readable on any theme. */
fun selectionTint(onSurface: Color): Color = onSurface.copy(alpha = 0.22f)

/**
 * The band behind the sentence being read aloud.
 *
 * Lighter than the selection tint - it is on screen continuously while you
 * listen, and at selection strength that becomes a flickering grey bar
 * marching down the page. Just enough to follow with your eye.
 */
fun spokenTint(onSurface: Color): Color = onSurface.copy(alpha = 0.11f)
