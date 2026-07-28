package dev.recto.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.recto.reader.data.LineSpacing
import dev.recto.reader.data.PageMargin
import dev.recto.reader.data.ReaderFont
import dev.recto.reader.data.ReaderSettings
import dev.recto.reader.data.ReaderTheme

/**
 * Kindle's "Aa" sheet: theme, font, size, spacing, margins.
 *
 * Everything applies live behind the sheet, which is why the sheet is
 * deliberately short - you want to see the effect on real text, not on a
 * preview swatch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onTheme: (ReaderTheme) -> Unit,
    onFont: (ReaderFont) -> Unit,
    onFontSize: (Int) -> Unit,
    onLineSpacing: (LineSpacing) -> Unit,
    onMargin: (PageMargin) -> Unit,
    onJustify: (Boolean) -> Unit,
    onVolumeKeys: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onWarmth: (Float) -> Unit,
    onDim: (Float) -> Unit,
    onUseReaderBrightness: (Boolean) -> Unit,
    onBrightness: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            SectionLabel("Theme")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReaderTheme.entries.forEach { theme ->
                    ThemeSwatch(
                        theme = theme,
                        selected = settings.theme == theme,
                        onClick = { onTheme(theme) }
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            SectionLabel("Text size")
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onFontSize(settings.fontSizeSp - 1) },
                    enabled = settings.fontSizeSp > ReaderSettings.MIN_FONT_SP
                ) {
                    Text("A", fontSize = 14.sp)
                }

                Text(
                    text = "${settings.fontSizeSp}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                OutlinedButton(
                    onClick = { onFontSize(settings.fontSizeSp + 1) },
                    enabled = settings.fontSizeSp < ReaderSettings.MAX_FONT_SP
                ) {
                    Text("A", fontSize = 22.sp)
                }
            }

            Spacer(Modifier.height(22.dp))

            SectionLabel("Comfort")

            LabelledSlider(
                label = "Warmth",
                detail = "Amber tint. Helps you sleep after reading at night.",
                value = settings.warmth,
                onChange = onWarmth
            )

            Spacer(Modifier.height(10.dp))

            LabelledSlider(
                label = "Extra dim",
                detail = "Goes darker than the phone's own minimum.",
                value = settings.dim,
                onChange = onDim
            )

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onUseReaderBrightness(!settings.useReaderBrightness) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Reader brightness", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Set brightness just for Recto",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.useReaderBrightness,
                    onCheckedChange = onUseReaderBrightness
                )
            }

            if (settings.useReaderBrightness) {
                Slider(
                    value = settings.brightness,
                    onValueChange = onBrightness,
                    valueRange = 0f..1f
                )
            }

            Spacer(Modifier.height(22.dp))

            SectionLabel("Font")
            ChipRow(
                options = ReaderFont.entries.map { it to it.label },
                selected = settings.font,
                onSelect = onFont
            )

            Spacer(Modifier.height(18.dp))

            SectionLabel("Line spacing")
            ChipRow(
                options = LineSpacing.entries.map { it to it.label },
                selected = settings.lineSpacing,
                onSelect = onLineSpacing
            )

            Spacer(Modifier.height(18.dp))

            SectionLabel("Margins")
            ChipRow(
                options = PageMargin.entries.map { it to it.label },
                selected = settings.margin,
                onSelect = onMargin
            )

            Spacer(Modifier.height(18.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onJustify(!settings.justify) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Justify text", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Straight right edge, like a printed book",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = settings.justify, onCheckedChange = onJustify)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onVolumeKeys(!settings.volumeKeysTurnPages) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Volume keys turn pages", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Volume down for next, up for previous",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.volumeKeysTurnPages,
                    onCheckedChange = onVolumeKeys
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onKeepScreenOn(!settings.keepScreenOn) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Keep screen on", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Stop the display dimming while you read",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = settings.keepScreenOn, onCheckedChange = onKeepScreenOn)
            }
        }
    }
}

/** A slider with a name and a one-line explanation of what it actually does. */
@Composable
private fun LabelledSlider(
    label: String,
    detail: String,
    value: Float,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) }
            )
        }
    }
}

/**
 * A round swatch showing the theme's actual page and ink colours, with a
 * sample letter. Faster to recognise than a name.
 */
@Composable
private fun ThemeSwatch(
    theme: ReaderTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    val ring =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(theme.background)
                .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Aa",
                color = theme.text,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = theme.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Table of contents. Uses the EPUB's own navigation document where present,
 * so the entries read the way the publisher wrote them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableOfContentsSheet(
    entries: List<TocEntry>,
    currentChapter: Int,
    onSelect: (TocEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Contents",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            )

            if (entries.isEmpty()) {
                Text(
                    text = "This book has no chapter list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            } else {
                LazyColumn {
                    items(
                        items = entries,
                        key = { it.chapterIndex }
                    ) { entry ->
                        val isCurrent = entry.chapterIndex == currentChapter
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(entry) }
                                .background(
                                    if (isCurrent) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (isCurrent) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${entry.percent}%",
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

/** One row in the contents list. */
data class TocEntry(
    val chapterIndex: Int,
    val title: String,
    val percent: Int
)
