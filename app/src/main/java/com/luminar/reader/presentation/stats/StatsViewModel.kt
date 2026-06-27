package com.luminar.reader.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminar.reader.data.local.db.ReadingSessionDao
import com.luminar.reader.data.model.Book
import com.luminar.reader.data.model.ReadingSession
import com.luminar.reader.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookStatSummary(
    val book: Book,
    val totalMinutes: Int,
    val percentComplete: Float,
    val lastReadAt: Long?
)

data class DayBar(
    val label: String,
    val minutes: Int,
    val isToday: Boolean
)

data class StatsUiState(
    val currentStreakDays: Int = 0,
    val todayMinutes: Int = 0,
    val thisWeekSessions: List<ReadingSession> = emptyList(),
    val weeklyBars: List<DayBar> = emptyList(),
    val allTimeTotalMinutes: Int = 0,
    val allTimeBooksFinished: Int = 0,
    val avgSessionMinutes: Int = 0,
    val longestSessionMinutes: Int = 0,
    val perBookStats: List<BookStatSummary> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val readingSessionDao: ReadingSessionDao,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeStats()
    }

    private fun observeStats() {
        viewModelScope.launch {
            combine(
                readingSessionDao.getAllSessions(),
                bookRepository.getAllBooks(),
                bookRepository.getAllProgress()
            ) { sessions, books, progress ->
                Triple(sessions, books, progress.associateBy { it.bookId })
            }.collect { (sessions, books, progressMap) ->
                val now = System.currentTimeMillis()
                val dayMillis = 86_400_000L

                // Valid finished sessions >= 1 minute
                val validSessions = sessions.filter { 
                    it.endedAt != null && (it.endedAt - it.startedAt) >= 60_000L 
                }

                // Streak calculation
                val todayDay = now / dayMillis
                val validDays = validSessions.map { it.startedAt / dayMillis }.toSet()
                var streak = 0
                var checkDay = todayDay
                if (!validDays.contains(checkDay) && validDays.contains(checkDay - 1)) {
                    checkDay -= 1
                }
                while (validDays.contains(checkDay)) {
                    streak++
                    checkDay--
                }

                // Today minutes
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfToday = cal.timeInMillis

                val todayMins = sessions
                    .filter { it.startedAt >= startOfToday }
                    .sumOf { ((it.endedAt ?: now) - it.startedAt) / 60_000L }
                    .toInt()

                // Weekly chart bars (Mon..Sun of current week)
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val startOfMon = cal.timeInMillis
                val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val bars = labels.mapIndexed { idx, label ->
                    val dayStart = startOfMon + (idx * dayMillis)
                    val dayEnd = dayStart + dayMillis
                    val mins = sessions
                        .filter { it.startedAt in dayStart until dayEnd }
                        .sumOf { ((it.endedAt ?: now) - it.startedAt) / 60_000L }
                        .toInt()
                    val isToday = now in dayStart until dayEnd
                    DayBar(label, mins, isToday)
                }

                val weekSessions = sessions.filter { it.startedAt >= startOfMon }

                // All time aggregates
                val allTimeMins = validSessions.sumOf { ((it.endedAt ?: now) - it.startedAt) / 60_000L }.toInt()
                val avgMins = if (validSessions.isNotEmpty()) allTimeMins / validSessions.size else 0
                val longestMins = validSessions.maxOfOrNull { ((it.endedAt ?: now) - it.startedAt) / 60_000L }?.toInt() ?: 0

                // Per book stats
                val perBook = books.map { book ->
                    val bookSessions = sessions.filter { it.bookId == book.id }
                    val bookMins = bookSessions.sumOf { ((it.endedAt ?: now) - it.startedAt) / 60_000L }.toInt()
                    val prog = progressMap[book.id]
                    val pct = if (book.totalPages > 1 && prog != null) {
                        (prog.currentPage + 1).toFloat() / book.totalPages.toFloat()
                    } else 0f
                    BookStatSummary(
                        book = book,
                        totalMinutes = bookMins,
                        percentComplete = pct.coerceIn(0f, 1f),
                        lastReadAt = book.lastOpenedAt ?: book.addedAt
                    )
                }.sortedByDescending { it.lastReadAt }.take(10)

                val finishedCount = perBook.count { it.percentComplete >= 0.95f }

                _uiState.update {
                    it.copy(
                        currentStreakDays = streak,
                        todayMinutes = todayMins,
                        thisWeekSessions = weekSessions,
                        weeklyBars = bars,
                        allTimeTotalMinutes = allTimeMins,
                        allTimeBooksFinished = finishedCount,
                        avgSessionMinutes = avgMins,
                        longestSessionMinutes = longestMins,
                        perBookStats = perBook,
                        isLoading = false
                    )
                }
            }
        }
    }
}
