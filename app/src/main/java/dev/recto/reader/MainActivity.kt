package dev.recto.reader

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.recto.reader.ui.library.LibraryScreen
import dev.recto.reader.ui.library.LibraryViewModel
import dev.recto.reader.ui.reader.ReaderScreen
import dev.recto.reader.ui.reader.VolumeKeyHandler
import dev.recto.reader.ui.theme.RectoTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /**
     * Intents arriving from outside the app. A StateFlow rather than a plain
     * field because onNewIntent can fire while Compose is already running -
     * for instance tapping a second WhatsApp attachment while Recto is open.
     */
    private val incoming = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Only treat this as an incoming book on a fresh start. On a
        // configuration change or process restore the same intent is redelivered,
        // and re-importing on every rotation would be maddening.
        if (savedInstanceState == null) {
            incoming.value = intent
        }

        setContent {
            RectoTheme {
                RectoApp(incoming = incoming)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming.value = intent
    }

    /**
     * Set by the reader while it is on screen, cleared when it leaves.
     * Null means volume keys behave normally.
     */
    var volumeKeyHandler: VolumeKeyHandler? = null

    /**
     * Volume keys are delivered to the window before any Compose focus target
     * sees them, so page-turning has to be intercepted here.
     *
     * Only KeyDown is acted on; the matching KeyUp is still consumed so the
     * system volume UI does not flash on screen.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = volumeKeyHandler
        if (handler != null) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (event.action == KeyEvent.ACTION_DOWN) handler.onVolumeDown()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (event.action == KeyEvent.ACTION_DOWN) handler.onVolumeUp()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun RectoApp(incoming: MutableStateFlow<Intent?>) {
    // Hoisted so the library keeps importing even while the reader is on top.
    val libraryVm: LibraryViewModel = viewModel()

    var openBookId by rememberSaveable { mutableStateOf<Long?>(null) }
    /** Character offset to jump to on open, from a library search hit. */
    var openAtChar by rememberSaveable { mutableStateOf<Int?>(null) }

    val pendingIntent by incoming.collectAsState()

    LaunchedEffect(pendingIntent) {
        val uris = IncomingBook.urisFrom(pendingIntent)
        if (uris.isNotEmpty()) {
            libraryVm.importFromIntent(uris)
        }
        // Consume it either way, so rotation cannot replay the import.
        if (pendingIntent != null) incoming.value = null
    }

    // A book handed to us from outside goes straight to the reader. Dropping
    // the user on the library after they tapped a specific file makes them
    // hunt for the thing they just opened.
    LaunchedEffect(Unit) {
        libraryVm.openImmediately.collect { id -> openBookId = id }
    }

    val id = openBookId
    if (id == null) {
        LibraryScreen(
            onOpenBook = { book ->
                openAtChar = null
                openBookId = book.id
            },
            onOpenBookAt = { book, charOffset ->
                openAtChar = charOffset
                openBookId = book.id
            },
            vm = libraryVm
        )
    } else {
        ReaderScreen(
            bookId = id,
            jumpToChar = openAtChar,
            onJumpConsumed = { openAtChar = null },
            onBack = {
                openAtChar = null
                openBookId = null
            }
        )
    }
}
