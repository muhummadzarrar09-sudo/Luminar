package dev.recto.reader.ui.reader

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.recto.reader.MainActivity

/**
 * Eye-comfort overlays: warmth and extra dimming.
 *
 * What the evidence actually says, because it is worth being straight about:
 *
 *  - Blue light is NOT the main cause of digital eye strain. The 2023 Cochrane
 *    review and the American Academy of Ophthalmology both found blue-light
 *    filtering makes little to no difference to strain symptoms. The real
 *    drivers are reduced blink rate, sustained near focus, glare, and a screen
 *    much brighter than the room.
 *
 *  - Warm/amber shifting DOES have decent evidence for one thing: reducing
 *    evening melatonin suppression, i.e. sleeping better after reading in bed.
 *
 *  - The single most effective software lever for comfort is BRIGHTNESS
 *    matched to the room. Phones cannot go dim enough for a dark room, which
 *    is why "minimum brightness is still too bright" is such a common
 *    complaint.
 *
 * So we ship both, and label them for what they really do: Warmth (sleep) and
 * Brightness (comfort). No claims about radiation or eye damage.
 *
 * Kindle's Paperwhite does warmth in hardware with amber LEDs. On a phone the
 * closest honest equivalent is a translucent amber layer over the page, which
 * is also how f.lux and Night Light work.
 */
@Composable
fun EyeComfortOverlay(
    warmth: Float,
    dim: Float
) {
    // Amber, matched to roughly the colour temperature shift of a warm bulb.
    // Multiplied by 0.34 so full warmth is a strong tint but never unreadable.
    if (warmth > 0.001f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFFFF9A2E).copy(alpha = warmth * 0.34f))
        )
    }

    // Extra dimming BELOW the hardware minimum, for reading in a dark room.
    // Capped at 0.72 so the page can never become entirely unreadable, which
    // would look like a crash.
    if (dim > 0.001f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim * 0.72f))
        )
    }
}

/**
 * Drives the actual screen backlight for this window only.
 *
 * This is the part that genuinely reduces strain: it lets the app be dimmer
 * than the phone's own minimum, and it is scoped to Recto - leaving the
 * reader restores whatever the system was doing.
 *
 * [level] is 0..1 of the *hardware* range. Null hands control back to the
 * system.
 */
@Composable
fun WindowBrightness(level: Float?) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    DisposableEffect(activity, level) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val params = window.attributes
            params.screenBrightness = level?.coerceIn(0.01f, 1f)
                ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = params

            onDispose {
                val restore = window.attributes
                restore.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = restore
            }
        }
    }
}
