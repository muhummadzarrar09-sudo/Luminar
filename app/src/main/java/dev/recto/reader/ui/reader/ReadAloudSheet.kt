package dev.recto.reader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.recto.reader.data.ReaderSettings
import dev.recto.reader.data.tts.VoiceOption

/**
 * Read-aloud controls.
 *
 * A sheet rather than a bar over the page, because the page is the thing you
 * are supposed to be following while it reads. It can be dismissed and
 * playback carries on - the notification and the top-bar menu both stop it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadAloudSheet(
    speaking: Boolean,
    settings: ReaderSettings,
    voices: List<VoiceOption>,
    sleepRemaining: Int?,
    error: String?,
    onToggle: () -> Unit,
    onSkip: (Boolean) -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onVoice: (String?) -> Unit,
    onSleep: (Int) -> Unit,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // weight before verticalScroll. The other order measures the Column
        // unbounded, decides it fits, and never scrolls.
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Read aloud", style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Uses whichever text-to-speech engine Android is set " +
                    "to. For neural voices that work offline, install the " +
                    "sherpa-onnx Piper engine and pick it in Android " +
                    "settings under Accessibility.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            error?.let { message ->
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onDismissError) { Text("Dismiss") }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Transport. Big centre target because it is the one control you
            // reach for without looking.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { onSkip(false) }, enabled = speaking) {
                    Text("Back")
                }

                Spacer(Modifier.width(16.dp))

                FilledIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape
                ) {
                    // Glyphs rather than icon assets: Recto ships no icon
                    // pack, and two characters beat adding one for this.
                    Text(
                        text = if (speaking) "\u23F8" else "\u25B6",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Spacer(Modifier.width(16.dp))

                OutlinedButton(onClick = { onSkip(true) }, enabled = speaking) {
                    Text("Next")
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionCaption("Speed")
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = settings.ttsSpeed,
                    onValueChange = onSpeed,
                    valueRange = ReaderSettings.MIN_TTS_SPEED..ReaderSettings.MAX_TTS_SPEED,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatMultiplier(settings.ttsSpeed),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(44.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            SectionCaption("Pitch")
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = settings.ttsPitch,
                    onValueChange = onPitch,
                    valueRange = ReaderSettings.MIN_TTS_PITCH..ReaderSettings.MAX_TTS_PITCH,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatMultiplier(settings.ttsPitch),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(44.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            SectionCaption(
                if (sleepRemaining != null) {
                    "Sleep timer - ${sleepRemaining} min left"
                } else {
                    "Sleep timer"
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0, 15, 30, 45, 60).forEach { minutes ->
                    FilterChip(
                        selected = if (minutes == 0) {
                            sleepRemaining == null
                        } else {
                            sleepRemaining != null && minutes == settings.ttsSleepMinutes
                        },
                        onClick = { onSleep(minutes) },
                        label = { Text(if (minutes == 0) "Off" else "$minutes min") }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            SectionCaption("Voice")
            Spacer(Modifier.height(8.dp))

            if (voices.isEmpty()) {
                Text(
                    text = "No voices found for your language. Check that a " +
                        "text-to-speech engine is installed and has its " +
                        "voice data downloaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Capped and scrollable: some engines expose forty voices,
                // and an unbounded list here would push the transport
                // controls off the top of the sheet.
                Column(
                    Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    voices.forEach { voice ->
                        VoiceRow(
                            voice = voice,
                            selected = voice.id == settings.ttsVoiceId,
                            onClick = { onVoice(voice.id) }
                        )
                    }
                }

                if (settings.ttsVoiceId != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { onVoice(null) }) {
                        Text("Use the engine default")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun VoiceRow(
    voice: VoiceOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    // Plain Surface plus Modifier.clickable, not the Surface(onClick = ..)
    // overload - that one is ExperimentalMaterial3Api and would need an
    // opt-in on a stable screen for no benefit.
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = voice.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(voice.localeTag)
                        // Called out because it is the difference between a
                        // voice that works on a plane and one that does not.
                        append(if (voice.offline) " - offline" else " - needs internet")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Text("\u2713", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** "1.5x" without pulling in locale-aware number formatting for one label. */
private fun formatMultiplier(value: Float): String {
    val tenths = Math.round(value * 10f)
    return "${tenths / 10}.${tenths % 10}x"
}
