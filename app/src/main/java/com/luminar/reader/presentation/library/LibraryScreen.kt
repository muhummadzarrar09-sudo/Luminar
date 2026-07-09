// app/src/main/java/com/luminar/reader/presentation/library/LibraryScreen.kt
package com.luminar.reader.presentation.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.luminar.reader.data.model.BookFormat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.luminar.reader.presentation.components.ErrorReportDialog
import com.luminar.reader.presentation.theme.LuminarGold
import com.luminar.reader.presentation.theme.LuminarTitleFont
import com.luminar.reader.presentation.theme.Spacing
import com.luminar.reader.presentation.theme.Radius
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
    val haptics = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()

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

    // Error report dialog
    val currentError = uiState.error
    if (uiState.showErrorReport && currentError != null) {
        ErrorReportDialog(
            errorMessage = currentError,
            onDismiss = { viewModel.onEvent(LibraryEvent.DismissErrorReport) },
            onSendReport = { note -> viewModel.onEvent(LibraryEvent.SendErrorReport(note)) }
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
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium,
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            LibraryBottomBar(
                uiState = uiState,
                onToggleSearch = { viewModel.onEvent(LibraryEvent.ToggleSearch) },
                onToggleViewMode = { viewModel.onEvent(LibraryEvent.ToggleViewMode) },
                onOpenSettings = onOpenSettings,
                onImport = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    filePickerLauncher.launch(BookFormat.IMPORTABLE_MIME_TYPES)
                }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Continue Reading hero card
            if (!uiState.isLoading && uiState.allBooks.isNotEmpty()) {
                val lastBook = uiState.allBooks
                    .filter { it.lastOpenedAt != null }
                    .maxByOrNull { it.lastOpenedAt ?: 0 }

                if (lastBook != null) {
                    ContinueReadingCard(
                        book = lastBook,
                        progress = uiState.progressByBookId[lastBook.id]
                            .progressFraction(lastBook.totalPages),
                        onClick = { viewModel.onEvent(LibraryEvent.OpenBook(lastBook)) }
                    )
                }

                // Recently opened row (up to 6 books, excluding the "continue" book)
                val recentBooks = uiState.allBooks
                    .filter { it.lastOpenedAt != null && it.id != lastBook?.id }
                    .sortedByDescending { it.lastOpenedAt }
                    .take(6)

                if (recentBooks.isNotEmpty()) {
                    RecentlyOpenedRow(
                        books = recentBooks,
                        onBookClick = { viewModel.onEvent(LibraryEvent.OpenBook(it)) }
                    )
                }
            }

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
                        LibraryLoadingSkeleton()
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
                            gridState = gridState,
                            onBookClick = { viewModel.onEvent(LibraryEvent.OpenBook(it)) },
                            onBookLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                bookPendingDeletion = it
                            },
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

// ─── Continue Reading + Recent ────────────────────────────

@Composable
private fun ContinueReadingCard(
    book: Book,
    progress: Float,
    onClick: () -> Unit
) {
    val coverFile = remember(book.coverPath) {
        book.coverPath?.let(::File)?.takeIf { it.exists() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Gold left accent stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(LuminarGold)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover thumbnail
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 64.dp)
                    .clip(RoundedCornerShape(Radius.sm))
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
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Continue reading",
                    color = LuminarGold,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = book.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(LuminarGold, RoundedCornerShape(2.dp))
                    )
                }
                Text(
                    text = "${(progress * 100).toInt()}% · ${book.format.displayName}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            // Arrow
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_24),
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(24.dp),
                tint = LuminarGold
            )
            } // inner Row
        } // outer Row
    }
}

