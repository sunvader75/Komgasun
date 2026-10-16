package com.example.komgasun.ui.reader.comic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.komgasun.KomgasunApp
import com.example.komgasun.data.model.BookPageDto
import com.example.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComicReaderUiState(
    val isLoading: Boolean = false,
    val pages: List<BookPageDto> = emptyList(),
    val initialPage: Int = 0,
    val currentPage: Int = 0,
    val serverUrl: String = "",
    val showControls: Boolean = true,
    val errorMessage: String? = null
)

class ComicReaderViewModel(
    private val bookId: String,
    private val repository: KomgaRepository = KomgasunApp.instance.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComicReaderUiState())
    val uiState: StateFlow<ComicReaderUiState> = _uiState.asStateFlow()
    private var lastSyncedPage: Int = -1

    init {
        viewModelScope.launch {
            val config = repository.getActiveConfig()
            _uiState.update { it.copy(serverUrl = config?.serverUrl.orEmpty()) }
            loadPages()
        }
    }

    fun loadPages() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val bookResult = repository.getBook(bookId)
            val pagesResult = repository.getBookPages(bookId)

            if (pagesResult.isSuccess) {
                val pageList = pagesResult.getOrThrow()
                val readProgress = bookResult.getOrNull()?.readProgress
                val isCompleted = readProgress?.completed == true
                val savedPage = readProgress?.page ?: 1

                // If already completed, re-reading starts from page 1 (index 0). Otherwise resume.
                val initialPageIndex = if (isCompleted) {
                    0
                } else {
                    (savedPage - 1).coerceIn(0, (pageList.size - 1).coerceAtLeast(0))
                }

                lastSyncedPage = initialPageIndex + 1
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pages = pageList,
                        initialPage = initialPageIndex,
                        currentPage = initialPageIndex
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = pagesResult.exceptionOrNull()?.localizedMessage ?: "Error al cargar páginas"
                    )
                }
            }
        }
    }

    fun onPageChanged(pageIndex: Int, totalPages: Int) {
        _uiState.update { it.copy(currentPage = pageIndex) }
        val pageNumber = pageIndex + 1
        if (pageNumber == lastSyncedPage) return
        lastSyncedPage = pageNumber

        val isCompleted = pageNumber >= totalPages && totalPages > 0
        repository.syncReadProgressAsync(bookId = bookId, page = pageNumber, completed = isCompleted)
    }

    fun flushProgress() {
        val total = _uiState.value.pages.size
        val pageNumber = _uiState.value.currentPage + 1
        if (total > 0 && pageNumber in 1..total) {
            val isCompleted = pageNumber >= total
            repository.syncReadProgressAsync(bookId = bookId, page = pageNumber, completed = isCompleted)
        }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun setControlsVisible(visible: Boolean) {
        _uiState.update { it.copy(showControls = visible) }
    }
}
