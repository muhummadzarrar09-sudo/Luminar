package com.luminar.reader.presentation.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminar.reader.data.local.db.BookDao
import com.luminar.reader.data.local.db.PageContentDao
import com.luminar.reader.data.model.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val selectedTab: Int = 0,
    val currentBookId: Long? = null,
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val isIndexingComplete: Boolean = true
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pageContentDao: PageContentDao,
    private val bookDao: BookDao
) : ViewModel() {

    private val bookIdArg: String? = savedStateHandle["bookId"]
    private val initialBookId: Long? = bookIdArg?.toLongOrNull()

    private val _uiState = MutableStateFlow(
        SearchUiState(
            currentBookId = initialBookId,
            selectedTab = if (initialBookId != null) 0 else 1
        )
    )
    val uiState = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        checkIndexingStatus()
        observeQuery()
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery, isSearching = newQuery.isNotBlank()) }
        queryFlow.value = newQuery
    }

    fun onTabSelected(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
        performSearch(queryFlow.value)
    }

    private fun checkIndexingStatus() {
        viewModelScope.launch {
            if (initialBookId != null) {
                val book = bookDao.getBookById(initialBookId).first()
                _uiState.update { it.copy(isIndexingComplete = (book?.indexingProgress ?: 100) >= 100) }
            }
        }
    }

    private fun observeQuery() {
        viewModelScope.launch {
            queryFlow.debounce(300).collect { q ->
                performSearch(q)
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }

        viewModelScope.launch {
            val bookId = _uiState.value.currentBookId
            val tab = _uiState.value.selectedTab
            val res = runCatching {
                if (tab == 0 && bookId != null) {
                    pageContentDao.search(bookId, "$query*")
                } else {
                    pageContentDao.searchGlobal("$query*")
                }
            }.getOrDefault(emptyList())

            _uiState.update { it.copy(results = res, isSearching = false) }
        }
    }
}
