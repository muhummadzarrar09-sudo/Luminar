package dev.recto.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.recto.reader.ui.library.LibraryScreen
import dev.recto.reader.ui.reader.ReaderScreen
import dev.recto.reader.ui.theme.RectoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RectoTheme {
                RectoApp()
            }
        }
    }
}

/**
 * Two destinations, so a navigation library would be more ceremony than it is
 * worth right now. Navigation Compose arrives in Phase 2 when Home, Catalog
 * and Settings join the graph.
 */
@Composable
private fun RectoApp() {
    var openBookId by rememberSaveable { mutableStateOf<Long?>(null) }

    val id = openBookId
    if (id == null) {
        LibraryScreen(onOpenBook = { book -> openBookId = book.id })
    } else {
        ReaderScreen(
            bookId = id,
            onBack = { openBookId = null }
        )
    }
}
