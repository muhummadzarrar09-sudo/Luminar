package dev.recto.reader

import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Pulls a book URI out of an incoming intent.
 *
 * Covers the three ways another app hands us a file:
 *   ACTION_VIEW          - "Open with Recto" from a file manager, download
 *                          notification, or WhatsApp's document preview
 *   ACTION_SEND          - the share sheet
 *   ACTION_SEND_MULTIPLE - sharing several at once
 */
object IncomingBook {

    fun urisFrom(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()

        return when (intent.action) {
            Intent.ACTION_VIEW ->
                listOfNotNull(intent.data)

            Intent.ACTION_SEND ->
                listOfNotNull(extraStream(intent))

            Intent.ACTION_SEND_MULTIPLE ->
                extraStreamList(intent)

            else -> emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun extraStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

    @Suppress("DEPRECATION")
    private fun extraStreamList(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            (intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()
        }
}