@Composable
private fun RecentlyOpenedRow(
    books: List<Book>,
    onBookClick: (Book) -> Unit
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "Recently opened",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            books.forEach { book ->
                val coverFile = remember(book.coverPath) {
                    book.coverPath?.let(::File)?.takeIf { it.exists() }
                }

                Card(
                    modifier = Modifier
                        .width(56.dp)
                        .clickable { onBookClick(book) },
                    shape = RoundedCornerShape(Radius.sm),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 76.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(LuminarGold.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverFile != null) {
                            AsyncImage(
                                model = coverFile,
                                contentDescription = book.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = book.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                color = LuminarGold,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Bottom navigation bar ───────────────────────────────

@Composable
private fun LibraryBottomBar(
    uiState: LibraryUiState,
    onToggleSearch: () -> Unit,
    onToggleViewMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit
) {
    Column {
        // Top divider
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            thickness = 0.5.dp
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search
            BottomBarItem(
                icon = {
                    Icon(
                        painter = painterResource(
                            if (uiState.isSearchActive) R.drawable.ic_close_24
                            else R.drawable.ic_search_24
                        ),
                        contentDescription = "Search",
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = if (uiState.isSearchActive) "Close" else "Search",
                isActive = uiState.isSearchActive,
                onClick = onToggleSearch
            )

            // View toggle
            BottomBarItem(
                icon = {
                    Icon(
                        painter = painterResource(
                            if (uiState.viewMode == ViewMode.GRID) R.drawable.ic_view_list_24
                            else R.drawable.ic_grid_view_24
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = if (uiState.viewMode == ViewMode.GRID) "List" else "Grid",
                onClick = onToggleViewMode
            )

            // Import (center, prominent — larger, elevated)
            BottomBarItem(
                icon = {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.size(48.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = LuminarGold,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add_24),
                                contentDescription = "Import",
                                tint = Color(0xFF171100),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                label = "Import",
                onClick = onImport
            )

            // View toggle (placeholder for future stats)
            BottomBarItem(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_bar_chart_24),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = "Stats",
                onClick = onOpenSettings // for now, opens settings where stats live
            )

            // Settings
            BottomBarItem(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_24),
                        contentDescription = "Settings",
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = "Settings",
                onClick = onOpenSettings
            )
        }
        }
    }
}

@Composable
private fun BottomBarItem(
    icon: @Composable () -> Unit,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val activeAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "navIndicator"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // M3 Expressive active indicator pill
        Box(
            modifier = Modifier
                .background(
                    color = LuminarGold.copy(alpha = activeAlpha * 0.15f),
                    shape = RoundedCornerShape(Radius.lg)
                )
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Text(
            text = label,
            color = if (isActive) LuminarGold else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
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
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
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
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.weight(1f))

            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { showSortMenu = true }) {
                    Text(
                        text = "Sort: ${uiState.sortOrder.label}",
                        color = LuminarGold,
                        style = MaterialTheme.typography.labelMedium
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGrid(
    uiState: LibraryUiState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onRemoveMissingBook: (Book) -> Unit
) {
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = uiState.books,
            key = { it.id }
        ) { book ->
            BookCard(
                modifier = Modifier.animateItemPlacement(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
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
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
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
    modifier: Modifier = Modifier,
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
    val formatAccent = remember(book.format) { formatAccentColor(book.format) }

    // Press scale animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "cardScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        shape = RoundedCornerShape(Radius.md),
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

                // Format accent bar at top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.TopCenter)
                        .background(formatAccent)
                )

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
private fun LibraryLoadingSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false
    ) {
        items(6) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    // Cover placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .background(shimmerColor)
                    )
                    // Title placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(14.dp)
                            .padding(start = 10.dp, top = 10.dp)
                            .background(shimmerColor, RoundedCornerShape(4.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(10.dp)
                            .padding(start = 10.dp, top = 6.dp, bottom = 10.dp)
                            .background(shimmerColor, RoundedCornerShape(4.dp))
                    )
                    // Progress strip placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(shimmerColor.copy(alpha = alpha * 0.5f))
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(onImportClick: () -> Unit) {
    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600),
        label = "emptyAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 24.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "emptyOffset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = offsetY.toPx()
            },
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

private fun formatAccentColor(format: BookFormat): Color {
    return when (format) {
        BookFormat.PDF -> Color(0xFFE53935)       // red
        BookFormat.EPUB, BookFormat.MOBI, BookFormat.AZW3, BookFormat.FB2, BookFormat.PDB -> Color(0xFF1E88E5) // blue
        BookFormat.DOCX, BookFormat.DOC, BookFormat.ODT, BookFormat.RTF -> Color(0xFF2979FF) // blue
        BookFormat.XLSX, BookFormat.ODS, BookFormat.CSV -> Color(0xFF43A047) // green
        BookFormat.PPTX, BookFormat.ODP, BookFormat.PPT -> Color(0xFFFF7043) // orange
        BookFormat.CBZ, BookFormat.CBR, BookFormat.CBT -> Color(0xFFAB47BC) // purple
        BookFormat.MARKDOWN -> Color(0xFF78909C)  // blue-gray
        BookFormat.CODE -> Color(0xFF66BB6A)      // green
        BookFormat.JSON, BookFormat.XML -> Color(0xFFFF8F00) // amber
        BookFormat.HTML -> Color(0xFFEF6C00)      // deep orange
        else -> LuminarGold.copy(alpha = 0.5f)
    }
}

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
