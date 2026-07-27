package dev.recto.reader

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.recto.reader.ui.theme.RectoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RectoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Phase0Screen()
                }
            }
        }
    }
}

/**
 * Phase 0's only screen. Its job is to prove the toolchain end to end:
 * AGP 9 with built-in Kotlin, the Compose compiler plugin, Material 3, the
 * theme, and an APK that installs and launches on a real phone.
 *
 * Phase 1 replaces this with the library grid.
 */
@Composable
fun Phase0Screen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RectoMark()

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Recto",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "The page you're on.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        Text(
            text = "Phase 0 - build baseline",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "If you are reading this on your phone, the toolchain works: " +
                "AGP 9 built-in Kotlin, Compose, Material 3, and a signed debug APK.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Android ${Build.VERSION.RELEASE}  (API ${Build.VERSION.SDK_INT})\n" +
                "${Build.MANUFACTURER} ${Build.MODEL}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The icon concept: an open book reduced to two leaves, with the right-hand
 * one - the recto - picked out in the accent colour.
 */
@Composable
private fun RectoMark(modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Surface(
            modifier = Modifier
                .width(46.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ) {}

        Spacer(Modifier.width(4.dp))

        Surface(
            modifier = Modifier
                .width(46.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)),
            color = MaterialTheme.colorScheme.primary
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun Phase0ScreenPreview() {
    RectoTheme { Phase0Screen() }
}
