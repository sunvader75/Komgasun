package com.example.komgasun.data.repository

import com.example.komgasun.data.local.PreferencesManager
import com.example.komgasun.data.model.BookDto
import com.example.komgasun.data.model.BookPageDto
import com.example.komgasun.data.model.LibraryDto
import com.example.komgasun.data.model.SeriesDto
import com.example.komgasun.data.model.ServerConfig
import com.example.komgasun.data.model.UserDto
import com.example.komgasun.data.remote.KomgaApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import java.io.IOException

class KomgaRepository(
    val preferencesManager: PreferencesManager,
    val apiClient: KomgaApiClient = KomgaApiClient()
) {

    val serverConfigFlow: Flow<ServerConfig?> = preferencesManager.serverConfigFlow

    suspend fun getActiveConfig(): ServerConfig? = serverConfigFlow.firstOrNull()

    suspend fun login(config: ServerConfig): Result<UserDto> {
        val result = apiClient.authenticate(config)
        if (result.isSuccess) {
            preferencesManager.saveCredentials(config)
        }
        return result
    }

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
        return apiClient.getBooks(config, seriesId)
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
