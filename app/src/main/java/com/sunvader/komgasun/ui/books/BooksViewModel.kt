package com.sunvader.komgasun.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunvader.komgasun.KomgasunApp
import com.sunvader.komgasun.data.model.BookDto
import com.sunvader.komgasun.data.model.ItemFilter
import com.sunvader.komgasun.data.model.sortedByNumbering
import com.sunvader.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BooksUiState(
    val isLoading: Boolean = false,
    val books: List<BookDto> = emptyList(),
    val selectedFilters: Set<ItemFilter> = emptySet(),
    val serverUrl: String = "",
    val updatingReadStatus: Set<String> = emptySet(),
    val errorMessage: String? = null
)

class BooksViewModel(
    private val seriesId: String,
    val libraryId: String = "",
    private val repository: KomgaRepository = KomgasunApp.instance.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BooksUiState())
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preferencesManager.booksFilterFlow.collect { savedFilters ->
                _uiState.update { it.copy(selectedFilters = savedFilters) }
            }
        }
        viewModelScope.launch {
            repository.recentProgress.collect { overrides ->
                if (overrides.isNotEmpty()) {
                    _uiState.update { current ->
                        current.copy(
                            books = current.books.map { book ->
                                val override = overrides[book.id]
                                if (override != null) book.copy(readProgress = override) else book
                            }
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            val config = repository.getActiveConfig()
            _uiState.update { it.copy(serverUrl = config?.serverUrl.orEmpty()) }
            loadBooks(showLoading = true)
        }
    }

    fun toggleFilter(filter: ItemFilter) {
        if (filter == ItemFilter.ALL) {
            clearFilters()
            return
        }
        val current = _uiState.value.selectedFilters
        val updated = if (current.contains(filter)) current - filter else current + filter
        setFilters(updated)
    }

    fun clearFilters() {
        setFilters(emptySet())
    }

    fun setFilters(filters: Set<ItemFilter>) {
        val cleaned = filters.filter { it != ItemFilter.ALL }.toSet()
        _uiState.update { it.copy(selectedFilters = cleaned) }
        viewModelScope.launch {
            repository.preferencesManager.saveBooksFilter(cleaned)
        }
    }

    fun loadBooks(showLoading: Boolean = _uiState.value.books.isEmpty()) {
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        }
        viewModelScope.launch {
            val result = repository.getBooks(seriesId)
            result.onSuccess { list ->
                _uiState.update { it.copy(isLoading = false, books = list.sortedByNumbering()) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Error al cargar libros"
                    )
                }
            }
        }
    }

    fun setBookReadStatus(book: BookDto, completed: Boolean, onDone: (Result<Unit>) -> Unit = {}) {
        if (book.id in _uiState.value.updatingReadStatus) return
        _uiState.update { it.copy(updatingReadStatus = it.updatingReadStatus + book.id) }
        viewModelScope.launch {
            try {
                val result = repository.setBookReadStatus(book, completed)
                result.onSuccess {
                    val progress = com.sunvader.komgasun.data.model.ReadProgressDto(
                        page = if (completed) book.media.pagesCount else 0,
                        completed = completed
                    )
                    _uiState.update { state ->
                        state.copy(books = state.books.map {
                            if (it.id == book.id) it.copy(readProgress = progress) else it
                        })
                    }
                }
                onDone(result)
            } finally {
                _uiState.update { it.copy(updatingReadStatus = it.updatingReadStatus - book.id) }
            }
        }
    }

    fun deleteBook(bookId: String, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteBookFile(bookId)
            loadBooks()
            onDone(result)
        }
    }

    fun deleteSeries(onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteSeriesFile(seriesId)
            loadBooks()
            onDone(result)
        }
    }

    fun emptyTrash(onDone: (Result<Unit>) -> Unit = {}) {
        if (libraryId.isBlank()) {
            onDone(Result.failure(IllegalStateException("ID de biblioteca no disponible")))
            return
        }
        viewModelScope.launch {
            val result = repository.emptyTrash(libraryId)
            loadBooks()
            onDone(result)
        }
    }
}
