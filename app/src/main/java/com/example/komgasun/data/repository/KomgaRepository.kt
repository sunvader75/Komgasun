package com.example.komgasun.data.repository

import com.example.komgasun.data.local.PreferencesManager
import com.example.komgasun.data.model.BookDto
import com.example.komgasun.data.model.BookPageDto
import com.example.komgasun.data.model.LibraryDto
import com.example.komgasun.data.model.SeriesDto
import com.example.komgasun.data.model.ServerConfig
import com.example.komgasun.data.model.UserDto
import com.example.komgasun.data.remote.KomgaApiClient
import com.example.komgasun.data.model.ReadProgressDto
import com.example.komgasun.data.model.ServerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class KomgaRepository(
    val preferencesManager: PreferencesManager,
    val apiClient: KomgaApiClient = KomgaApiClient()
) {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _recentProgress = MutableStateFlow<Map<String, ReadProgressDto>>(emptyMap())
    val recentProgress: StateFlow<Map<String, ReadProgressDto>> = _recentProgress.asStateFlow()

    val serverConfigFlow: Flow<ServerConfig?> = preferencesManager.serverConfigFlow
    val activeServerTypeFlow: Flow<ServerType> = preferencesManager.activeServerTypeFlow
    val localUrlFlow: Flow<String> = preferencesManager.localUrlFlow
    val remoteUrlFlow: Flow<String> = preferencesManager.remoteUrlFlow

    suspend fun getActiveConfig(): ServerConfig? = serverConfigFlow.firstOrNull()

    suspend fun login(
        config: ServerConfig,
        type: ServerType = ServerType.LOCAL,
        otherUrl: String? = null
    ): Result<UserDto> {
        val result = apiClient.authenticate(config)
        if (result.isSuccess) {
            preferencesManager.saveCredentials(config, type, otherUrl)
        }
        return result
    }

    suspend fun switchServer(type: ServerType): String =
        preferencesManager.switchActiveServer(type)

    suspend fun saveServerUrls(localUrl: String, remoteUrl: String, activeType: ServerType) =
        preferencesManager.saveServerUrls(localUrl, remoteUrl, activeType)

    suspend fun logout() {
        preferencesManager.clearCredentials()
    }

    suspend fun getLibraries(): Result<List<LibraryDto>> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.getLibraries(config)
    }

    suspend fun getSeries(libraryId: String): Result<List<SeriesDto>> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.getSeries(config, libraryId)
    }

    suspend fun getBooks(seriesId: String): Result<List<BookDto>> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        val result = apiClient.getBooks(config, seriesId)
        return result.map { list ->
            val overrides = _recentProgress.value
            if (overrides.isEmpty()) list else {
                list.map { book ->
                    val override = overrides[book.id]
                    if (override != null) book.copy(readProgress = override) else book
                }
            }
        }
    }

    suspend fun getBook(bookId: String): Result<BookDto> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        val result = apiClient.getBook(config, bookId)
        return result.map { book ->
            val override = _recentProgress.value[bookId]
            if (override != null) book.copy(readProgress = override) else book
        }
    }

    fun syncReadProgressAsync(bookId: String, page: Int, completed: Boolean) {
        val updated = ReadProgressDto(page = page, completed = completed)
        _recentProgress.update { it + (bookId to updated) }
        repoScope.launch {
            val result = updateReadProgress(bookId, page, completed)
            if (result.isFailure) {
                android.util.Log.e("KomgaRepository", "Failed to update read progress on server for book $bookId", result.exceptionOrNull())
            }
        }
    }

    suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean): Result<Unit> {
        val updated = ReadProgressDto(page = page, completed = completed)
        _recentProgress.update { it + (bookId to updated) }
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.updateReadProgress(config, bookId, page, completed)
    }

    suspend fun getBookPages(bookId: String): Result<List<BookPageDto>> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.getBookPages(config, bookId)
    }

    suspend fun downloadBookFile(
        bookId: String,
        destinationFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.downloadBookFile(config, bookId, destinationFile, onProgress)
    }

    suspend fun deleteBookFile(bookId: String): Result<Unit> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.deleteBookFile(config, bookId)
    }

    suspend fun deleteSeriesFile(seriesId: String): Result<Unit> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.deleteSeriesFile(config, seriesId)
    }

    suspend fun emptyTrash(libraryId: String): Result<Unit> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.emptyTrash(config, libraryId)
    }

    suspend fun getSeriesThumbnailUrl(seriesId: String): String {
        val config = getActiveConfig() ?: return ""
        return KomgaApiClient.getSeriesThumbnailUrl(config.serverUrl, seriesId)
    }

    suspend fun getBookThumbnailUrl(bookId: String): String {
        val config = getActiveConfig() ?: return ""
        return KomgaApiClient.getBookThumbnailUrl(config.serverUrl, bookId)
    }

    suspend fun getPageImageUrl(bookId: String, pageNumber: Int): String {
        val config = getActiveConfig() ?: return ""
        return KomgaApiClient.getPageImageUrl(config.serverUrl, bookId, pageNumber)
    }
}
