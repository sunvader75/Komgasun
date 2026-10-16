package com.example.komgasun.ui.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.komgasun.KomgasunApp
import com.example.komgasun.data.model.ItemFilter
import com.example.komgasun.data.model.LibraryDto
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
    val errorMessage: String? = null
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

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onLoggedOut()
        }
    }
}
