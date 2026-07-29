package dev.recto.reader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.recto.reader.data.db.VocabularyEntity
import dev.recto.reader.data.lookup.Recall
import dev.recto.reader.data.lookup.Sm2

/**
 * Saved words, and the flashcard review that goes with them.
 *
 * Two modes in one sheet: a browse list, and a review session over the cards
 * that are due. Scheduling is SM-2 - the algorithm Anki uses - so words you
 * find hard come back in minutes and words you know drift out to months.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularySheet(
    words: List<VocabularyEntity>,
    dueCount: Int,
    reviewQueue: List<VocabularyEntity>,
    onStartReview: () -> Unit,
    onAnswer: (VocabularyEntity, Recall) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reviewing by remember { mutableStateOf(false) }

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
                Column(Modifier.weight(1f)) {
                    Text("Vocabulary", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = when {
                            words.isEmpty() -> "No words saved yet"
                            dueCount > 0 -> "${words.size} words - $dueCount due"
                            else -> "${words.size} words - all caught up"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (dueCount > 0 && !reviewing) {
                    Button(onClick = {
                        onStartReview()
                        reviewing = true
                    }) { Text("Review") }
                }
                if (reviewing) {
                    TextButton(onClick = { reviewing = false }) { Text("Done") }
                }
            }

            if (reviewing) {
                ReviewPane(
                    queue = reviewQueue,
                    onAnswer = onAnswer,
                    onFinished = { reviewing = false }
                )
            } else if (words.isEmpty()) {
                Text(
                    text = "Long-press a word while reading, then tap Save on the " +
                        "definition card. Saved words become flashcards.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(words, key = { it.id }) { w -> WordRow(w, onDelete) }
                }
            }
        }
    }
}

@Composable
private fun ReviewPane(
    queue: List<VocabularyEntity>,
    onAnswer: (VocabularyEntity, Recall) -> Unit,
    onFinished: () -> Unit
) {
    val card = queue.firstOrNull()

    if (card == null) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("All done", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Nothing else due right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onFinished) { Text("Back to list") }
        }
        return
    }

    // Reset the reveal every time the card changes, or the next word would
    // arrive with its answer already showing.
    var revealed by remember(card.id) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
    ) {
        Text(
            text = "${queue.size} left",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { revealed = true }
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = card.word,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                card.phonetic?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // The context sentence with the word masked. This is the whole
                // point of storing context: recalling a word in the sentence
                // you met it in is far easier than recalling it cold.
                card.contextSentence?.let { sentence ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (revealed) {
                            sentence
                        } else {
                            sentence.replace(
                                Regex("(?i)\\b${Regex.escape(card.word)}\\w*"),
                                "\u2026"
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                AnimatedVisibility(visible = revealed) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(16.dp))
                        card.partOfSpeech?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = card.definition,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (!revealed) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Tap to reveal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (revealed) {
            // Each button shows when the card would next appear, so the
            // choice is informed rather than a guess.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Recall.entries.forEach { recall ->
                    OutlinedButton(
                        onClick = { onAnswer(card, recall) },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout
                            .PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(recall.label, style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = Sm2.previewInterval(card, recall),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordRow(word: VocabularyEntity, onDelete: (Long) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = word.word,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (word.timesReviewed > 0) {
                Text(
                    text = "${word.timesCorrect}/${word.timesReviewed}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { onDelete(word.id) }) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        }
        Text(
            text = word.definition,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        word.bookTitle?.let {
            Text(
                text = "from $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        )
    }
}
