package com.example.komgasun.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.komgasun.KomgasunApp
import com.example.komgasun.data.model.ItemFilter
import com.example.komgasun.data.model.SeriesDto
import com.example.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeriesUiState(
    val isLoading: Boolean = false,
    val seriesList: List<SeriesDto> = emptyList(),
    val selectedFilter: ItemFilter = ItemFilter.ALL,
    val serverUrl: String = "",
    val errorMessage: String? = null
)

class SeriesViewModel(
    private val libraryId: String,
    private val repository: KomgaRepository = KomgasunApp.instance.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeriesUiState())
    val uiState: StateFlow<SeriesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preferencesManager.seriesFilterFlow.collect { savedFilter ->
                _uiState.update { it.copy(selectedFilter = savedFilter) }
            }
        }
        viewModelScope.launch {
            val config = repository.getActiveConfig()
            _uiState.update { it.copy(serverUrl = config?.serverUrl.orEmpty()) }
            loadSeries()
        }
    }

    fun setFilter(filter: ItemFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
        viewModelScope.launch {
            repository.preferencesManager.saveSeriesFilter(filter)
        }
    }

    fun loadSeries(showLoading: Boolean = _uiState.value.seriesList.isEmpty()) {
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        }
        viewModelScope.launch {
            val result = repository.getSeries(libraryId)
            result.onSuccess { list ->
                _uiState.update { it.copy(isLoading = false, seriesList = list) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Error al cargar series"
                    )
                }
            }
        }
    }
}
