package dev.recto.reader.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.recto.reader.data.SettingsRepository
import dev.recto.reader.data.db.RectoDatabase
import dev.recto.reader.data.stats.ReadingStats
import kotlinx.coroutines.flow.first

/**
 * Decides whether a reminder is actually worth showing, then shows it.
 *
 * This runs in WorkManager rather than in the BroadcastReceiver because it
 * touches the database. A receiver gets roughly ten seconds on the main
 * thread; a Room query plus a streak computation has no business being there.
 */
object ReminderWork {

    private const val DAILY_CHECK = "recto_daily_reminder_check"
    private const val RESCHEDULE = "recto_reminder_reschedule"

    fun enqueueDailyCheck(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            DAILY_CHECK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailyReminderWorker>().build()
        )
    }

    fun enqueueReschedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            RESCHEDULE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RescheduleWorker>().build()
        )
    }
}

/**
 * The daily reminder.
 *
 * Deliberately quiet. It does nothing at all if:
 *  - reminders are switched off
 *  - you have already met today's goal
 *  - you have already read today at all and there is no goal set
 *
 * A reminder to do something you have already done is the fastest way to get
 * an app's notifications muted for good.
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = SettingsRepository(context).settings.first()

        // Always re-arm, even when we decide not to notify - otherwise
        // skipping one day would silently end the reminder forever.
        if (settings.remindersEnabled) {
            ReminderScheduler.schedule(
                context,
                settings.reminderHour,
                settings.reminderMinute
            )
        }

        if (!settings.remindersEnabled) return Result.success()
        if (!Notifications.canPost(context)) return Result.success()

        val db = RectoDatabase.get(context)
        val today = ReadingStats.dayKey()
        val millisToday = db.readingSessionDao().millisOnDay(today)
        val minutesToday = (millisToday / 60_000).toInt()
        val goal = settings.dailyGoalMinutes

        if (goal > 0 && minutesToday >= goal) return Result.success()
        if (goal <= 0 && minutesToday > 0) return Result.success()

        val currentBook = db.bookDao().observeCurrent().first()

        Notifications.dailyReminder(
            context = context,
            bookTitle = currentBook?.title,
            minutesGoal = goal,
            minutesToday = minutesToday
        )

        // The streak nudge is folded into the same run rather than being a
        // second notification, honouring the one-a-day rule from the plan.
        if (settings.streakAlertsEnabled) {
            val days = db.readingSessionDao().recentDays().toSet()
            val streak = ReadingStats.currentStreak(days, today)
            if (streak >= 2 && ReadingStats.streakAtRisk(days, today)) {
                Notifications.streakAtRisk(context, streak)
            }
        }

        return Result.success()
    }
}

/** Re-arms the alarm after a reboot or an app update. */
class RescheduleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext).settings.first()
        if (settings.remindersEnabled) {
            ReminderScheduler.schedule(
                applicationContext,
                settings.reminderHour,
                settings.reminderMinute
            )
        }
        return Result.success()
    }
}
