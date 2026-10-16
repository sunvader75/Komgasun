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
    val selectedFilter: ItemFilter = ItemFilter.ALL,
    val errorMessage: String? = null
)

class LibrariesViewModel(
    private val repository: KomgaRepository = KomgasunApp.instance.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibrariesUiState())
    val uiState: StateFlow<LibrariesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preferencesManager.librariesFilterFlow.collect { savedFilter ->
                _uiState.update { it.copy(selectedFilter = savedFilter) }
            }
        }
        loadLibraries()
    }

    fun setFilter(filter: ItemFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
        viewModelScope.launch {
            repository.preferencesManager.saveLibrariesFilter(filter)
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
