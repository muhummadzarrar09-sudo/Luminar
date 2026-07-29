package dev.recto.reader.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.recto.reader.R
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import dev.recto.reader.ui.reader.SearchHitRow
import dev.recto.reader.data.BookFormat
import dev.recto.reader.data.db.BookEntity

@Composable
fun LibraryScreen(
    onOpenBook: (BookEntity) -> Unit,
    onOpenBookAt: (BookEntity, Int) -> Unit = { b, _ -> onOpenBook(b) },
    vm: LibraryViewModel = viewModel()
) {
    val books by vm.books.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val selectedCollection by vm.selectedCollection.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val deepSearch by vm.deepSearch.collectAsStateWithLifecycle()

    // Instant title/author filter over what is already on screen.
    val visibleBooks = remember(books, filter) {
        val q = filter.trim()
        if (q.length < 2) {
            books
        } else {
            books.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.author?.contains(q, ignoreCase = true) == true
            }
        }
    }

    val snackbars = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var showShelfDialog by remember { mutableStateOf(false) }

    val selecting = selection.isNotEmpty()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.importFromPicker(uris) }

    LaunchedEffect(message) {
        message?.let {
            snackbars.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // Back exits selection mode before it exits the screen.
    BackHandler(enabled = selecting) { vm.clearSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionBar(
                    count = selection.size,
                    inCollection = selectedCollection != null,
                    onClear = vm::clearSelection,
                    onSelectAll = vm::selectAll,
                    onAddToShelf = { showShelfDialog = true },
                    onRemoveFromShelf = vm::removeSelectionFromCurrentCollection,
                    onDelete = { confirmDelete = true }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            if (books.isNotEmpty() && !selecting) {
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(BookFormat.pickerMimeTypes) },
                    text = { Text("Add books") },
                    icon = { Text("+", style = MaterialTheme.typography.titleLarge) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.fillMaxSize()) {

                if (!selecting) {
                    LibrarySearchBar(
                        query = filter,
                        onQueryChange = vm::setFilter,
                        onDeepSearch = vm::runDeepSearch,
                        deepSearch = deepSearch,
                        onOpenHit = { hit ->
                            books.firstOrNull { it.id == hit.bookId }?.let { book ->
                                vm.open(book)
                                onOpenBookAt(book, hit.charOffset)
                            }
                        },
                        onCancelDeep = vm::cancelDeepSearch
                    )
                }

                if (collections.isNotEmpty() && !selecting) {
                    ShelfChips(
                        collections = collections,
                        selected = selectedCollection,
                        onSelect = vm::selectCollection,
                        onDeleteShelf = vm::deleteCollection
                    )
                }

                when {
                    visibleBooks.isEmpty() && filter.trim().length >= 2 &&
                        !deepSearch.running && deepSearch.hits.isEmpty() ->
                        NoLocalMatches(
                            query = filter,
                            onDeepSearch = vm::runDeepSearch,
                            searched = deepSearch.booksSearched > 0
                        )

                    books.isEmpty() && !importing && selectedCollection == null ->
                        EmptyLibrary(onPick = { picker.launch(BookFormat.pickerMimeTypes) })

                    books.isEmpty() && !importing ->
                        EmptyShelf(onBackToAll = { vm.selectCollection(null) })

                    else -> BookGrid(
                        books = visibleBooks,
                        selection = selection,
                        selecting = selecting,
                        onOpen = { book ->
                            if (selecting) {
                                vm.toggleSelection(book.id)
                            } else {
                                vm.open(book)
                                onOpenBook(book)
                            }
                        },
                        onLongPress = { vm.toggleSelection(it.id) }
                    )
                }
            }

            if (importing) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    if (confirmDelete) {
        val n = selection.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (n == 1) "Remove this book?" else "Remove $n books?") },
            text = {
                Text(
                    "This removes them from your library. Files you added from " +
                        "your phone stay where they are; copies Recto made are deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteSelected()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    if (showShelfDialog) {
        ShelfPickerDialog(
            collections = collections,
            onDismiss = { showShelfDialog = false },
            onPick = { id, name ->
                showShelfDialog = false
                vm.addSelectionTo(id, name)
            },
            onCreate = { name ->
                showShelfDialog = false
                vm.createCollectionWithSelection(name)
            }
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    inCollection: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onAddToShelf: () -> Unit,
    onRemoveFromShelf: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClear) { Text("Done") }
                Text(
                    text = "$count selected",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                TextButton(onClick = onSelectAll) { Text("All") }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = onAddToShelf, label = { Text("Add to shelf") })
                if (inCollection) {
                    AssistChip(
                        onClick = onRemoveFromShelf,
                        label = { Text("Remove from shelf") }
                    )
                }
                AssistChip(
                    onClick = onDelete,
                    label = { Text("Remove", color = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}

@Composable
private fun ShelfChips(
    collections: List<dev.recto.reader.data.db.CollectionWithCount>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    onDeleteShelf: (Long) -> Unit
) {
    var confirmShelf by remember { mutableStateOf<Long?>(null) }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All books") }
        )
        collections.forEach { c ->
            FilterChip(
                selected = selected == c.id,
                onClick = { onSelect(c.id) },
                label = { Text("${c.name} (${c.bookCount})") }
            )
        }
        if (selected != null) {
            AssistChip(
                onClick = { confirmShelf = selected },
                label = { Text("Delete shelf") }
            )
        }
    }

    confirmShelf?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmShelf = null },
            title = { Text("Delete this shelf?") },
            text = { Text("The books stay in your library. Only the shelf is removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmShelf = null
                    onDeleteShelf(id)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmShelf = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ShelfPickerDialog(
    collections: List<dev.recto.reader.data.db.CollectionWithCount>,
    onDismiss: () -> Unit,
    onPick: (Long, String) -> Unit,
    onCreate: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to shelf") },
        text = {
            Column {
                collections.forEach { c ->
                    TextButton(
                        onClick = { onPick(c.id, c.name) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(c.name, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New shelf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(newName) },
                enabled = newName.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BookGrid(
    books: List<BookEntity>,
    selection: Set<Long>,
    selecting: Boolean,
    onOpen: (BookEntity) -> Unit,
    onLongPress: (BookEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookCell(
                book = book,
                selected = book.id in selection,
                selecting = selecting,
                onClick = { onOpen(book) },
                onLongClick = { onLongPress(book) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCell(
    book: BookEntity,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .shadow(3.dp, RoundedCornerShape(3.dp))
                .clip(RoundedCornerShape(3.dp))
        ) {
            Box(Modifier.alpha(if (selecting && !selected) 0.55f else 1f)) {
                BookCover(
                    coverPath = book.coverPath,
                    title = book.title,
                    author = book.author,
                    modifier = Modifier.fillMaxSize()
                )
            }

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
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "OK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
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
private fun EmptyShelf(onBackToAll: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Nothing on this shelf yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Long-press a book in All books to add it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onBackToAll) { Text("Back to all books") }
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
        // The app mark itself, rather than a lookalike drawn in Compose.
        // One source of truth means the empty state can never drift away
        // from the launcher icon.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(132.dp)
        )

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
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        ExtendedFloatingActionButton(
            onClick = onPick,
            text = { Text("Choose files") },
            icon = { Text("+", style = MaterialTheme.typography.titleLarge) }
        )
    }
}

/**
 * Library search: an instant title filter, with full-text search across every
 * book as an explicit second step.
 *
 * Two tiers on purpose. Filtering titles is free and happens as you type.
 * Reading every book off disk costs seconds, so it only runs when you ask -
 * that is the "which book was that in?" failsafe, not something to fire on
 * every keystroke.
 */
@Composable
private fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onDeepSearch: () -> Unit,
    deepSearch: dev.recto.reader.data.search.LibrarySearchProgress,
    onOpenHit: (dev.recto.reader.data.search.SearchHit) -> Unit,
    onCancelDeep: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search your library") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = {
                        onQueryChange("")
                        onCancelDeep()
                    }) { Text("Clear") }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (query.trim().length >= 2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        deepSearch.running ->
                            "Searching inside books " +
                                "${deepSearch.booksSearched}/${deepSearch.booksTotal}"

                        deepSearch.done && deepSearch.hits.isEmpty() ->
                            "Nothing inside your books either"

                        deepSearch.hits.isNotEmpty() ->
                            "${deepSearch.hits.size} matches inside books"

                        else -> "Not the book you meant?"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                if (!deepSearch.running && deepSearch.booksTotal == 0) {
                    TextButton(onClick = onDeepSearch) { Text("Search inside") }
                }
            }

            if (deepSearch.running) {
                LinearProgressIndicator(
                    progress = {
                        if (deepSearch.booksTotal == 0) 0f
                        else deepSearch.booksSearched.toFloat() / deepSearch.booksTotal
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(2.dp)
                )
            }

            if (deepSearch.hits.isNotEmpty()) {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    listItems(
                        deepSearch.hits,
                        key = { "${it.bookId}:${it.charOffset}" }
                    ) { hit ->
                        SearchHitRow(hit = hit, showBook = true, onClick = { onOpenHit(hit) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NoLocalMatches(query: String, onDeepSearch: () -> Unit, searched: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No book titled \"$query\"",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (searched) {
                "And no matches inside your books."
            } else {
                "Try searching inside your books - it reads every one, so it " +
                    "takes a few seconds."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!searched) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDeepSearch) { Text("Search inside books") }
        }
    }
}
