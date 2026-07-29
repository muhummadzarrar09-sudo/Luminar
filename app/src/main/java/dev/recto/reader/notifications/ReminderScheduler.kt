package dev.recto.reader.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Schedules the daily reading reminder.
 *
 * Uses INEXACT alarms deliberately, and that is a considered choice rather
 * than a shortcut:
 *
 *  - Exact alarms are denied by default from Android 14. Getting them means
 *    sending the user to a system settings screen to grant a special access.
 *  - The alternative, USE_EXACT_ALARM, is granted on install but Google Play
 *    restricts it to apps whose core function is alarms and timers - clock
 *    apps, calendars. A reading reminder does not qualify, and shipping it
 *    risks a policy rejection.
 *  - A reminder that lands at 20:07 instead of 20:00 is completely fine. This
 *    is a nudge, not an alarm clock.
 *
 * setAndAllowWhileIdle still fires during Doze, so the reminder arrives even
 * on a phone that has been sitting on a desk all evening. It just may drift
 * by a few minutes, which nobody will notice.
 */
object ReminderScheduler {

    private const val REQUEST_DAILY = 4001
    private const val ACTION_DAILY = "dev.recto.reader.DAILY_REMINDER"

    fun schedule(context: Context, hour: Int, minute: Int) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context)

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // If today's slot has passed, aim at tomorrow. Otherwise a
            // reminder set for 8pm at 9pm would fire immediately.
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        runCatching {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target.timeInMillis,
                pending
            )
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { manager.cancel(pendingIntent(context)) }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_DAILY,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_DAILY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

/**
 * Fires the reminder, then re-arms for tomorrow.
 *
 * Re-arming here rather than using a repeating alarm is intentional: all
 * repeating alarms have been inexact since API 19 anyway, and a one-shot that
 * reschedules itself survives clock changes and daylight-saving shifts
 * correctly, because the next slot is computed fresh each time.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()

        // Work must be handed off - a BroadcastReceiver gets about ten
        // seconds, and reading the database is not something to do on the
        // main thread.
        ReminderWork.enqueueDailyCheck(context)

        pending.finish()
    }
}

/**
 * Re-arms the reminder after a reboot, which clears every scheduled alarm.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        ReminderWork.enqueueReschedule(context)
    }
}
