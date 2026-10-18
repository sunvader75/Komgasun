package com.sunvader.komgasun.ui.reader.epub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sunvader.komgasun.KomgasunApp
import com.sunvader.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import com.sunvader.komgasun.data.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EpubReaderUiState(
    val isLoading: Boolean = true,
    val isReady: Boolean = false,
    val downloadProgress: Float? = null,
    val epubBook: EpubBook? = null,
    val currentChapterIndex: Int = 0,
    val page: Int = 0,
    val pageCount: Int = 0,
    val proportion: Double = 0.0,
    val chapterGeneration: Long = 0,
    val fontSizeSp: Int = 18,
    val isDarkTheme: Boolean = true,
    val showControls: Boolean = false,
    val syncStatus: String? = null,
    val errorMessage: String? = null
)

class EpubReaderViewModel(
    application: Application,
    private val bookId: String,
    private val repository: KomgaRepository = KomgasunApp.instance.repository
) : AndroidViewModel(application) {
    private val readerPreferences = application.getSharedPreferences("epub_reader_settings", 0)
    private val _uiState = MutableStateFlow(
        EpubReaderUiState(isDarkTheme = readerPreferences.getBoolean("dark_theme", true))
    )
    val uiState: StateFlow<EpubReaderUiState> = _uiState.asStateFlow()
    private val preferences = application.getSharedPreferences("epub_locators", 0)
    private var identity: String? = null
    private var loadJob: Job? = null
    private var generation = 0L
    private var lastPosition: Pair<Int, Double>? = null
    private var lastModified = 0L
    private var positionChangedByReader = false
    private var session = 0L
    private var sessionConfig: ServerConfig? = null
    private var syncQueue: EpubProgressionQueue? = null
    private var bookPages: EpubBookPageMap? = null
    private val syncMutex = Mutex()
    private val devicePreferences = application.getSharedPreferences("epub_sync_device", 0)
    private val deviceId: String = devicePreferences.getString("id", null) ?: UUID.randomUUID().toString().also {
        devicePreferences.edit().putString("id", it).apply()
    }

    fun initEpub() {
        val previous = loadJob
        previous?.cancel()
        val token = ++generation
        val sessionToken = ++session
        syncQueue?.close()
        syncQueue = null
        sessionConfig = null
        bookPages = null
        identity = null
        _uiState.update { it.copy(isLoading = true, isReady = false, pageCount = 0, errorMessage = null, syncStatus = null) }
        loadJob = viewModelScope.launch {
            try {
                // The downloader uses a deterministic .tmp sibling: never overlap writers.
                previous?.join()
                ensureActive()
                val config = repository.getActiveConfig() ?: error("Sesión no iniciada")
                val key = EpubReaderStorage.identity(config.serverUrl, config.username, bookId)
                val book = withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().cacheDir, "epub_books")
                    check(dir.isDirectory || dir.mkdirs()) { "No se pudo crear la caché EPUB" }
                    EpubReaderStorage.load(File(dir, "$key.epub")) { destination ->
                        // Never publish bytes under another account's key if the session changes.
                        val staging = File.createTempFile("$key-", ".epub", dir)
                        try {
                            val before = repository.getActiveConfig()
                            check(before?.serverUrl == config.serverUrl && before.username == config.username) { "La sesión ha cambiado" }
                            repository.downloadBookFile(bookId, staging) { value ->
                                if (token == generation) _uiState.update { it.copy(downloadProgress = value.coerceIn(0f, 1f)) }
                            }.getOrThrow()
                            ensureActive()
                            val after = repository.getActiveConfig()
                            check(after?.serverUrl == config.serverUrl && after.username == config.username) { "La sesión ha cambiado" }
                            // Validate before replacing the cache. Cancelled/invalid downloads cannot replace it.
                            EpubParser.parse(staging)
                            ensureActive()
                            check(staging.renameTo(destination)) { "No se pudo guardar la caché EPUB" }
                            destination
                        } finally {
                            staging.delete()
                            File(dir, "${staging.name}.tmp").delete()
                        }
                    }
                }
                ensureActive()
                if (token != generation) return@launch
                val remote = syncMutex.withLock {
                    ensureActive()
                    check(repository.getActiveConfig() == config) { "La sesión ha cambiado" }
                    repository.getEpubProgression(config, bookId)
                }
                ensureActive()
                if (sessionToken != session) return@launch
                val local = preferences.getString(key, null)
                val saved = EpubProgressionSync.resume(book.chapters.map { it.resourcePath.orEmpty() }, local, remote.getOrNull(), book.chapters.map { it.resourceBytes })
                check(repository.getActiveConfig() == config) { "La sesión ha cambiado; vuelve a abrir el libro" }
                identity = key
                sessionConfig = config
                lastPosition = saved
                positionChangedByReader = false
                // Cache the selected remote locator with its original time, never 'now'.
                val remoteValue = remote.getOrNull()
                if (remoteValue != null && (EpubProgressionSync.epoch(local?.split(':', limit = 3)?.getOrNull(2)) ?: 0L) <= (EpubProgressionSync.epoch(remoteValue.modified) ?: 0L)
                    && saved == EpubProgressionSync.remotePosition(book.chapters.map { it.resourcePath.orEmpty() }, remoteValue, book.chapters.map { it.resourceBytes })) {
                    saveLocal(key, "${saved.first}:${saved.second}:${remoteValue.modified}")
                }
                lastModified = maxOf(EpubProgressionSync.epoch(remote.getOrNull()?.modified) ?: 0L,
                    EpubProgressionSync.epoch(local?.split(':', limit = 3)?.getOrNull(2)) ?: 0L)
                if (remote.isSuccess) {
                    syncQueue = EpubProgressionQueue(viewModelScope, send = { value ->
                        syncMutex.withLock {
                            ensureActive()
                            if (sessionToken != session || repository.getActiveConfig() != config) {
                                Result.failure(IllegalStateException("La sesión ha cambiado"))
                            } else {
                                val result = repository.putEpubProgression(config, bookId, value)
                                ensureActive()
                                if (sessionToken == session && repository.getActiveConfig() == config) result
                                else Result.failure(IllegalStateException("La sesión ha cambiado"))
                            }
                        }
                    }, onResult = { result ->
                        if (sessionToken == session && sessionConfig == config) {
                            _uiState.update { it.copy(syncStatus = if (result.isSuccess) null else syncFailure(result.exceptionOrNull())) }
                        }
                    })
                }
                _uiState.update { it.copy(isLoading = false, isReady = true, downloadProgress = null,
                    epubBook = book, currentChapterIndex = saved.first,
                    syncStatus = when {
                        remote.isFailure -> syncFailure(remote.exceptionOrNull())
                        remoteValue != null && EpubProgressionSync.remotePosition(book.chapters.map { it.resourcePath.orEmpty() }, remoteValue, book.chapters.map { it.resourceBytes }) == null -> "Posición remota no válida o capítulo desconocido; se usa la posición local."
                        else -> null
                    },
                    page = 0, pageCount = 0, proportion = saved.second, chapterGeneration = token) }
                repository.serverConfigFlow.collect { active ->
                    if (active != config) {
                        identity = null
                        sessionConfig = null
                        ++session
                        syncQueue?.close()
                        syncQueue = null
                        ++generation
                        _uiState.update { it.copy(isReady = false, isLoading = false,
                            errorMessage = "La sesión ha cambiado; vuelve a abrir el libro") }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (token == generation) _uiState.update { it.copy(isLoading = false, isReady = false,
                    downloadProgress = null, errorMessage = "No se pudo abrir el EPUB: ${e.localizedMessage}. Reintenta la descarga o comprueba que el archivo no esté cifrado/dañado.") }
            }
        }
    }

    fun selectChapter(index: Int, atEnd: Boolean = false) {
        val state = _uiState.value
        if (!state.isReady || index !in (state.epubBook?.chapters?.indices ?: IntRange.EMPTY)) return
        positionChangedByReader = true
        _uiState.update { it.copy(currentChapterIndex = index, page = 0, pageCount = 0,
            proportion = if (atEnd) 1.0 else 0.0, chapterGeneration = ++generation) }
        // The server derives completion from the locator; never send a completed flag.
    }

    fun movePage(direction: Int) {
        val state = _uiState.value
        if (!state.isReady || state.pageCount == 0) return
        val position = EpubPagination.move(EpubPosition(state.currentChapterIndex, state.page), state.pageCount,
            state.epubBook?.chapters?.size ?: 0, direction)
        if (position.chapter != state.currentChapterIndex) selectChapter(position.chapter, direction < 0)
        else if (position.page != state.page) {
            positionChangedByReader = true
            _uiState.update { it.copy(page = position.page, proportion = EpubPagination.proportion(position.page, it.pageCount)) }
        }
    }

    /** Only the app's evaluateJavascript callback calls this; no publication/native bridge exists. */
    fun onPageMeasured(token: Long, page: Int, count: Int) {
        val state = _uiState.value
        if (!state.isReady || token != state.chapterGeneration || count !in 1..100000 || page !in 0 until count) return
        _uiState.update { it.copy(page = page, pageCount = count, proportion = EpubPagination.proportion(page, count)) }
        flushProgress()
        saveCoverSummary()
    }

    internal fun onBookPageMap(pages: EpubBookPageMap?) {
        val state = _uiState.value
        if (!state.isReady) return
        if (pages != null && pages.counts.size != state.epubBook?.chapters?.size) return
        bookPages = pages
        saveCoverSummary()
    }

    private fun saveCoverSummary() {
        val state = _uiState.value
        val config = sessionConfig ?: return
        val chapters = state.epubBook?.chapters?.size ?: return
        if (!state.isReady || chapters < 1 || state.currentChapterIndex !in 0 until chapters) return
        val pages = bookPages
        val fraction = if (pages != null) {
            val chapterCount = pages.counts[state.currentChapterIndex]
            val page = if (state.pageCount == chapterCount) state.page else EpubPagination.page(state.proportion, chapterCount)
            val absolutePage = pages.absolutePage(state.currentChapterIndex, page)
            if (pages.total <= 1) 0.0 else (absolutePage - 1).toDouble() / (pages.total - 1)
        } else EpubBookProgress.fraction(state.currentChapterIndex, state.proportion, chapters)
        repository.preferencesManager.epubCoverProgress.save(config, bookId, EpubCoverProgress(fraction, pages?.total))
    }

    fun flushProgress() {
        val state = _uiState.value
        if (!state.isReady || state.pageCount == 0 || !positionChangedByReader) return
        positionChangedByReader = false
        val value = state.currentChapterIndex to state.proportion
        if (value == lastPosition) return
        lastPosition = value
        lastModified = maxOf(System.currentTimeMillis(), lastModified + 1)
        val modified = EpubProgressionSync.timestamp(lastModified)
        identity?.let { saveLocal(it, "${state.currentChapterIndex}:${state.proportion}:$modified") }
        val book = state.epubBook ?: return
        val chapter = book.chapters.getOrNull(state.currentChapterIndex) ?: return
        val path = chapter.resourcePath ?: return
        val type = book.publication?.mediaTypes?.get(path) ?: return
        val progression = EpubProgressionSync.discreteProgression(state.proportion, chapter.resourceBytes)
        syncQueue?.offer(EpubProgressionDto(modified, EpubDeviceDto(deviceId, "Komgasun"),
            EpubLocatorDto(EpubProgressionSync.href(path), type, EpubLocationsDto(progression))))
    }

    private fun saveLocal(key: String, locator: String) {
        if (preferences.getString(key, null) == locator) return
        val editor = preferences.edit()
        // Bound private locator storage; deterministic eviction needs no secondary index.
        if (!preferences.contains(key)) preferences.all.keys.sorted().take((preferences.all.size - 255).coerceAtLeast(0)).forEach(editor::remove)
        editor.putString(key, locator).apply()
    }

    private fun syncFailure(error: Throwable?): String {
        val code = error?.message?.let { Regex("HTTP (\\d{3})").find(it)?.groupValues?.get(1) }
        val reason = when (code) {
            "404" -> "libro o API de localizadores no disponible"
            "401", "403" -> "acceso denegado"
            "409" -> "hay un progreso más reciente en el servidor"
            "400" -> "localizador rechazado por el servidor"
            else -> "no se pudo confirmar con el servidor"
        }
        return "Progreso solo local: $reason. Vuelve a abrir el libro para reintentar."
    }

    fun onReaderClosed() {
        flushProgress()
        saveCoverSummary()
        ++generation
        ++session
        syncQueue?.close()
        syncQueue = null
        sessionConfig = null
        loadJob?.cancel()
        identity = null
        _uiState.update { it.copy(isReady = false, isLoading = true) }
    }
    fun nextChapter() = movePage(1)
    fun previousChapter() = movePage(-1)
    fun nextPage() = movePage(1)
    fun previousPage() = movePage(-1)
    fun toggleControls() { _uiState.update { it.copy(showControls = !it.showControls) } }
    fun setControlsVisible(visible: Boolean) { _uiState.update { it.copy(showControls = visible) } }
    fun seekPage(targetPage: Int) {
        val state = _uiState.value
        if (!state.isReady || state.pageCount <= 0) return
        val clamped = targetPage.coerceIn(0, state.pageCount - 1)
        if (clamped != state.page) {
            positionChangedByReader = true
            _uiState.update { it.copy(page = clamped, proportion = EpubPagination.proportion(clamped, it.pageCount)) }
        }
    }
    internal fun seekBookPage(page: Int, pages: EpubBookPageMap) {
        val state = _uiState.value
        if (!state.isReady || pages.counts.size != state.epubBook?.chapters?.size) return
        val position = pages.position(page)
        if (position.chapter == state.currentChapterIndex && state.pageCount > 0) {
            seekPage(position.page)
        } else {
            positionChangedByReader = true
            _uiState.update { it.copy(currentChapterIndex = position.chapter, page = 0, pageCount = 0,
                proportion = pages.proportion(position.chapter, position.page), chapterGeneration = ++generation) }
        }
    }

    fun seekBook(fraction: Float) {
        val state = _uiState.value
        val chapters = state.epubBook?.chapters?.size ?: 0
        if (!state.isReady || chapters == 0 || !fraction.isFinite()) return
        val (chapter, proportion) = EpubBookProgress.position(fraction.toDouble(), chapters)
        positionChangedByReader = true
        if (chapter == state.currentChapterIndex && state.pageCount > 0) {
            seekPage(EpubPagination.page(proportion, state.pageCount))
        } else {
            _uiState.update { it.copy(currentChapterIndex = chapter, page = 0, pageCount = 0,
                proportion = proportion, chapterGeneration = ++generation) }
        }
    }

    fun nextChapterDirect() {
        val state = _uiState.value
        val totalChapters = state.epubBook?.chapters?.size ?: 0
        if (state.currentChapterIndex < totalChapters - 1) {
            selectChapter(state.currentChapterIndex + 1)
        }
    }
    fun previousChapterDirect() {
        val state = _uiState.value
        if (state.currentChapterIndex > 0) {
            selectChapter(state.currentChapterIndex - 1)
        }
    }
    fun increaseFontSize() { _uiState.update { it.copy(fontSizeSp = (it.fontSizeSp + 2).coerceAtMost(32), pageCount = 0) } }
    fun decreaseFontSize() { _uiState.update { it.copy(fontSizeSp = (it.fontSizeSp - 2).coerceAtLeast(12), pageCount = 0) } }
    fun toggleTheme() {
        val isDarkTheme = !_uiState.value.isDarkTheme
        readerPreferences.edit().putBoolean("dark_theme", isDarkTheme).apply()
        _uiState.update { it.copy(isDarkTheme = isDarkTheme) }
    }
    fun deleteBookFile(onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onDone(repository.deleteBookFile(bookId)) }
    }
}
