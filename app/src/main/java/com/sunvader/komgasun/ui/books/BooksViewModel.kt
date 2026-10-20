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
                                if (override != null) book.withReadingProgress(override) else book
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

    /** Alterna un filtro; [ItemFilter.ALL] equivale a borrar la selección persistida. */
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

    /** Publica y guarda los filtros sin [ItemFilter.ALL]; el conjunto vacío representa todos. */
    fun setFilters(filters: Set<ItemFilter>) {
        val cleaned = filters.filter { it != ItemFilter.ALL }.toSet()
        _uiState.update { it.copy(selectedFilters = cleaned) }
        viewModelScope.launch {
            repository.preferencesManager.saveBooksFilter(cleaned)
        }
    }

    /**
     * Recarga los libros de la serie y los ordena por numeración, conservando la lista si falla.
     * [showLoading] activa el indicador y borra el error previo; por defecto solo lo hace sin libros.
     * Los fallos del repositorio se publican en [uiState] y siempre desactivan el indicador.
     */
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

    /**
     * Sincroniza el estado leído/no leído y actualiza el progreso local solo tras el éxito.
     * Ignora otra petición para el mismo libro mientras esté pendiente, sin llamar a [onDone].
     * Al completar usa el total de páginas; al desmarcar vuelve a cero. Libera la marca de
     * operación pendiente incluso si la acción o [onDone] lanza una excepción.
     */
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
                            if (it.id == book.id) it.withReadingProgress(progress) else it
                        })
                    }
                }
                onDone(result)
            } finally {
                _uiState.update { it.copy(updatingReadStatus = it.updatingReadStatus - book.id) }
            }
        }
    }

    /** Solicita borrar el archivo del libro y lanza una recarga antes de entregar el resultado. */
    fun deleteBook(bookId: String, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteBookFile(bookId)
            loadBooks()
            onDone(result)
        }
    }

    /** Solicita borrar los archivos de toda la serie; recarga incluso si falla y avisa a [onDone]. */
    fun deleteSeries(onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteSeriesFile(seriesId)
            loadBooks()
            onDone(result)
        }
    }

    /**
     * Solicita purgar la papelera de la biblioteca y lanza una recarga antes de avisar a [onDone].
     * Sin identificador de biblioteca devuelve un fallo mediante [onDone], sin acceder al servidor.
     * La confirmación de esta operación destructiva corresponde a quien la invoque.
     */
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
