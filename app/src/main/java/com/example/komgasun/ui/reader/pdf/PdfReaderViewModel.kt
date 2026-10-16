package com.example.komgasun.ui.reader.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.komgasun.KomgasunApp
import com.example.komgasun.data.model.BookPageDto
import com.example.komgasun.data.remote.KomgaApiClient
import com.example.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface PdfSourceMode {
    data object StreamFromKomga : PdfSourceMode
    data class NativeRenderer(val totalPages: Int) : PdfSourceMode
}

data class PdfReaderUiState(
    val isLoading: Boolean = false,
    val downloadProgress: Float? = null,
    val streamPages: List<BookPageDto> = emptyList(),
    val initialPage: Int = 0,
    val serverUrl: String = "",
    val mode: PdfSourceMode = PdfSourceMode.StreamFromKomga,
    val showControls: Boolean = true,
    val errorMessage: String? = null
)

class PdfReaderViewModel(
    application: Application,
    private val bookId: String,
    private val repository: KomgaRepository = KomgasunApp.instance.repository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PdfReaderUiState())
    val uiState: StateFlow<PdfReaderUiState> = _uiState.asStateFlow()

    private var pdfRenderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private val bitmapCache = mutableMapOf<Int, Bitmap>()
    private var lastSyncedPage: Int = -1

    init {
        viewModelScope.launch {
            val config = repository.getActiveConfig()
            _uiState.update { it.copy(serverUrl = config?.serverUrl.orEmpty()) }
            initReader()
        }
    }

    fun initReader() {
        viewModelScope.launch {
            val bookResult = repository.getBook(bookId)
            val readProgress = bookResult.getOrNull()?.readProgress
            val isCompleted = readProgress?.completed == true
            val savedPage = readProgress?.page ?: 1
            val initialPageIndex = if (isCompleted) 0 else (savedPage - 1).coerceAtLeast(0)
            lastSyncedPage = initialPageIndex + 1
            _uiState.update { it.copy(initialPage = initialPageIndex) }

            val cachedPdf = getLocalPdfFile()
            if (cachedPdf.exists() && cachedPdf.length() > 0) {
                setupNativeRenderer(cachedPdf)
            } else {
                loadStreamPages()
            }
        }
    }

    private fun getLocalPdfFile(): File {
        val dir = File(getApplication<Application>().cacheDir, "pdf_books")
        dir.mkdirs()
        return File(dir, "$bookId.pdf")
    }

    fun loadStreamPages() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, mode = PdfSourceMode.StreamFromKomga) }
        viewModelScope.launch {
            val result = repository.getBookPages(bookId)
            result.onSuccess { pages ->
                val validInitialPage = _uiState.value.initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        streamPages = pages,
                        initialPage = validInitialPage,
                        mode = PdfSourceMode.StreamFromKomga
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Error al cargar páginas de PDF"
                    )
                }
            }
        }
    }

    fun downloadAndRenderLocally() {
        _uiState.update { it.copy(isLoading = true, downloadProgress = 0f, errorMessage = null) }
        viewModelScope.launch {
            val destFile = getLocalPdfFile()
            val result = repository.downloadBookFile(
                bookId = bookId,
                destinationFile = destFile,
                onProgress = { progress ->
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
            )

            result.onSuccess { file ->
                _uiState.update { it.copy(isLoading = false, downloadProgress = null) }
                setupNativeRenderer(file)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        downloadProgress = null,
                        errorMessage = "Error al descargar PDF: ${error.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun setupNativeRenderer(file: File) {
        try {
            closeRenderer()
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd = descriptor
            val renderer = PdfRenderer(descriptor)
            pdfRenderer = renderer
            val validInitialPage = _uiState.value.initialPage.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
            _uiState.update {
                it.copy(
                    isLoading = false,
                    initialPage = validInitialPage,
                    mode = PdfSourceMode.NativeRenderer(renderer.pageCount)
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Error al abrir el renderizador de PDF: ${e.localizedMessage}"
                )
            }
        }
    }

    fun onPageChanged(pageIndex: Int, totalPages: Int) {
        val pageNumber = pageIndex + 1
        if (pageNumber == lastSyncedPage) return
        lastSyncedPage = pageNumber

        val isCompleted = pageNumber >= totalPages && totalPages > 0
        repository.syncReadProgressAsync(bookId = bookId, page = pageNumber, completed = isCompleted)
    }

    fun flushProgress() {
        val total = when (val mode = _uiState.value.mode) {
            is PdfSourceMode.NativeRenderer -> mode.totalPages
            is PdfSourceMode.StreamFromKomga -> _uiState.value.streamPages.size
        }
        val pageNumber = _uiState.value.initialPage + 1
        if (total > 0 && pageNumber in 1..total) {
            val isCompleted = pageNumber >= total
            repository.syncReadProgressAsync(bookId = bookId, page = pageNumber, completed = isCompleted)
        }
    }

    suspend fun renderPageBitmap(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        bitmapCache[pageIndex]?.let { return@withContext it }
        val renderer = pdfRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        synchronized(renderer) {
            val page = renderer.openPage(pageIndex)
            val width = (page.width * 1.5f).toInt()
            val height = (page.height * 1.5f).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmapCache[pageIndex] = bitmap
            bitmap
        }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    private fun closeRenderer() {
        bitmapCache.values.forEach { it.recycle() }
        bitmapCache.clear()
        try {
            pdfRenderer?.close()
            pdfRenderer = null
            pfd?.close()
            pfd = null
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        closeRenderer()
    }
}
