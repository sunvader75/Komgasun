package com.example.komgasun.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.komgasun.KomgasunApp
import com.example.komgasun.data.model.BookDto
import com.example.komgasun.data.model.ItemFilter
import com.example.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BooksUiState(
    val isLoading: Boolean = false,
    val books: List<BookDto> = emptyList(),
    val selectedFilter: ItemFilter = ItemFilter.ALL,
    val serverUrl: String = "",
    val errorMessage: String? = null
)

class BooksViewModel(
    private val seriesId: String,
    private val repository: KomgaRepository = KomgasunApp.instance.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BooksUiState())
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preferencesManager.booksFilterFlow.collect { savedFilter ->
                _uiState.update { it.copy(selectedFilter = savedFilter) }
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

    fun setFilter(filter: ItemFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
        viewModelScope.launch {
            repository.preferencesManager.saveBooksFilter(filter)
        }
    }

    fun loadBooks(showLoading: Boolean = _uiState.value.books.isEmpty()) {
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        }
        viewModelScope.launch {
            val result = repository.getBooks(seriesId)
            result.onSuccess { list ->
                _uiState.update { it.copy(isLoading = false, books = list) }
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
}
