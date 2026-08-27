package dev.recto.reader.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.recto.reader.data.db.BookTotal
import dev.recto.reader.data.stats.ReadingStats

/**
 * Everything the app knows about your reading, on one sheet.
 */
data class StatsSnapshot(
    val todayMillis: Long = 0,
    val goalMinutes: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalMillis: Long = 0,
    val sessionCount: Int = 0,
    val daysWithReading: Set<Int> = emptySet(),
    val dailyMillis: Map<Int, Long> = emptyMap(),
    val topBooks: List<BookTotal> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsSheet(
    stats: StatsSnapshot,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Your reading", style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(16.dp))

            // Today, against the goal if there is one.
            val todayMinutes = (stats.todayMillis / 60_000).toInt()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = ReadingStats.formatDuration(stats.todayMillis),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (stats.goalMinutes > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                        (todayMinutes.toFloat() / stats.goalMinutes).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (todayMinutes >= stats.goalMinutes) {
                        "Goal reached"
                    } else {
                        "${stats.goalMinutes - todayMinutes} min to your goal"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(22.dp))

            Row(Modifier.fillMaxWidth()) {
                StatCell("Streak", "${stats.currentStreak}", "days", Modifier.weight(1f))
                StatCell("Longest", "${stats.longestStreak}", "days", Modifier.weight(1f))
                StatCell(
                    "All time",
                    ReadingStats.formatDurationShort(stats.totalMillis),
                    "read",
                    Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Last few months",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Heatmap(stats.dailyMillis)

            if (stats.topBooks.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Most read",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                stats.topBooks.forEach { book ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = book.bookTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = ReadingStats.formatDuration(book.millisRead),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (stats.sessionCount == 0) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Read for a minute or two and your stats will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "$label - $unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * GitHub-style contribution grid: one column per week, seven rows for the
 * weekdays. Intensity is bucketed rather than linear, because a single
 * three-hour Sunday would otherwise wash out every ordinary day to nothing.
 */
@Composable
private fun Heatmap(dailyMillis: Map<Int, Long>) {
    val days = ReadingStats.heatmapDays()
    val weeks = days.chunked(7)
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { day ->
                    val minutes = (dailyMillis[day] ?: 0L) / 60_000
                    val alpha = when {
                        minutes <= 0 -> 0f
                        minutes < 10 -> 0.30f
                        minutes < 25 -> 0.52f
                        minutes < 60 -> 0.76f
                        else -> 1f
                    }
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (alpha == 0f) empty else base.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}
