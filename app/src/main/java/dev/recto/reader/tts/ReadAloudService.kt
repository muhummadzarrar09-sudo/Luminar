package dev.recto.reader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import dev.recto.reader.MainActivity
import dev.recto.reader.R

/**
 * Keeps read-aloud alive when the screen is off, and puts controls on the
 * lock screen.
 *
 * WHY A SERVICE AT ALL
 * The speech engine lives in the ViewModel, which dies when the process is
 * backgrounded and trimmed. Without a foreground service, listening with the
 * screen off stops after a minute or two - which is exactly the case
 * read-aloud exists for.
 *
 * WHAT THIS DOES NOT DO
 * It does not own the engine or hold any book state. It is a notification
 * with buttons plus a promise to the system that the process is doing
 * something the user asked for. Commands come back through [ReadAloudBus],
 * which the reader observes. Keeping playback logic out of here means the
 * sheet, the menu and the lock screen all drive one code path.
 */
class ReadAloudService : Service() {

    private var lastTitle: String = "Reading aloud"
    private var lastLine: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: lastTitle
        val line = intent?.getStringExtra(EXTRA_LINE) ?: lastLine
        val playing = intent?.getBooleanExtra(EXTRA_PLAYING, true) ?: true
        lastTitle = title
        lastLine = line

        // ALWAYS go foreground first, before any path can return early.
        //
        // If the process was killed while the notification was still on
        // screen, tapping one of its buttons starts this service fresh. A
        // service started via startForegroundService that returns without
        // calling startForeground is killed with
        // ForegroundServiceDidNotStartInTimeException. Promoting up front
        // makes every branch below safe.
        startForegroundCompat(notification(this, title, line, playing))

        when (intent?.action) {
            ACTION_STOP -> {
                ReadAloudBus.emit(ReadAloudCommand.STOP)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> ReadAloudBus.emit(ReadAloudCommand.TOGGLE)
            ACTION_NEXT -> ReadAloudBus.emit(ReadAloudCommand.NEXT)
            ACTION_PREVIOUS -> ReadAloudBus.emit(ReadAloudCommand.PREVIOUS)
        }

        // Not START_STICKY: a restarted service with no book loaded would be
        // a dead control panel. If the process dies, playback is over.
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(note: Notification) {
        // Android 14 wants the type at startForeground time AND a matching
        // manifest declaration AND the permission. Getting any of them wrong
        // throws rather than degrading.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                note,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, note)
        }
    }

    companion object {
        const val CHANNEL_ID = "read_aloud"
        const val NOTIFICATION_ID = 2001

        const val ACTION_UPDATE = "dev.recto.reader.tts.UPDATE"
        const val ACTION_TOGGLE = "dev.recto.reader.tts.TOGGLE"
        const val ACTION_NEXT = "dev.recto.reader.tts.NEXT"
        const val ACTION_PREVIOUS = "dev.recto.reader.tts.PREVIOUS"
        const val ACTION_STOP = "dev.recto.reader.tts.STOP"

        const val EXTRA_TITLE = "title"
        const val EXTRA_LINE = "line"
        const val EXTRA_PLAYING = "playing"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
                ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Read aloud",
                    // LOW: a transport control, not news. Anything higher
                    // buzzes every time the sentence text changes.
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Playback controls while a book is read aloud"
                    setShowBadge(false)
                }
            )
        }

        /**
         * Starts the service. Call once, when playback begins.
         *
         * Must be called with the app in the foreground - Android 12+ forbids
         * starting a foreground service from the background. Read-aloud is
         * always started by a tap, so that holds.
         */
        fun start(context: Context, title: String, line: String) {
            val intent = Intent(context, ReadAloudService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_LINE, line)
                .putExtra(EXTRA_PLAYING, true)
            runCatching { context.startForegroundService(intent) }
        }

        /**
         * Refreshes the notification text as the voice moves.
         *
         * Posts straight to NotificationManager rather than re-delivering an
         * intent. startForegroundService once per SENTENCE would be an IPC
         * round trip several times a minute, and would throw outright if it
         * ever landed while the app was in the background.
         */
        fun refresh(context: Context, title: String, line: String, playing: Boolean) {
            ensureChannel(context)
            val manager = NotificationManagerCompat.from(context)
            // Posting to a disabled channel is a silent no-op, but checking
            // avoids the work and keeps logcat clean.
            if (!manager.areNotificationsEnabled()) return
            runCatching {
                manager.notify(
                    NOTIFICATION_ID,
                    notification(context, title, line, playing)
                )
            }
        }

        fun stop(context: Context) {
            // stopService, not an ACTION_STOP intent: this is called once
            // playback has ALREADY stopped, and routing through the bus would
            // bounce a redundant STOP back into the ViewModel.
            runCatching {
                context.stopService(Intent(context, ReadAloudService::class.java))
            }
        }

        /**
         * The notification itself, built the same way whether it is being
         * posted by the service or refreshed from outside it. One builder,
         * so the two can never drift apart.
         */
        private fun notification(
            context: Context,
            title: String,
            line: String,
            playing: Boolean
        ): Notification {
            ensureChannel(context)

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            fun command(action: String): PendingIntent {
                val intent = Intent(context, ReadAloudService::class.java)
                    .setAction(action)
                return PendingIntent.getService(
                    context,
                    action.hashCode(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(line.ifBlank { "Tap to open" })
                .setContentIntent(open)
                .setOngoing(playing)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .addAction(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    command(ACTION_PREVIOUS)
                )
                .addAction(
                    if (playing) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    },
                    if (playing) "Pause" else "Play",
                    command(ACTION_TOGGLE)
                )
                .addAction(
                    android.R.drawable.ic_media_next,
                    "Next",
                    command(ACTION_NEXT)
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    command(ACTION_STOP)
                )
                // Compact view gets the three transport buttons; Stop stays
                // in the expanded view where it cannot be hit by accident.
                .setStyle(MediaStyle().setShowActionsInCompactView(0, 1, 2))
                .build()
        }
    }
}
