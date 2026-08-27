package dev.recto.reader.data.stats

import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Day keys, streaks and the arithmetic behind the stats screen.
 *
 * A "day" here is a local calendar day, formatted yyyyMMdd. Storing that
 * alongside the raw timestamp means streaks and heatmaps are integer
 * comparisons rather than timezone-aware date maths on every query - and it
 * means a session at 11pm and one at 1am correctly land on different days,
 * which UTC-based bucketing gets wrong for most of the world.
 */
object ReadingStats {

    /** yyyyMMdd for a timestamp, in the device's local time zone. */
    fun dayKey(timeMillis: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        return cal.get(Calendar.YEAR) * 10000 +
            (cal.get(Calendar.MONTH) + 1) * 100 +
            cal.get(Calendar.DAY_OF_MONTH)
    }

    /** Midnight at the start of a day key, as epoch millis. */
    fun startOfDay(dayKey: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, dayKey / 10000)
            set(Calendar.MONTH, (dayKey / 100) % 100 - 1)
            set(Calendar.DAY_OF_MONTH, dayKey % 100)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /** The day key [days] before the given one, handling month and year ends. */
    fun dayKeyOffset(from: Int, days: Int): Int {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfDay(from)
            add(Calendar.DAY_OF_YEAR, days)
        }
        return dayKey(cal.timeInMillis)
    }

    /**
     * Current streak: consecutive days ending today, or ending yesterday.
     *
     * Yesterday counts because a streak should not be declared broken at
     * midnight - you still have all of today to read. It only breaks once a
     * whole day has passed with nothing.
     */
    fun currentStreak(daysWithReading: Set<Int>, today: Int = dayKey()): Int {
        if (daysWithReading.isEmpty()) return 0

        val yesterday = dayKeyOffset(today, -1)
        var cursor = when {
            today in daysWithReading -> today
            yesterday in daysWithReading -> yesterday
            else -> return 0
        }

        var streak = 0
        while (cursor in daysWithReading) {
            streak++
            cursor = dayKeyOffset(cursor, -1)
        }
        return streak
    }

    /** Longest run of consecutive days ever recorded. */
    fun longestStreak(daysWithReading: Set<Int>): Int {
        if (daysWithReading.isEmpty()) return 0

        var best = 0
        for (day in daysWithReading) {
            // Only start counting from the beginning of a run, so each run is
            // walked once rather than once per day in it.
            if (dayKeyOffset(day, -1) in daysWithReading) continue
            var run = 0
            var cursor = day
            while (cursor in daysWithReading) {
                run++
                cursor = dayKeyOffset(cursor, 1)
            }
            if (run > best) best = run
        }
        return best
    }

    /**
     * True when the streak is alive but today has no reading yet - the
     * condition worth a nudge.
     */
    fun streakAtRisk(daysWithReading: Set<Int>, today: Int = dayKey()): Boolean =
        today !in daysWithReading && dayKeyOffset(today, -1) in daysWithReading

    fun formatDuration(millis: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            totalMinutes < 1 -> "under a minute"
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    fun formatDurationShort(millis: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val hours = totalMinutes / 60
        return if (hours >= 1) "${hours}h" else "${totalMinutes}m"
    }

    /**
     * The last [weeks] weeks of day keys, oldest first, aligned so each row of
     * seven starts on the same weekday - which is what makes a heatmap grid
     * readable rather than a wrapped ribbon.
     */
    fun heatmapDays(weeks: Int = 17, today: Int = dayKey()): List<Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(today) }
        // Back up to the most recent Monday so columns line up by weekday.
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val sinceMonday = (dow - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, -sinceMonday - (weeks - 1) * 7)

        return (0 until weeks * 7).map { offset ->
            val c = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            dayKey(c.timeInMillis)
        }
    }
}
