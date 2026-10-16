package com.example.komgasun.ui.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.komgasun.KomgasunApp
import com.example.komgasun.data.model.ItemFilter
import com.example.komgasun.data.model.LibraryDto
import com.example.komgasun.data.model.ServerType
import com.example.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibrariesUiState(
    val isLoading: Boolean = false,
    val libraries: List<LibraryDto> = emptyList(),
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
            repository.preferencesManager.saveLibrariesFilter(cleaned)
        }
    }

    fun loadLibraries() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
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

    fun emptyTrash(libraryId: String, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.emptyTrash(libraryId)
            loadLibraries()
            onDone(result)
        }
    }

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

    fun saveServerUrls(localUrl: String, remoteUrl: String, activeType: ServerType, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveServerUrls(localUrl, remoteUrl, activeType)
            loadLibraries()
            onSaved()
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onLoggedOut()
        }
    }
}
