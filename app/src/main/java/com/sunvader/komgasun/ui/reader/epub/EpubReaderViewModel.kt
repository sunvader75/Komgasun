package com.sunvader.komgasun.ui.reader.epub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sunvader.komgasun.KomgasunApp
import com.sunvader.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var lastSyncedChapter: Int = -1
    private var initialChapterIndex: Int = 0

    fun initEpub() {
        _uiState.update { it.copy(isLoading = true, isReady = false, downloadProgress = null, errorMessage = null) }
        viewModelScope.launch {
            val bookResult = repository.getBook(bookId)
            val readProgress = bookResult.getOrNull()?.readProgress
            val isCompleted = readProgress?.completed == true
            val savedPage = readProgress?.page ?: 1
            initialChapterIndex = if (isCompleted) 0 else (savedPage - 1).coerceAtLeast(0)
            lastSyncedChapter = initialChapterIndex + 1
            loadEpub()
        }
    }

    private fun getLocalEpubFile(): File {
        val dir = File(getApplication<Application>().cacheDir, "epub_books")
        dir.mkdirs()
        return File(dir, "$bookId.epub")
    }

    fun loadEpub() {
        val file = getLocalEpubFile()
        if (file.exists() && file.length() > 0) {
            parseEpubFile(file)
        } else {
            downloadEpub()
        }
    }

    private fun downloadEpub() {
        _uiState.update { it.copy(isLoading = true, downloadProgress = 0f, errorMessage = null) }
        viewModelScope.launch {
            val destFile = getLocalEpubFile()
            val result = repository.downloadBookFile(
                bookId = bookId,
                destinationFile = destFile,
                onProgress = { progress ->
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
            )

            result.onSuccess { downloadedFile ->
                _uiState.update { it.copy(downloadProgress = null) }
                parseEpubFile(downloadedFile)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        downloadProgress = null,
                        errorMessage = "Error al descargar EPUB: ${error.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun parseEpubFile(file: File) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val book = EpubParser.parse(file)
                check(book.chapters.isNotEmpty()) { "No se encontraron capítulos legibles en este EPUB" }
                val targetIndex = initialChapterIndex.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
                lastSyncedChapter = targetIndex + 1
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isReady = true,
                        epubBook = book,
                        currentChapterIndex = targetIndex
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isReady = false,
                        errorMessage = "Error al analizar el archivo EPUB: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun selectChapter(index: Int) {
        val count = _uiState.value.epubBook?.chapters?.size ?: 0
        if (index in 0 until count) {
            _uiState.update { it.copy(currentChapterIndex = index) }
            syncProgress(index, count)
        }
    }

    private fun syncProgress(chapterIndex: Int, totalChapters: Int) {
        val chapterNumber = chapterIndex + 1
        if (chapterNumber == lastSyncedChapter) return
        lastSyncedChapter = chapterNumber

        val isCompleted = chapterNumber >= totalChapters && totalChapters > 0
        repository.syncReadProgressAsync(bookId = bookId, page = chapterNumber, completed = isCompleted)
    }

    fun flushProgress() {
        val total = _uiState.value.epubBook?.chapters?.size ?: 0
        val chapterNumber = _uiState.value.currentChapterIndex + 1
        if (total > 0 && chapterNumber in 1..total) {
            val isCompleted = chapterNumber >= total
            repository.syncReadProgressAsync(bookId = bookId, page = chapterNumber, completed = isCompleted)
        }
    }

    fun onReaderClosed() {
        flushProgress()
        _uiState.update {
            it.copy(
                isReady = false,
                isLoading = true
            )
        }
        lastSyncedChapter = -1
    }

    fun nextChapter() {
        selectChapter(_uiState.value.currentChapterIndex + 1)
    }

    fun previousChapter() {
        selectChapter(_uiState.value.currentChapterIndex - 1)
    }

    fun increaseFontSize() {
        _uiState.update { it.copy(fontSizeSp = (it.fontSizeSp + 2).coerceAtMost(32)) }
    }

    fun decreaseFontSize() {
        _uiState.update { it.copy(fontSizeSp = (it.fontSizeSp - 2).coerceAtLeast(12)) }
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun deleteBookFile(onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteBookFile(bookId)
            onDone(result)
        }
    }
}
