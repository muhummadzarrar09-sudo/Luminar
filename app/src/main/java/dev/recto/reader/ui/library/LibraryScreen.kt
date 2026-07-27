package dev.recto.reader.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.recto.reader.data.BookFormat
import dev.recto.reader.data.db.BookEntity

@Composable
fun LibraryScreen(
    onOpenBook: (BookEntity) -> Unit,
    vm: LibraryViewModel = viewModel()
) {
    val books by vm.books.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    val snackbars = remember { SnackbarHostState() }

    // OpenMultipleDocuments gives us a persistable URI, unlike GetContent.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.importAll(uris) }

    LaunchedEffect(message) {
        message?.let {
            snackbars.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            if (books.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(BookFormat.pickerMimeTypes) },
                    text = { Text("Add books") },
                    icon = { Text("+", style = MaterialTheme.typography.titleLarge) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                books.isEmpty() && !importing -> EmptyLibrary(
                    onPick = { picker.launch(BookFormat.pickerMimeTypes) }
                )

                else -> BookGrid(
                    books = books,
                    onOpen = { book ->
                        vm.open(book)
                        onOpenBook(book)
                    }
                )
            }

            if (importing) {
                Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun BookGrid(
    books: List<BookEntity>,
    onOpen: (BookEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookCell(book = book, onClick = { onOpen(book) })
        }
    }
}

@Composable
private fun BookCell(book: BookEntity, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .shadow(3.dp, RoundedCornerShape(3.dp))
                .clip(RoundedCornerShape(3.dp))
        ) {
            BookCover(
                coverPath = book.coverPath,
                title = book.title,
                author = book.author,
                modifier = Modifier.fillMaxSize()
            )

            // Format badge for anything we cannot read yet, so the library is
            // honest rather than silently failing on tap.
            val format = runCatching { BookFormat.valueOf(book.format) }.getOrNull()
            if (format != null && !format.readable) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(topStart = 6.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = format.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (book.progress > 0.001f) {
            LinearProgressIndicator(
                progress = { book.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!book.author.isNullOrBlank()) {
            Text(
                text = book.author,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyLibrary(onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Two leaves, the recto in accent - the same mark as the app icon.
        Row {
            Box(
                Modifier
                    .height(76.dp)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            )
            Spacer(Modifier.padding(horizontal = 2.dp))
            Box(
                Modifier
                    .height(76.dp)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Your library is empty",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Add an EPUB from your phone and start reading. " +
                "PDF and the other formats arrive in a later build.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        ExtendedFloatingActionButton(
            onClick = onPick,
            text = { Text("Choose files") },
            icon = { Text("+", style = MaterialTheme.typography.titleLarge) }
        )
    }
}
