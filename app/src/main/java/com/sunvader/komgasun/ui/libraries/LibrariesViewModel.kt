package com.sunvader.komgasun.ui.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunvader.komgasun.KomgasunApp
import com.sunvader.komgasun.data.model.BookDto
import com.sunvader.komgasun.data.model.ItemFilter
import com.sunvader.komgasun.data.model.LibraryDto
import com.sunvader.komgasun.data.model.ServerType
import com.sunvader.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibrariesUiState(
    val isLoading: Boolean = false,
    val libraries: List<LibraryDto> = emptyList(),
    val inProgressBooks: List<BookDto> = emptyList(),
    val progressLoading: Boolean = false,
    val progressError: String? = null,
    val updatingBooks: Set<String> = emptySet(),
    val selectedFilters: Set<ItemFilter> = emptySet(),
    val errorMessage: String? = null,
    val activeServerType: ServerType = ServerType.LOCAL,
    val localUrl: String = "",
    val remoteUrl: String = "",
    val activeUrl: String = ""
)

class LibrariesViewModel(
    private val repository: KomgaRepository = KomgasunApp.instance.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibrariesUiState())
    val uiState: StateFlow<LibrariesUiState> = _uiState.asStateFlow()
    private var progressRevision = 0

    init {
        viewModelScope.launch {
            repository.preferencesManager.librariesFilterFlow.collect { savedFilters ->
                _uiState.update { it.copy(selectedFilters = savedFilters) }
            }
        }
        viewModelScope.launch {
            repository.activeServerTypeFlow.collect { type ->
                _uiState.update { it.copy(activeServerType = type) }
            }
        }
        viewModelScope.launch {
            repository.localUrlFlow.collect { url ->
                _uiState.update { it.copy(localUrl = url) }
            }
        }
        viewModelScope.launch {
            repository.remoteUrlFlow.collect { url ->
                _uiState.update { it.copy(remoteUrl = url) }
            }
        }
        viewModelScope.launch {
            repository.serverConfigFlow.collect { config ->
                _uiState.update { it.copy(activeUrl = config?.serverUrl ?: "") }
            }
        }
        loadLibraries()
    }

    /** Alterna un filtro o borra todos si se elige [ItemFilter.ALL]. */
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

    /** Publica y persiste los filtros sin [ItemFilter.ALL]; vacío representa todas las bibliotecas. */
    fun setFilters(filters: Set<ItemFilter>) {
        val cleaned = filters.filter { it != ItemFilter.ALL }.toSet()
        _uiState.update { it.copy(selectedFilters = cleaned) }
        viewModelScope.launch {
            repository.preferencesManager.saveLibrariesFilter(cleaned)
        }
    }

    /**
     * Recarga «Continuar leyendo» con indicador y error independientes de las bibliotecas.
     * No sustituye la lista si una mutación confirmada ha cambiado su revisión desde el inicio;
     * así una respuesta antigua no restaura libros retirados tras modificar su estado o borrarlos.
     */
    fun loadInProgressBooks() {
        val revision = progressRevision
        _uiState.update { it.copy(progressLoading = true, progressError = null) }
        viewModelScope.launch {
            repository.getInProgressBooks().onSuccess { books ->
                _uiState.update {
                    it.copy(
                        progressLoading = false,
                        inProgressBooks = if (revision == progressRevision) books else it.inProgressBooks
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(progressLoading = false, progressError = error.localizedMessage ?: "Error al cargar lecturas en progreso") }
            }
        }
    }

    /** Inicia en paralelo la recarga de bibliotecas con indicador y la de lecturas en curso. */
    fun loadLibraries() {
        loadInProgressBooks()
        refreshLibraries(showLoading = true)
    }

    /** Recarga las bibliotecas, conservando los datos previos en caso de error. */
    private fun refreshLibraries(showLoading: Boolean = false) {
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        }
        viewModelScope.launch {
            val result = repository.getLibraries()
            result.onSuccess { list ->
                _uiState.update { it.copy(isLoading = false, libraries = list) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Error al cargar bibliotecas"
                    )
                }
            }
        }
    }

    /** Sincroniza el estado de un libro en curso y lo retira de la lista si la operación tiene éxito. */
    fun setBookReadStatus(book: BookDto, completed: Boolean, onDone: (Result<Unit>) -> Unit = {}) {
        updateBook(book.id, onDone) { repository.setBookReadStatus(book, completed) }
    }

    /** Solicita borrar el archivo de un libro en curso; la confirmación corresponde a la pantalla. */
    fun deleteBook(bookId: String, onDone: (Result<Unit>) -> Unit = {}) {
        updateBook(bookId, onDone) { repository.deleteBookFile(bookId) }
    }

    /**
     * Serializa las mutaciones por libro y exige que siga presente en las lecturas en curso.
     * Si se rechaza la petición no se llama a [onDone]. Tras el éxito invalida respuestas antiguas,
     * retira el libro y lanza la recarga de bibliotecas; libera siempre la marca de operación.
     */
    private fun updateBook(
        bookId: String,
        onDone: (Result<Unit>) -> Unit,
        action: suspend () -> Result<Unit>
    ) {
        if (bookId in _uiState.value.updatingBooks) return
        if (_uiState.value.inProgressBooks.none { it.id == bookId }) return
        _uiState.update { it.copy(updatingBooks = it.updatingBooks + bookId) }
        viewModelScope.launch {
            try {
                val result = action()
                result.onSuccess {
                    // Ignora consultas de progreso anteriores a este cambio confirmado por el servidor.
                    progressRevision++
                    _uiState.update { state ->
                        state.copy(inProgressBooks = state.inProgressBooks.filterNot { it.id == bookId })
                    }
                    refreshLibraries()
                }
                onDone(result)
            } finally {
                _uiState.update { it.copy(updatingBooks = it.updatingBooks - bookId) }
            }
        }
    }

    /** Solicita purgar la papelera indicada y lanza ambas recargas antes de avisar a [onDone]. */
    fun emptyTrash(libraryId: String, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.emptyTrash(libraryId)
            loadLibraries()
            onDone(result)
        }
    }

    /**
     * Purga secuencialmente las bibliotecas presentes al empezar, sin detenerse por un fallo.
     * Lanza la recarga y comunica el último error encontrado, o éxito si no hubo ninguno.
     */
    fun emptyTrashAll(onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val list = _uiState.value.libraries
            var lastError: Throwable? = null
            for (lib in list) {
                val res = repository.emptyTrash(lib.id)
                if (res.isFailure) lastError = res.exceptionOrNull()
            }
            loadLibraries()
            if (lastError != null) {
                onDone(Result.failure(lastError))
            } else {
                onDone(Result.success(Unit))
            }
        }
    }

    /**
     * Delega el cambio de conexión y devuelve la URL mediante [onResult].
     * Una URL vacía se comunica como fallo; solo con una URL no vacía se recargan las listas.
     */
    fun switchServer(type: ServerType, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val targetUrl = repository.switchServer(type)
            if (targetUrl.isBlank()) {
                onResult(Result.failure(IllegalStateException("No hay URL configurada para ${type.label}")))
            } else {
                loadLibraries()
                onResult(Result.success(targetUrl))
            }
        }
    }

    /** Guarda ambas direcciones y el tipo activo; inicia la recarga antes de llamar a [onSaved]. */
    fun saveServerUrls(localUrl: String, remoteUrl: String, activeType: ServerType, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveServerUrls(localUrl, remoteUrl, activeType)
            loadLibraries()
            onSaved()
        }
    }

    /** Delega el cierre de sesión y llama a [onLoggedOut] después, para permitir la navegación. */
    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onLoggedOut()
        }
    }
}
