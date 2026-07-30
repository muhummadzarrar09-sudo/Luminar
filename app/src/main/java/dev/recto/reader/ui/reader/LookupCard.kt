package dev.recto.reader.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.recto.reader.data.lookup.DictionaryEntry
import dev.recto.reader.data.lookup.LookupState
import dev.recto.reader.data.lookup.WikipediaSummary

/**
 * The lookup card: definition, Wikipedia, and a web search escape hatch.
 *
 * A bottom sheet rather than a floating popover, because definitions are
 * variable-length and a popover either clips a long entry or covers the page.
 * The sheet is capped so the top of the page - and usually the word you
 * looked up - stays visible behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupSheet(
    word: String,
    contextSentence: String?,
    dictionary: LookupState<DictionaryEntry>,
    wikipedia: LookupState<WikipediaSummary>,
    isSaved: Boolean,
    onSave: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember(word) { mutableIntStateOf(0) }
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .navigationBarsPadding()
        ) {
            // Header: the word, its pronunciation, and save
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    // A selected phrase can be a whole sentence, so the style
                    // steps down and wraps rather than overflowing the card.
                    val isPhrase = word.contains(' ')
                    Text(
                        text = word,
                        style = if (isPhrase) {
                            MaterialTheme.typography.bodyLarge
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    val phonetic = (dictionary as? LookupState.Success)?.data?.phonetic
                    if (!phonetic.isNullOrBlank()) {
                        Text(
                            text = phonetic,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val audio = (dictionary as? LookupState.Success)?.data?.audioUrl
                if (!audio.isNullOrBlank()) {
                    TextButton(onClick = { onPlayAudio(audio) }) { Text("Play") }
                }

                TextButton(
                    onClick = onSave,
                    enabled = !isSaved && dictionary is LookupState.Success
                ) {
                    Text(if (isSaved) "Saved" else "Save")
                }
            }

            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Dictionary") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Wikipedia") }
                )
            }

            if (!contextSentence.isNullOrBlank() && word.contains(' ')) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = contextSentence,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                when (tab) {
                    0 -> DictionaryPane(
                        state = dictionary,
                        onRetry = onRetry,
                        onSearchWeb = { openUrl(context, googleUrl(word)) }
                    )
                    else -> WikipediaPane(
                        state = wikipedia,
                        onRetry = onRetry,
                        onSearchWeb = { openUrl(context, googleUrl(word)) }
                    )
                }
            }

            // Web search: the honest fallback for proper nouns, jargon and
            // anything a dictionary will never carry.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { openUrl(context, googleUrl(word)) },
                    label = { Text("Search the web") }
                )
                if (!word.contains(' ')) {
                    AssistChip(
                        onClick = {
                            openUrl(
                                context,
                                "https://www.google.com/search?q=" + enc("define $word")
                            )
                        },
                        label = { Text("Define on Google") }
                    )
                } else {
                    // For a phrase, "what does X mean" is what a person would
                    // actually type - and it is what surfaces idiom and
                    // metaphor explanations rather than dictionary stubs.
                    AssistChip(
                        onClick = {
                            openUrl(
                                context,
                                "https://www.google.com/search?q=" +
                                    enc("what does \"$word\" mean")
                            )
                        },
                        label = { Text("Explain this phrase") }
                    )
                }
                (wikipedia as? LookupState.Success)?.data?.let { summary ->
                    AssistChip(
                        onClick = { openUrl(context, summary.pageUrl) },
                        label = { Text("Full article") }
                    )
                }
            }
        }
    }
}

@Composable
private fun DictionaryPane(
    state: LookupState<DictionaryEntry>,
    onRetry: () -> Unit,
    onSearchWeb: () -> Unit
) {
    when (state) {
        is LookupState.Idle, is LookupState.Loading -> Loading()

        is LookupState.Empty -> NotFound(state.message, onSearchWeb = onSearchWeb)

        is LookupState.Failed -> NotFound(
            state.message,
            onSearchWeb = onSearchWeb,
            onRetry = onRetry
        )

        is LookupState.Success -> Column {
            state.data.entries.forEach { pos ->
                Text(
                    text = pos.partOfSpeech,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))

                pos.senses.forEachIndexed { i, sense ->
                    Row(Modifier.padding(bottom = 8.dp)) {
                        Text(
                            text = "${i + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(22.dp)
                        )
                        Column {
                            Text(
                                text = sense.definition,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            sense.example?.let {
                                Text(
                                    text = "\u201C$it\u201D",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                if (pos.synonyms.isNotEmpty()) {
                    Text(
                        text = "Similar: " + pos.synonyms.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (pos.antonyms.isNotEmpty()) {
                    Text(
                        text = "Opposite: " + pos.antonyms.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun WikipediaPane(
    state: LookupState<WikipediaSummary>,
    onRetry: () -> Unit,
    onSearchWeb: () -> Unit
) {
    when (state) {
        is LookupState.Idle, is LookupState.Loading -> Loading()

        is LookupState.Empty -> NotFound(state.message, onSearchWeb = onSearchWeb)

        is LookupState.Failed -> NotFound(
            state.message,
            onSearchWeb = onSearchWeb,
            onRetry = onRetry
        )

        is LookupState.Success -> Column {
            state.data.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(text = state.data.extract, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Shown when a lookup finds nothing.
 *
 * The message states plainly what was not found, and directly under it sits
 * ONE prominent button: search the web. Nothing found is exactly the moment
 * the user needs an obvious next step, not a row of small equal-weight chips
 * to choose between.
 */
@Composable
private fun NotFound(
    message: String,
    onSearchWeb: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onSearchWeb,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Search the web")
        }

        if (onRetry != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Try again")
            }
        }
    }
}

private fun enc(s: String): String = Uri.encode(s)

/** A plain web search, which is the right answer for a phrase or metaphor. */
private fun googleUrl(term: String): String =
    "https://www.google.com/search?q=" + enc(
        if (term.contains(' ')) "\"$term\" meaning" else "$term meaning"
    )

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/**
 * Plays a pronunciation clip.
 *
 * MediaPlayer rather than a full media library: these are 20 KB one-shot mp3s
 * with no controls, no queue and no lifecycle to speak of. It releases itself
 * on completion and on error, so a dead URL cannot leak the player.
 */
fun playPronunciation(context: android.content.Context, url: String) {
    runCatching {
        android.media.MediaPlayer().apply {
            setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(url)
            setOnPreparedListener { it.start() }
            setOnCompletionListener { it.release() }
            setOnErrorListener { mp, _, _ -> mp.release(); true }
            prepareAsync()
        }
    }
}
