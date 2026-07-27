package dev.recto.reader.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import dev.recto.reader.MainActivity

/**
 * Turns pages with the hardware volume keys while the reader is on screen.
 *
 * This has to be done at the Activity level. Android delivers volume keys to
 * the window as a whole, so a Compose `onKeyEvent` on a focusable node never
 * sees them - the system has already handled them by then. MainActivity
 * exposes a hook that dispatchKeyEvent consults, and this composable
 * registers into it for exactly as long as the reader is composed.
 */
@Composable
fun VolumeKeyPageTurns(
    enabled: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    // Keep the latest lambdas without re-registering on every recomposition.
    val currentNext by rememberUpdatedState(onNext)
    val currentPrevious by rememberUpdatedState(onPrevious)

    DisposableEffect(activity, enabled) {
        if (activity == null || !enabled) {
            onDispose { }
        } else {
            activity.volumeKeyHandler = VolumeKeyHandler(
                onVolumeDown = { currentNext() },
                onVolumeUp = { currentPrevious() }
            )
            // Critically, clear the handler on the way out. Leaving it set
            // would swallow the volume keys on the library screen, where the
            // user quite reasonably expects to change the volume.
            onDispose { activity.volumeKeyHandler = null }
        }
    }
}

/**
 * Callbacks the Activity invokes when a volume key is pressed.
 */
class VolumeKeyHandler(
    val onVolumeDown: () -> Unit,
    val onVolumeUp: () -> Unit
)

/**
 * Holds the screen awake while reading. A reader that dims mid-page is the
 * single most irritating thing an ebook app can do; Kindle keeps the screen
 * on and so do we, released the moment you leave the reader.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    DisposableEffect(activity, enabled) {
        val window = activity?.window
        if (window != null && enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        } else {
            onDispose { }
        }
    }
}
