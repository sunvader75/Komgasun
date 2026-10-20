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

    /** Alterna un filtro; [ItemFilter.ALL] elimina la selección mediante [clearFilters]. */
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

    /** Publica y persiste la selección sin [ItemFilter.ALL]; vacío significa sin restricciones. */
    fun setFilters(filters: Set<ItemFilter>) {
        val cleaned = filters.filter { it != ItemFilter.ALL }.toSet()
        _uiState.update { it.copy(selectedFilters = cleaned) }
        viewModelScope.launch {
            repository.preferencesManager.saveSeriesFilter(cleaned)
        }
    }

    /** Cambia el criterio local y lo persiste; la pantalla aplica la ordenación sin otra descarga. */
    fun setSortOrder(order: SeriesSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        viewModelScope.launch {
            repository.preferencesManager.saveSeriesSort(order)
        }
    }

    /** Renueva la semilla con la hora actual para recalcular el orden aleatorio, sin persistirla. */
    fun reshuffle() {
        _uiState.update { it.copy(randomSeed = System.currentTimeMillis()) }
    }

    /**
     * Recarga las series de esta biblioteca y conserva la lista anterior en caso de fallo.
     * [showLoading] muestra la carga y borra el error previo; por defecto solo se activa sin datos.
     * Publica los fallos del repositorio y desactiva la carga tanto en éxito como en error.
     */
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

    /**
     * Solicita borrar los archivos de [seriesId], sin pedir confirmación en este nivel.
     * Lanza la recarga incluso si falla y entrega el resultado a [onDone] sin esperar esa recarga.
     */
    fun deleteSeries(seriesId: String, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteSeriesFile(seriesId)
            loadSeries()
            onDone(result)
        }
    }

    /** Purga la papelera de esta biblioteca y lanza una recarga antes de entregar el resultado. */
    fun emptyTrash(onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.emptyTrash(libraryId)
            loadSeries()
            onDone(result)
        }
    }
}
