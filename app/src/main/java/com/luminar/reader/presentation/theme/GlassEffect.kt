// app/src/main/java/com/luminar/reader/presentation/theme/GlassEffect.kt
package com.luminar.reader.presentation.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glassmorphism modifier — applies real blur on API 31+,
 * falls back to translucent surface on older devices.
 *
 * Usage:
 *   Box(modifier = Modifier.glassEffect())
 */
fun Modifier.glassEffect(
    blurRadius: Float = 25f,
    tintColor: Color = Color.Black.copy(alpha = 0.3f),
    borderColor: Color = Color.White.copy(alpha = 0.08f),
    borderWidth: Dp = 0.5.dp,
    cornerRadius: Dp = 16.dp
): Modifier {
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .then(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Real hardware-accelerated blur on Android 12+
                Modifier.graphicsLayer {
                    renderEffect = RenderEffect
                        .createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                }
            } else {
                // Fallback: no blur, just tinted background
                Modifier
            }
        )
        .background(tintColor, RoundedCornerShape(cornerRadius))
        .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
}

/**
 * Glass surface colors per theme — for composables that
 * need the glass tint color without the blur modifier.
 */
object GlassColors {
    val darkSurface = Color(0xFF111111).copy(alpha = 0.75f)
    val darkBorder = Color(0xFFD4A843).copy(alpha = 0.1f)

    val sepiaSurface = Color(0xFFF5EDD6).copy(alpha = 0.8f)
    val sepiaBorder = Color(0xFF8B7028).copy(alpha = 0.15f)

    val lightSurface = Color.White.copy(alpha = 0.85f)
    val lightBorder = Color(0xFF1A1A1A).copy(alpha = 0.06f)
}
