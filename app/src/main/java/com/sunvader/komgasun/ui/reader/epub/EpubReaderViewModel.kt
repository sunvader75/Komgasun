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
    val errorMessage: String? = null
)

class EpubReaderViewModel(
    application: Application,
    private val bookId: String,
    private val repository: KomgaRepository = KomgasunApp.instance.repository
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(EpubReaderUiState())
    val uiState: StateFlow<EpubReaderUiState> = _uiState.asStateFlow()
    private val preferences = application.getSharedPreferences("epub_locators", 0)
    private var identity: String? = null
    private var loadJob: Job? = null
    private var generation = 0L
    private var lastSync: Pair<Int, Boolean>? = null

    fun initEpub() {
        val previous = loadJob
        previous?.cancel()
        val token = ++generation
        identity = null
        _uiState.update { it.copy(isLoading = true, isReady = false, pageCount = 0, errorMessage = null) }
        loadJob = viewModelScope.launch {
            try {
                // The downloader uses a deterministic .tmp sibling: never overlap writers.
                previous?.join()
                ensureActive()
                val config = repository.getActiveConfig() ?: error("Sesión no iniciada")
                val key = EpubReaderStorage.identity(config.serverUrl, config.username, bookId)
                val progress = repository.getBook(bookId).getOrThrow().readProgress
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
                val serverChapter = if (progress?.completed == true) 0 else ((progress?.page ?: 1) - 1).coerceIn(0, book.chapters.lastIndex)
                val saved = EpubReaderStorage.resumeLocator(preferences.getString(key, null), book.chapters.size,
                    progress?.page ?: 0, progress?.completed == true)
                val currentConfig = repository.getActiveConfig()
                check(currentConfig?.serverUrl == config.serverUrl && currentConfig.username == config.username) { "La sesión ha cambiado; vuelve a abrir el libro" }
                identity = key
                lastSync = null
                _uiState.update { it.copy(isLoading = false, isReady = true, downloadProgress = null,
                    epubBook = book, currentChapterIndex = saved?.first ?: serverChapter,
                    page = 0, pageCount = 0, proportion = saved?.second ?: 0.0, chapterGeneration = token) }
                repository.serverConfigFlow.collect { active ->
                    if (active?.serverUrl != config.serverUrl || active.username != config.username) {
                        identity = null
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
        _uiState.update { it.copy(currentChapterIndex = index, page = 0, pageCount = 0,
            proportion = if (atEnd) 1.0 else 0.0, chapterGeneration = ++generation) }
        // Do not mark completed until the new chapter's last page is actually measured.
    }

    fun movePage(direction: Int) {
        val state = _uiState.value
        if (!state.isReady || state.pageCount == 0) return
        val position = EpubPagination.move(EpubPosition(state.currentChapterIndex, state.page), state.pageCount,
            state.epubBook?.chapters?.size ?: 0, direction)
        if (position.chapter != state.currentChapterIndex) selectChapter(position.chapter, direction < 0)
        else if (position.page != state.page) {
            _uiState.update { it.copy(page = position.page, proportion = EpubPagination.proportion(position.page, it.pageCount)) }
        }
    }

    /** Only the app's evaluateJavascript callback calls this; no publication/native bridge exists. */
    fun onPageMeasured(token: Long, page: Int, count: Int) {
        val state = _uiState.value
        if (!state.isReady || token != state.chapterGeneration || count !in 1..100000 || page !in 0 until count) return
        _uiState.update { it.copy(page = page, pageCount = count, proportion = EpubPagination.proportion(page, count)) }
        flushProgress()
    }

    fun flushProgress() {
        val state = _uiState.value
        if (!state.isReady || state.pageCount == 0) return
        val completed = EpubPagination.completed(state.currentChapterIndex, state.epubBook?.chapters?.size ?: 0, state.page, state.pageCount)
        val value = state.currentChapterIndex + 1 to completed
        if (value != lastSync) {
            repository.syncReadProgressAsync(bookId, value.first, value.second)
            lastSync = value
        }
        identity?.let {
            val locator = "${state.currentChapterIndex}:${state.proportion}"
            if (preferences.getString(it, null) != locator) {
                val editor = preferences.edit()
                // Bound private locator storage; deterministic eviction needs no secondary index.
                if (!preferences.contains(it)) preferences.all.keys.sorted().take((preferences.all.size - 255).coerceAtLeast(0)).forEach(editor::remove)
                editor.putString(it, locator).apply()
            }
        }
    }

    fun onReaderClosed() {
        flushProgress()
        ++generation
        loadJob?.cancel()
        identity = null
        _uiState.update { it.copy(isReady = false, isLoading = true) }
    }
    fun nextChapter() = movePage(1)
    fun previousChapter() = movePage(-1)
    fun increaseFontSize() { _uiState.update { it.copy(fontSizeSp = (it.fontSizeSp + 2).coerceAtMost(32), pageCount = 0) } }
    fun decreaseFontSize() { _uiState.update { it.copy(fontSizeSp = (it.fontSizeSp - 2).coerceAtLeast(12), pageCount = 0) } }
    fun toggleTheme() { _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) } }
    fun deleteBookFile(onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onDone(repository.deleteBookFile(bookId)) }
    }
}
