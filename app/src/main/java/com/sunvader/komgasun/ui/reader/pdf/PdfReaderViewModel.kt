package com.sunvader.komgasun.ui.reader.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sunvader.komgasun.KomgasunApp
import com.sunvader.komgasun.data.model.BookPageDto
import com.sunvader.komgasun.data.remote.KomgaApiClient
import com.sunvader.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.sunvader.komgasun.ui.reader.readerPageWindow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay

sealed interface PdfSourceMode {
    data object StreamFromKomga : PdfSourceMode
    data class NativeRenderer(val totalPages: Int) : PdfSourceMode
}

data class PdfReaderUiState(
    val isLoading: Boolean = true,
    val isReady: Boolean = false,
    val downloadProgress: Float? = null,
    val streamPages: List<BookPageDto> = emptyList(),
    val initialPage: Int = 0,
    val currentPage: Int = 0,
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
    private val rendererLock = Any()
    private var rendererGeneration = 0
    private val bitmapCache = PdfPageCache<Bitmap>(32L * 1024 * 1024) { it.allocationByteCount.toLong() }
    private var lastSyncedPage: Int = -1

    /**
     * Recupera configuración y progreso de forma asíncrona; convierte páginas desde uno a índices desde cero.
     * Los libros terminados se reabren al principio. Prefiere un PDF local no vacío; si no existe, pide páginas al servidor.
     * El ajuste al límite superior se realiza al conocer las páginas del modo elegido.
     */
    fun initReader() {
        _uiState.update { it.copy(isLoading = true, isReady = false, errorMessage = null) }
        viewModelScope.launch {
            val config = repository.getActiveConfig()
            val serverUrl = config?.serverUrl.orEmpty()
            val bookResult = repository.getBook(bookId)
            val readProgress = bookResult.getOrNull()?.readProgress
            val isCompleted = readProgress?.completed == true
            val savedPage = readProgress?.page ?: 1
            val initialPageIndex = if (isCompleted) 0 else (savedPage - 1).coerceAtLeast(0)
            lastSyncedPage = initialPageIndex + 1
            _uiState.update {
                it.copy(
                    serverUrl = serverUrl,
                    initialPage = initialPageIndex,
                    currentPage = initialPageIndex
                )
            }

            val cachedPdf = getLocalPdfFile()
            if (cachedPdf.exists() && cachedPdf.length() > 0) {
                setupNativeRenderer(cachedPdf)
            } else {
                loadStreamPages()
            }
        }
    }

    /** Crea si es necesario el directorio de caché y devuelve la ruta del PDF del libro, sin crear el propio archivo. */
    private fun getLocalPdfFile(): File {
        val dir = File(getApplication<Application>().cacheDir, "pdf_books")
        dir.mkdirs()
        return File(dir, "$bookId.pdf")
    }

    /** Carga páginas del servidor en viewModelScope, acota la posición inicial y publica disponibilidad o mensaje de error. */
    fun loadStreamPages() {
        _uiState.update { it.copy(isLoading = true, isReady = false, errorMessage = null, mode = PdfSourceMode.StreamFromKomga) }
        viewModelScope.launch {
            val result = repository.getBookPages(bookId)
            result.onSuccess { pages ->
                val validInitialPage = _uiState.value.initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isReady = true,
                        streamPages = pages,
                        initialPage = validInitialPage,
                        currentPage = validInitialPage,
                        mode = PdfSourceMode.StreamFromKomga
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isReady = false,
                        errorMessage = error.localizedMessage ?: "Error al cargar páginas de PDF"
                    )
                }
            }
        }
    }

    /**
     * Descarga a la caché del libro publicando el progreso fraccionario recibido del repositorio.
     * Al terminar abre el renderizador nativo; si falla, limpia el progreso y publica el error sin marcar el lector listo.
     */
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
                        isReady = false,
                        downloadProgress = null,
                        errorMessage = "Error al descargar PDF: ${error.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Sustituye el renderizador bajo rendererLock y asume la propiedad del descriptor de [file], abierto en solo lectura.
     * Publica el modo nativo y una posición acotada; convierte excepciones en error de interfaz.
     * El cierre posterior libera los recursos retenidos, también si la apertura quedó incompleta.
     */
    private fun setupNativeRenderer(file: File) = synchronized(rendererLock) {
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
                    isReady = true,
                    initialPage = validInitialPage,
                    currentPage = validInitialPage,
                    mode = PdfSourceMode.NativeRenderer(renderer.pageCount)
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isReady = false,
                    errorMessage = "Error al abrir el renderizador de PDF: ${e.localizedMessage}"
                )
            }
        }
    }

    /**
     * Recibe un índice válido desde cero y total positivo; solo actúa si el lector está listo.
     * Publica la posición y envía progreso desde uno sin esperar respuesta, deduplicando envíos consecutivos.
     * La última página marca el libro terminado; no valida el índice recibido.
     */
    fun onPageChanged(pageIndex: Int, totalPages: Int) {
        if (!_uiState.value.isReady) return
        _uiState.update { it.copy(currentPage = pageIndex, initialPage = pageIndex) }
        val pageNumber = pageIndex + 1
        if (pageNumber == lastSyncedPage) return
        lastSyncedPage = pageNumber

        val isCompleted = pageNumber >= totalPages && totalPages > 0
        repository.syncReadProgressAsync(bookId = bookId, page = pageNumber, completed = isCompleted)
    }

    /** Envía de nuevo el progreso actual si está dentro del total del modo activo; no espera confirmación remota. */
    fun flushProgress() {
        val total = when (val mode = _uiState.value.mode) {
            is PdfSourceMode.NativeRenderer -> mode.totalPages
            is PdfSourceMode.StreamFromKomga -> _uiState.value.streamPages.size
        }
        val pageNumber = _uiState.value.currentPage + 1
        if (total > 0 && pageNumber in 1..total) {
            val isCompleted = pageNumber >= total
            repository.syncReadProgressAsync(bookId = bookId, page = pageNumber, completed = isCompleted)
        }
    }

    /** Envía el progreso, libera recursos nativos y reinicia disponibilidad y deduplicación para la próxima apertura. */
    fun onReaderClosed() {
        flushProgress()
        closeRenderer()
        _uiState.update {
            it.copy(
                isReady = false,
                isLoading = true
            )
        }
        lastSyncedPage = -1
    }

    /**
     * Retiene y renderiza la ventana de [current] (desde cero) solo en modo nativo, empezando por la actual.
     * Conserva la generación inicial para rechazar trabajo obsoleto; comprueba cancelación entre renderizados
     * serializados y espera 50 ms antes de cada vecina. La caché tiene un presupuesto de 32 MiB.
     */
    suspend fun preloadPages(current: Int) {
        val generation = synchronized(rendererLock) { rendererGeneration }
        val total = (_uiState.value.mode as? PdfSourceMode.NativeRenderer)?.totalPages ?: return
        synchronized(rendererLock) { bitmapCache.retain(readerPageWindow(current, total)) }
        // Renderiza primero la actual; comprueba cancelación entre renderizados serializados.
        readerPageWindow(current, total).forEach { page ->
            currentCoroutineContext().ensureActive()
            if (page != current) delay(50)
            renderPageBitmap(page, generation)
        }
    }

    /**
     * Obtiene o renderiza [pageIndex] (desde cero) en IO usando la generación actual.
     * Devuelve null si no hay renderizador, el índice no existe o la generación caduca.
     * El bitmap puede seguir en uso tras salir de la caché: el consumidor no debe reciclarlo.
     * Las excepciones de renderizado y la cancelación se propagan.
     */
    suspend fun renderPageBitmap(pageIndex: Int): Bitmap? =
        renderPageBitmap(pageIndex, synchronized(rendererLock) { rendererGeneration })

    /** Serializa caché y renderizador con su cierre; cierra cada página y solo cachea resultados dentro de la ventana actual. */
    private suspend fun renderPageBitmap(pageIndex: Int, generation: Int): Bitmap? = withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        coroutineContext.ensureActive()
        synchronized(rendererLock) {
            coroutineContext.ensureActive()
            if (generation != rendererGeneration) return@synchronized null
            bitmapCache[pageIndex]?.let { return@synchronized it }
            val renderer = pdfRenderer ?: return@synchronized null
            if (pageIndex !in 0 until renderer.pageCount) return@synchronized null
            renderer.openPage(pageIndex).use { page ->
                // Limita incluso una página sobredimensionada a unos 16 MiB (ARGB), con escala máxima 1,5.
                val scale = minOf(1.5, kotlin.math.sqrt(4_000_000.0 / (page.width.toDouble() * page.height)))
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                if (pageIndex in readerPageWindow(_uiState.value.currentPage, renderer.pageCount)) {
                    bitmapCache.put(pageIndex, bitmap)
                }
                bitmap
            }
        }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    /** Invalida trabajos anteriores, suelta la caché sin reciclar bitmaps y cierra renderizador y descriptor bajo el mismo bloqueo; tolera errores de cierre. */
    private fun closeRenderer() = synchronized(rendererLock) {
        rendererGeneration++
        bitmapCache.clear()
        try { pdfRenderer?.close() } catch (_: Exception) {}
        pdfRenderer = null
        try { pfd?.close() } catch (_: Exception) {}
        pfd = null
    }

    override fun onCleared() {
        super.onCleared()
        closeRenderer()
    }

    /** Borra el archivo remoto de forma asíncrona y entrega el Result a [onDone] en viewModelScope; no elimina aquí la copia local. */
    fun deleteBookFile(onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteBookFile(bookId)
            onDone(result)
        }
    }
}
