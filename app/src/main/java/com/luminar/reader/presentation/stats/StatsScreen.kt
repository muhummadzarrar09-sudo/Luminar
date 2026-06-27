package com.luminar.reader.presentation.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.luminar.reader.R
import com.luminar.reader.presentation.theme.LuminarGold
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Reading Stats") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back_24), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LuminarGold)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item { TodaySection(uiState) }
                item { WeeklyChartSection(uiState.weeklyBars) }
                item { AllTimeSection(uiState) }
                item {
                    Text(
                        "Recently Read",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(uiState.perBookStats, key = { it.book.id }) { bookStat ->
                    BookStatItem(bookStat)
                }
            }
        }
    }
}

@Composable
private fun TodaySection(uiState: StatsUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${uiState.todayMinutes} min today",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        val sub = if (uiState.currentStreakDays > 0) "${uiState.currentStreakDays} day streak 🔥" else "Start your streak!"
        Text(
            text = sub,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeeklyChartSection(bars: List<DayBar>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "This Week",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val maxMins = bars.maxOfOrNull { it.minutes }?.coerceAtLeast(30) ?: 30
            val primaryColor = MaterialTheme.colorScheme.primary
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

            Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val bottomLabelHeight = 24.dp.toPx()
                val chartHeight = canvasHeight - bottomLabelHeight
                val barCount = bars.size
                val totalGapWidth = canvasWidth * 0.4f
                val gapWidth = totalGapWidth / (barCount + 1)
                val barWidth = (canvasWidth - totalGapWidth) / barCount

                bars.forEachIndexed { idx, bar ->
                    val x = gapWidth + idx * (barWidth + gapWidth)
                    val barHeightFraction = bar.minutes.toFloat() / maxMins.toFloat()
                    val barH = (chartHeight * barHeightFraction).coerceAtLeast(if (bar.minutes > 0) 8.dp.toPx() else 2.dp.toPx())
                    val y = chartHeight - barH

                    val color = if (bar.isToday) primaryColor else primaryColor.copy(alpha = 0.7f)

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )

                    if (bar.isToday) {
                        drawCircle(
                            color = primaryColor,
                            radius = 3.dp.toPx(),
                            center = Offset(x + barWidth / 2f, (y - 8.dp.toPx()).coerceAtLeast(4.dp.toPx()))
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                bars.forEach { bar ->
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bar.isToday) MaterialTheme.colorScheme.primary else labelColor,
                        fontWeight = if (bar.isToday) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun AllTimeSection(uiState: StatsUiState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(Modifier.weight(1f), uiState.allTimeBooksFinished.toString(), "Books Finished")
        StatCard(Modifier.weight(1f), "${uiState.allTimeTotalMinutes / 60}", "Total Hours")
        StatCard(Modifier.weight(1f), "${uiState.avgSessionMinutes}m", "Avg Session")
    }
}

@Composable
private fun StatCard(modifier: Modifier, largeNum: String, label: String) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(largeNum, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BookStatItem(bookStat: BookStatSummary) {
    val book = bookStat.book
    val coverFile = remember(book.coverPath) { book.coverPath?.let(::File)?.takeIf { it.exists() } }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp, 60.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surface)) {
                if (coverFile != null) {
                    AsyncImage(model = coverFile, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(bookStat.percentComplete).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                }
                Spacer(Modifier.height(6.dp))
                val hours = bookStat.totalMinutes / 60f
                val daysAgo = if (bookStat.lastReadAt != null) {
                    TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - bookStat.lastReadAt).coerceAtLeast(0)
                } else 0
                Text(
                    String.format("%.1f hours total · Last read %d days ago", hours, daysAgo),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
