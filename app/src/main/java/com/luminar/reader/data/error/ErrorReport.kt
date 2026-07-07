// app/src/main/java/com/luminar/reader/data/error/ErrorReport.kt
package com.luminar.reader.data.error

import android.content.Context
import android.os.Build
import com.luminar.reader.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class CrashReport(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()),
    val appVersion: String = BuildConfig.VERSION_NAME,
    val appVersionCode: Int = BuildConfig.VERSION_CODE,
    val androidVersion: Int = Build.VERSION.SDK_INT,
    val device: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val errorType: String,
    val errorMessage: String,
    val stackTrace: String,
    val context: String, // what the user was doing
    val fileFormat: String? = null,
    val userNote: String? = null
)

@Singleton
class ErrorReporter @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    // ─── Configure these for your Supabase project ──────────
    companion object {
        // TODO: Replace with your actual Supabase project URL and anon key
        private const val SUPABASE_URL = ""  // e.g. "https://xyzproject.supabase.co"
        private const val SUPABASE_ANON_KEY = ""  // your anon/public key
        private const val TABLE_NAME = "error_reports"

        val isConfigured: Boolean
            get() = SUPABASE_URL.isNotBlank() && SUPABASE_ANON_KEY.isNotBlank()
    }
    // ────────────────────────────────────────────────────────

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Send an error report to Supabase.
     * Fails silently — reporting should never crash the app.
     */
    suspend fun report(
        errorType: String,
        errorMessage: String,
        throwable: Throwable? = null,
        context: String,
        fileFormat: String? = null,
        userNote: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext false

        try {
            val report = CrashReport(
                errorType = errorType,
                errorMessage = errorMessage,
                stackTrace = throwable?.stackTraceToString()?.take(4000) ?: "",
                context = context,
                fileFormat = fileFormat,
                userNote = userNote
            )

            val json = buildJsonBody(report)

            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/$TABLE_NAME")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            response.close()
            response.isSuccessful
        } catch (_: Exception) {
            // Reporting must NEVER crash the app
            false
        }
    }

    private fun buildJsonBody(report: CrashReport): String {
        // Manual JSON — avoids needing Moshi adapters for this simple model
        return """
        {
            "id": "${report.id.escapeJson()}",
            "timestamp": "${report.timestamp.escapeJson()}",
            "app_version": "${report.appVersion.escapeJson()}",
            "app_version_code": ${report.appVersionCode},
            "android_version": ${report.androidVersion},
            "device": "${report.device.escapeJson()}",
            "error_type": "${report.errorType.escapeJson()}",
            "error_message": "${report.errorMessage.escapeJson()}",
            "stack_trace": "${report.stackTrace.escapeJson()}",
            "context": "${report.context.escapeJson()}",
            "file_format": ${report.fileFormat?.let { "\"${it.escapeJson()}\"" } ?: "null"},
            "user_note": ${report.userNote?.let { "\"${it.escapeJson()}\"" } ?: "null"}
        }
        """.trimIndent()
    }

    private fun String.escapeJson(): String {
        return this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
