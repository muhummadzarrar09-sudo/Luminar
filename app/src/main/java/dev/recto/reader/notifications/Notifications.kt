package dev.recto.reader.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.recto.reader.MainActivity
import dev.recto.reader.R

/**
 * Notification channels and the notifications themselves.
 *
 * Separate channels so each can be silenced independently in system settings -
 * someone who wants download alerts but not streak nudges should not have to
 * choose between all and nothing.
 *
 * The rule from the plan, still holding: never more than one engagement
 * notification a day, and everything except downloads is off by default.
 * Reading apps that nag get uninstalled.
 */
object Notifications {

    const val CHANNEL_REMINDERS = "reading_reminders"
    const val CHANNEL_STREAKS = "streaks_goals"
    const val CHANNEL_LIBRARY = "library"

    const val ID_DAILY_REMINDER = 1001
    const val ID_STREAK_RISK = 1002
    const val ID_GOAL_REACHED = 1003

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Reading reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Your daily nudge to pick up where you left off"
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STREAKS,
                "Streaks and goals",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "When a streak is about to lapse, or a goal is met"
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LIBRARY,
                "Library",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Imports and other library activity"
            }
        )
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * "Continue reading" - the daily nudge. Names the actual book, because
     * "time to read" is ignorable and "20 minutes left in Dune" is not.
     */
    fun dailyReminder(
        context: Context,
        bookTitle: String?,
        minutesGoal: Int,
        minutesToday: Int
    ) {
        val remaining = (minutesGoal - minutesToday).coerceAtLeast(0)
        val title = if (bookTitle != null) "Continue $bookTitle" else "Time to read"
        val body = when {
            minutesGoal <= 0 -> "Pick up where you left off"
            remaining == minutesGoal -> "$minutesGoal minutes today"
            else -> "$remaining minutes left to hit today's goal"
        }
        post(context, CHANNEL_REMINDERS, ID_DAILY_REMINDER, title, body)
    }

    fun streakAtRisk(context: Context, streakDays: Int) {
        post(
            context,
            CHANNEL_STREAKS,
            ID_STREAK_RISK,
            "Your $streakDays-day streak ends tonight",
            "A few pages is all it takes to keep it"
        )
    }

    fun goalReached(context: Context, minutes: Int, streakDays: Int) {
        post(
            context,
            CHANNEL_STREAKS,
            ID_GOAL_REACHED,
            "Goal reached - $minutes minutes",
            if (streakDays > 1) "$streakDays days in a row" else "Nice one"
        )
    }

    private fun post(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        body: String
    ) {
        if (!canPost(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = android.app.PendingIntent.getActivity(
            context,
            id,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Wrapped because POST_NOTIFICATIONS can be revoked between the
        // areNotificationsEnabled() check and here.
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
