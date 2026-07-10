// app/src/main/java/com/luminar/reader/CrashLogger.kt
package com.luminar.reader

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catches ANY uncaught exception and writes it to a file
 * at /data/data/com.luminar.reader/files/crash_log.txt
 * so the user can share it.
 */
class CrashLogger(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val log = buildString {
                appendLine("=== LUMINAR CRASH LOG ===")
                appendLine("Time: $timestamp")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("")
                appendLine("Thread: ${thread.name}")
                appendLine("")
                appendLine("Exception: ${throwable.javaClass.name}")
                appendLine("Message: ${throwable.message}")
                appendLine("")
                appendLine("Stack trace:")
                appendLine(sw.toString())
            }

            val file = File(context.filesDir, "crash_log.txt")
            file.writeText(log)

        } catch (_: Exception) {
            // If even the crash logger crashes, give up
        }

        // Let Android handle the crash (shows "app stopped" dialog)
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
