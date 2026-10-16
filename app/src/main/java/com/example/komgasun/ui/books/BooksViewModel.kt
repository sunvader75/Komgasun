package com.example.komgasun.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.komgasun.KomgasunApp
import com.example.komgasun.data.model.BookDto
import com.example.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BooksUiState(
    val isLoading: Boolean = false,
    val books: List<BookDto> = emptyList(),
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
            val config = repository.getActiveConfig()
            _uiState.update { it.copy(serverUrl = config?.serverUrl.orEmpty()) }
            loadBooks()
        }
    }

    fun loadBooks() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
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
