// app/src/main/java/com/luminar/reader/presentation/library/LibraryScreen.kt
package com.luminar.reader.presentation.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.luminar.reader.data.model.BookFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { viewModel.onEvent(LibraryEvent.ImportFile(it)) }
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
                    if (uiState.isSearchActive) {
                        LibrarySearchField(
                            query = uiState.searchQuery,
                            onQueryChanged = { viewModel.onEvent(LibraryEvent.UpdateSearchQuery(it)) }
                        )
                    } else {
                        Text(
                            text = "Luminar",
                            fontFamily = LuminarTitleFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 30.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
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

                    // Search toggle
                    IconButton(onClick = { viewModel.onEvent(LibraryEvent.ToggleSearch) }) {
                        Icon(
                            painter = painterResource(
                                if (uiState.isSearchActive) R.drawable.ic_close_24
                                else R.drawable.ic_search_24
                            ),
                            contentDescription = if (uiState.isSearchActive) "Close search" else "Search",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Grid/List toggle
                    IconButton(onClick = { viewModel.onEvent(LibraryEvent.ToggleViewMode) }) {
                        Text(
                            text = if (uiState.viewMode == ViewMode.GRID) "☰" else "⊞",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 18.sp
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
                onClick = { filePickerLauncher.launch(BookFormat.IMPORTABLE_MIME_TYPES) },
                containerColor = LuminarGold,
                contentColor = Color(0xFF171100)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24),
                    contentDescription = "Import file"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Filter chips + sort — only show when library has items
            if (uiState.allBooks.isNotEmpty()) {
                LibraryToolbar(
                    uiState = uiState,
                    onSortChanged = { viewModel.onEvent(LibraryEvent.SetSortOrder(it)) },
                    onFilterChanged = { viewModel.onEvent(LibraryEvent.SetFormatFilter(it)) }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = LuminarGold
                        )
                    }

                    uiState.allBooks.isEmpty() -> {
                        EmptyLibraryState(
                            onImportClick = { filePickerLauncher.launch(BookFormat.IMPORTABLE_MIME_TYPES) }
                        )
                    }

                    uiState.books.isEmpty() -> {
                        // Has books but all filtered out
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No matching files",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (uiState.searchQuery.isNotBlank()) {
                                Text(
                                    text = "Try a different search term",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    else -> {
                        if (uiState.viewMode == ViewMode.GRID) {
                            LibraryGrid(
                                uiState = uiState,
                                onBookClick = { viewModel.onEvent(LibraryEvent.OpenBook(it)) },
                                onBookLongClick = { bookPendingDeletion = it },
                                onRemoveMissingBook = { viewModel.onEvent(LibraryEvent.DeleteBook(it)) }
                            )
                        } else {
                            LibraryList(
                                uiState = uiState,
                                onBookClick = { viewModel.onEvent(LibraryEvent.OpenBook(it)) },
                                onBookLongClick = { bookPendingDeletion = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Search field ────────────────────────────────────────────

@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChanged: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (query.isEmpty()) {
            Text(
                text = "Search library…",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 16.sp
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            ),
            singleLine = true,
            cursorBrush = SolidColor(LuminarGold)
        )
    }
}

// ─── Filter chips + Sort ─────────────────────────────────────

@Composable
private fun LibraryToolbar(
    uiState: LibraryUiState,
    onSortChanged: (SortOrder) -> Unit,
    onFilterChanged: (FormatFilter) -> Unit
) {
    Column {
        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FormatFilter.entries.forEach { filter ->
                val count = uiState.formatCounts[filter] ?: 0
                if (filter == FormatFilter.ALL || count > 0) {
                    FilterChip(
                        selected = uiState.formatFilter == filter,
                        onClick = { onFilterChanged(filter) },
                        label = {
                            Text(
                                text = if (filter == FormatFilter.ALL) filter.label
                                       else "${filter.label} ($count)",
                                fontSize = 13.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LuminarGold.copy(alpha = 0.2f),
                            selectedLabelColor = LuminarGold
                        )
                    )
                }
            }
        }

        // Sort row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${uiState.books.size} file${if (uiState.books.size != 1) "s" else ""}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { showSortMenu = true }) {
                    Text(
                        text = "Sort: ${uiState.sortOrder.label}",
                        color = LuminarGold,
                        fontSize = 13.sp
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = order.label,
                                    fontWeight = if (order == uiState.sortOrder) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSortChanged(order)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─── Grid view ───────────────────────────────────────────────

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

// ─── List view ───────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryList(
    uiState: LibraryUiState,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            items = uiState.books,
            key = { it.id }
        ) { book ->
            val fileSize = remember(book.filePath) { formatFileSize(File(book.filePath)) }
            val progress = uiState.progressByBookId[book.id].progressFraction(book.totalPages)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onBookClick(book) },
                        onLongClick = { onBookLongClick(book) }
                    ),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cover thumbnail
                    val coverFile = remember(book.coverPath) {
                        book.coverPath?.let(::File)?.takeIf { it.exists() }
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(LuminarGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverFile != null) {
                            AsyncImage(
                                model = coverFile,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = book.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                color = LuminarGold,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Title + metadata
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = book.format.displayName,
                                color = LuminarGold.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = fileSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            if (progress > 0f) {
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Book card (grid) ────────────────────────────────────────

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
    val fileSize = remember(book.filePath) { formatFileSize(File(book.filePath)) }

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

                if (book.format != BookFormat.PDF) {
                    FormatBadge(
                        format = book.format,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    )
                }
            }

            // Title
            Text(
                text = book.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            // File size
            Text(
                text = fileSize,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
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
private fun MissingFileOverlay(onRemove: () -> Unit) {
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
                Text(text = "Remove", color = LuminarGold)
            }
        }
    }
}

@Composable
private fun FormatBadge(
    format: BookFormat,
    modifier: Modifier = Modifier
) {
    Text(
        text = format.displayName.uppercase(),
        modifier = modifier
            .background(
                color = LuminarGold.copy(alpha = 0.9f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = Color(0xFF171100),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
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
private fun EmptyLibraryState(onImportClick: () -> Unit) {
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
            text = "Import a PDF, Markdown, text file, or code and begin reading.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(22.dp))

        OutlinedButton(
            onClick = onImportClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LuminarGold)
        ) {
            Text(text = "Import file")
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
            Text(text = "\u201C${book.title}\u201D will be removed from your library and internal storage.")
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

// ─── Helpers ─────────────────────────────────────────────────

private fun ReadingProgress?.progressFraction(totalPages: Int): Float {
    if (this == null || totalPages <= 1) return 0f
    return (currentPage + 1).toFloat() / totalPages.toFloat()
}

private fun formatFileSize(file: File): String {
    val bytes = runCatching { file.length() }.getOrDefault(0L)
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
