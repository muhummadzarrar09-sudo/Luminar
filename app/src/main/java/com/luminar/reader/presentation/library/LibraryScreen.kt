// app/src/main/java/com/luminar/reader/presentation/library/LibraryScreen.kt
package com.luminar.reader.presentation.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.luminar.reader.R
import com.luminar.reader.data.model.Book
import com.luminar.reader.data.model.ReadingProgress
import com.luminar.reader.presentation.theme.LuminarGold
import com.luminar.reader.presentation.theme.LuminarTitleFont
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let { viewModel.onEvent(LibraryEvent.ImportPdf(it)) }
        }
    )

    var bookPendingDeletion by remember { mutableStateOf<Book?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LibraryEffect.NavigateToReader -> onOpenBook(effect.bookId)
                is LibraryEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    bookPendingDeletion?.let { book ->
        DeleteBookDialog(
            book = book,
            onDismiss = { bookPendingDeletion = null },
            onConfirm = {
                viewModel.onEvent(LibraryEvent.DeleteBook(book))
                bookPendingDeletion = null
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Luminar",
                        fontFamily = LuminarTitleFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    if (uiState.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp,
                            color = LuminarGold
                        )
                    }

                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_24),
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pdfPickerLauncher.launch("application/pdf") },
                containerColor = LuminarGold,
                contentColor = Color(0xFF171100)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24),
                    contentDescription = "Import PDF"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = LuminarGold
                    )
                }

                uiState.books.isEmpty() -> {
                    EmptyLibraryState(
                        onImportClick = { pdfPickerLauncher.launch("application/pdf") }
                    )
                }

                else -> {
                    LibraryGrid(
                        uiState = uiState,
                        onBookClick = { book ->
                            viewModel.onEvent(LibraryEvent.OpenBook(book))
                        },
                        onBookLongClick = { book ->
                            bookPendingDeletion = book
                        },
                        onRemoveMissingBook = { book ->
                            viewModel.onEvent(LibraryEvent.DeleteBook(book))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryGrid(
    uiState: LibraryUiState,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onRemoveMissingBook: (Book) -> Unit
) {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = uiState.books,
            key = { it.id }
        ) { book ->
            BookCard(
                book = book,
                progress = uiState.progressByBookId[book.id].progressFraction(book.totalPages),
                onClick = { onBookClick(book) },
                onLongClick = { onBookLongClick(book) },
                onRemoveMissingBook = { onRemoveMissingBook(book) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    book: Book,
    progress: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveMissingBook: () -> Unit
) {
    val fileExists = remember(book.filePath) { File(book.filePath).exists() }
    val coverFile = remember(book.coverPath) {
        book.coverPath?.let(::File)?.takeIf { it.exists() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                if (coverFile != null) {
                    AsyncImage(
                        model = coverFile,
                        contentDescription = "${book.title} cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    CoverPlaceholder(bookTitle = book.title)
                }

                if (!fileExists) {
                    MissingFileOverlay(onRemove = onRemoveMissingBook)
                }
            }

            Text(
                text = book.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            ProgressStrip(progress = progress)
        }
    }
}

@Composable
private fun CoverPlaceholder(bookTitle: String) {
    val firstLetter = bookTitle.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        LuminarGold.copy(alpha = 0.85f),
                        Color(0xFF3B3014),
                        Color(0xFF111111)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = firstLetter,
            color = Color(0xFF171100),
            fontSize = 54.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = LuminarTitleFont
        )
    }
}

@Composable
private fun MissingFileOverlay(
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "File not found",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelLarge
            )
            TextButton(onClick = onRemove) {
                Text(
                    text = "Remove",
                    color = LuminarGold
                )
            }
        }
    }
}

@Composable
private fun ProgressStrip(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(LuminarGold)
        )
    }
}

@Composable
private fun EmptyLibraryState(
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_auto_stories_48),
            contentDescription = null,
            modifier = Modifier.size(68.dp),
            tint = LuminarGold
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Add your first book",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Import a PDF and begin reading in Luminar.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(22.dp))

        OutlinedButton(
            onClick = onImportClick,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = LuminarGold
            )
        ) {
            Text(text = "Import PDF")
        }
    }
}

@Composable
private fun DeleteBookDialog(
    book: Book,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Remove book?") },
        text = {
            Text(
                text = "“${book.title}” will be removed from your library and internal storage."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Remove", color = LuminarGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

private fun ReadingProgress?.progressFraction(totalPages: Int): Float {
    if (this == null || totalPages <= 1) return 0f
    return (currentPage + 1).toFloat() / totalPages.toFloat()
}
