package com.example.komgasun.data.remote

import com.example.komgasun.data.model.BookDto
import com.example.komgasun.data.model.BookPageDto
import com.example.komgasun.data.model.LibraryDto
import com.example.komgasun.data.model.PageResponseDto
import com.example.komgasun.data.model.SeriesDto
import com.example.komgasun.data.model.ServerConfig
import com.example.komgasun.data.model.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class KomgaApiClient(
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun buildRequest(url: String, config: ServerConfig): Request {
        val credential = Credentials.basic(config.username, config.password)
        return Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .build()
    }

    private fun buildJsonPatchRequest(url: String, config: ServerConfig, jsonBody: String): Request {
        val credential = Credentials.basic(config.username, config.password)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)
        return Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .patch(body)
            .build()
    }

    suspend fun authenticate(config: ServerConfig): Result<UserDto> = withContext(Dispatchers.IO) {
        val baseUrl = config.serverUrl.trim().removeSuffix("/")

        // Komga moved users API from v1 to v2 (/api/v2/users/me).
        // We try v2 first, then fall back to v1, and finally test /api/v1/libraries as safeguard.
        val endpointsToTry = listOf(
            "$baseUrl/api/v2/users/me",
            "$baseUrl/api/v1/users/me"
        )

        for (endpoint in endpointsToTry) {
            val request = buildRequest(endpoint, config)
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        200 -> {
                            val body = response.body?.string() ?: return@withContext Result.failure(
                                IOException("Respuesta vacía del servidor")
                            )
                            val user = json.decodeFromString<UserDto>(body)
                            return@withContext Result.success(user)
                        }
                        401 -> return@withContext Result.failure(IOException("Credenciales inválidas (401)"))
                        403 -> return@withContext Result.failure(IOException("Acceso denegado (403)"))
                        404 -> {
                            // Endpoint not found on this version, try next fallback
                        }
                        else -> return@withContext Result.failure(IOException("Error del servidor: HTTP ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

        // Final fallback: check if /api/v1/libraries responds with 200 (proves valid auth & Komga server)
        val librariesFallbackUrl = "$baseUrl/api/v1/libraries"
        try {
            okHttpClient.newCall(buildRequest(librariesFallbackUrl, config)).execute().use { response ->
                when (response.code) {
                    200 -> Result.success(UserDto(id = "me", email = config.username))
                    401 -> Result.failure(IOException("Credenciales inválidas (401)"))
                    403 -> Result.failure(IOException("Acceso denegado (403)"))
                    404 -> Result.failure(IOException("Servidor o ruta no encontrada (404). Verificá la URL ingresada."))
                    else -> Result.failure(IOException("Error del servidor: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLibraries(config: ServerConfig): Result<List<LibraryDto>> = withContext(Dispatchers.IO) {
        val baseUrl = config.serverUrl.trim().removeSuffix("/")
        val request = buildRequest("$baseUrl/api/v1/libraries", config)

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body?.string().orEmpty()
                val libraries = json.decodeFromString<List<LibraryDto>>(body)
                Result.success(libraries)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeries(config: ServerConfig, libraryId: String): Result<List<SeriesDto>> = withContext(Dispatchers.IO) {
        val baseUrl = config.serverUrl.trim().removeSuffix("/")
        val url = "$baseUrl/api/v1/series?library_id=$libraryId&size=500"
        val request = buildRequest(url, config)

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body?.string().orEmpty()
                val page = json.decodeFromString<PageResponseDto<SeriesDto>>(body)
                Result.success(page.content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBooks(config: ServerConfig, seriesId: String): Result<List<BookDto>> = withContext(Dispatchers.IO) {
        val baseUrl = config.serverUrl.trim().removeSuffix("/")
        val url = "$baseUrl/api/v1/series/$seriesId/books?size=500"
        val request = buildRequest(url, config)

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body?.string().orEmpty()
                val page = json.decodeFromString<PageResponseDto<BookDto>>(body)
                Result.success(page.content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBook(config: ServerConfig, bookId: String): Result<BookDto> = withContext(Dispatchers.IO) {
        val baseUrl = config.serverUrl.trim().removeSuffix("/")
        val url = "$baseUrl/api/v1/books/$bookId"
        val request = buildRequest(url, config)

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body?.string().orEmpty()
                val book = json.decodeFromString<BookDto>(body)
                Result.success(book)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    private data class ReadProgressUpdatePayload(
        val page: Int,
        val completed: Boolean
    )

    suspend fun updateReadProgress(
        config: ServerConfig,
        bookId: String,
        page: Int,
        completed: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = config.serverUrl.trim().removeSuffix("/")
        val url = "$baseUrl/api/v1/books/$bookId/read-progress"
        val payload = ReadProgressUpdatePayload(page = page, completed = completed)
        val payloadJson = json.encodeToString(payload)
        val request = buildJsonPatchRequest(url, config, payloadJson)

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    android.util.Log.e("KomgaApiClient", "PATCH read-progress HTTP ${response.code}: $errorBody")
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message} - $errorBody"))
                }
                android.util.Log.d("KomgaApiClient", "PATCH read-progress OK: bookId=$bookId, page=$page, completed=$completed (code=${response.code})")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("KomgaApiClient", "PATCH read-progress error: bookId=$bookId", e)
            Result.failure(e)
        }
    }

    suspend fun getBookPages(config: ServerConfig, bookId: String): Result<List<BookPageDto>> = withContext(Dispatchers.IO) {
        val baseUrl = config.serverUrl.trim().removeSuffix("/")
        val url = "$baseUrl/api/v1/books/$bookId/pages"
        val request = buildRequest(url, config)

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body?.string().orEmpty()
                val pages = json.decodeFromString<List<BookPageDto>>(body)
                Result.success(pages)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadBookFile(
        config: ServerConfig,
        bookId: String,
        destinationFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val baseUrl = config.serverUrl.trim().removeSuffix("/")
        val url = "$baseUrl/api/v1/books/$bookId/file"
        val request = buildRequest(url, config)

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body ?: return@withContext Result.failure(IOException("Empty body"))
                val totalLength = body.contentLength()

                destinationFile.parentFile?.mkdirs()
                val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")

                body.byteStream().use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (totalLength > 0) {
                                onProgress(totalBytesRead.toFloat() / totalLength.toFloat())
                            }
                        }
                        outputStream.flush()
                    }
                }

                if (destinationFile.exists()) destinationFile.delete()
                if (!tempFile.renameTo(destinationFile)) {
                    tempFile.copyTo(destinationFile, overwrite = true)
                    tempFile.delete()
                }

                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun getSeriesThumbnailUrl(serverUrl: String, seriesId: String): String =
            "${serverUrl.trim().removeSuffix("/")}/api/v1/series/$seriesId/thumbnail"

        fun getBookThumbnailUrl(serverUrl: String, bookId: String): String =
            "${serverUrl.trim().removeSuffix("/")}/api/v1/books/$bookId/thumbnail"

        fun getPageImageUrl(serverUrl: String, bookId: String, pageNumber: Int): String =
            "${serverUrl.trim().removeSuffix("/")}/api/v1/books/$bookId/pages/$pageNumber"
    }
}
