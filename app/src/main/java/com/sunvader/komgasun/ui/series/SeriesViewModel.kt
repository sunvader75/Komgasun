package com.sunvader.komgasun.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunvader.komgasun.KomgasunApp
import com.sunvader.komgasun.data.model.ItemFilter
import com.sunvader.komgasun.data.model.SeriesDto
import com.sunvader.komgasun.data.model.SeriesSortOrder
import com.sunvader.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeriesUiState(
    val isLoading: Boolean = false,
    val seriesList: List<SeriesDto> = emptyList(),
    val selectedFilters: Set<ItemFilter> = emptySet(),
    val sortOrder: SeriesSortOrder = SeriesSortOrder.ALPHABETICAL,
    val randomSeed: Long = System.currentTimeMillis(),
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
            repository.preferencesManager.seriesFilterFlow.collect { savedFilters ->
                _uiState.update { it.copy(selectedFilters = savedFilters) }
            }
        }
        viewModelScope.launch {
            repository.preferencesManager.seriesSortFlow.collect { savedSort ->
                _uiState.update { it.copy(sortOrder = savedSort) }
            }
        }
        viewModelScope.launch {
            val config = repository.getActiveConfig()
            _uiState.update { it.copy(serverUrl = config?.serverUrl.orEmpty()) }
            loadSeries()
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
            repository.preferencesManager.saveSeriesFilter(cleaned)
        }
    }

    fun setSortOrder(order: SeriesSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        viewModelScope.launch {
            repository.preferencesManager.saveSeriesSort(order)
        }
    }

    fun reshuffle() {
        _uiState.update { it.copy(randomSeed = System.currentTimeMillis()) }
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

    fun deleteSeries(seriesId: String, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteSeriesFile(seriesId)
            loadSeries()
            onDone(result)
        }
    }

    fun emptyTrash(onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.emptyTrash(libraryId)
            loadSeries()
            onDone(result)
        }
    }
}
