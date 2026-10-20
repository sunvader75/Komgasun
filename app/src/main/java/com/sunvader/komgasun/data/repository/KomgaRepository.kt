package com.sunvader.komgasun.data.repository

import com.sunvader.komgasun.data.local.PreferencesManager
import com.sunvader.komgasun.data.model.BookDto
import com.sunvader.komgasun.data.model.BookPageDto
import com.sunvader.komgasun.data.model.BookType
import com.sunvader.komgasun.data.model.EpubCoverProgress
import com.sunvader.komgasun.data.model.LibraryDto
import com.sunvader.komgasun.data.model.ReadProgressDto
import com.sunvader.komgasun.data.model.SeriesDto
import com.sunvader.komgasun.data.model.ServerConfig
import com.sunvader.komgasun.data.model.ServerType
import com.sunvader.komgasun.data.model.UserDto
import com.sunvader.komgasun.data.model.detectBookType
import com.sunvader.komgasun.data.remote.KomgaApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class KomgaRepository(
    val preferencesManager: PreferencesManager,
    val apiClient: KomgaApiClient = KomgaApiClient()
) {

    private val repoScope = CoroutineScope(SupervisorJob() + IO)
    private val _recentProgress = MutableStateFlow<Map<String, ReadProgressDto>>(emptyMap())
    val recentProgress: StateFlow<Map<String, ReadProgressDto>> = _recentProgress.asStateFlow()

    val serverConfigFlow: Flow<ServerConfig?> = preferencesManager.serverConfigFlow
    val activeServerTypeFlow: Flow<ServerType> = preferencesManager.activeServerTypeFlow
    val localUrlFlow: Flow<String> = preferencesManager.localUrlFlow
    val remoteUrlFlow: Flow<String> = preferencesManager.remoteUrlFlow

    /** Devuelve la primera configuración de sesión emitida, o `null` si no hay sesión válida. */
    suspend fun getActiveConfig(): ServerConfig? = serverConfigFlow.firstOrNull()

    /**
     * Autentica y, solo si hay éxito, guarda credenciales y URL del tipo indicado.
     * [otherUrl] actualiza opcionalmente el otro tipo; devuelve el Result de autenticación.
     * Los errores de cifrado o persistencia posteriores a la autenticación se propagan.
     */
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

    /** Cambia el tipo guardado sin autenticar; devuelve su URL, vacía si falta, sin vaciar la URL activa. */
    suspend fun switchServer(type: ServerType): String =
        preferencesManager.switchActiveServer(type)

    /** Persiste URL normalizadas no vacías y el tipo activo; no comprueba conectividad ni credenciales. */
    suspend fun saveServerUrls(localUrl: String, remoteUrl: String, activeType: ServerType) =
        preferencesManager.saveServerUrls(localUrl, remoteUrl, activeType)

    /** Elimina las contraseñas y desactiva la sesión; conserva las URL y los ajustes del usuario. */
    suspend fun logout() {
        preferencesManager.clearCredentials()
    }

    /** Devuelve bibliotecas o el fallo remoto; sin configuración devuelve fallo «Sesión no iniciada». */
    suspend fun getLibraries(): Result<List<LibraryDto>> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.getLibraries(config)
    }

    /** Devuelve hasta 500 series de la biblioteca; sin sesión devuelve fallo, igual que los errores remotos. */
    suspend fun getSeries(libraryId: String): Result<List<SeriesDto>> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.getSeries(config, libraryId)
    }

    /**
     * Obtiene hasta 500 libros, aplica las sustituciones recientes de readProgress y añade resúmenes EPUB.
     * Sin sesión devuelve fallo; conserva fallos de la API, pero los errores de enriquecimiento se propagan.
     */
    suspend fun getBooks(seriesId: String): Result<List<BookDto>> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        val result = apiClient.getBooks(config, seriesId)
        return result.map { list ->
            val overrides = _recentProgress.value
            val books = list.map { book ->
                val override = overrides[book.id]
                if (override != null) book.copy(readProgress = override) else book
            }
            enrichEpubCovers(config, books)
        }
    }

    /**
     * Obtiene todos los libros en curso y enriquece sus portadas EPUB, sin aplicar sustituciones recientes.
     * Sin sesión o ante error de API devuelve fallo; los errores de enriquecimiento se propagan.
     */
    suspend fun getInProgressBooks(): Result<List<BookDto>> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.getInProgressBooks(config).map { enrichEpubCovers(config, it) }
    }

    /**
     * Obtiene el libro con el progreso reciente y el resumen EPUB disponibles.
     * Sin sesión o ante error de API devuelve fallo; los errores de enriquecimiento se propagan.
     */
    suspend fun getBook(bookId: String): Result<BookDto> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        val result = apiClient.getBook(config, bookId)
        return result.map { book ->
            val override = _recentProgress.value[bookId]
            val updated = if (override != null) book.copy(readProgress = override) else book
            enrichEpubCovers(config, listOf(updated)).single()
        }
    }

    /**
     * Conserva el orden y añade resúmenes solo a EPUB, con un máximo de cuatro operaciones simultáneas.
     * Prefiere el resumen local; consulta el remoto solo si falta aquel y hay lectura en curso.
     * Un fallo remoto equivale a resumen ausente; no convierte posiciones en páginas ni persiste datos.
     */
    private suspend fun enrichEpubCovers(config: ServerConfig, books: List<BookDto>): List<BookDto> = coroutineScope {
        val requests = Semaphore(4)
        books.map { book -> async {
            if (book.detectBookType() != BookType.EPUB) book else requests.withPermit {
                val local = withContext(IO) { preferencesManager.epubCoverProgress.read(config, book.id) }
                val remote = if (local == null && book.readProgress?.completed == false && book.readProgress.page > 0)
                    EpubCoverProgress.fromRemote(apiClient.getEpubProgression(config, book.id).getOrNull()) else null
                book.copy(epubProgress = local ?: remote)
            }
        } }.awaitAll()
    }

    /** Obtiene posiciones del servidor con la configuración capturada, sin resolver la sesión activa. */
    suspend fun getEpubPositions(config: ServerConfig, bookId: String) =
        apiClient.getEpubPositions(config, bookId)

    /** Obtiene el localizador con la configuración capturada; éxito nulo indica HTTP 204, no HTTP 404. */
    suspend fun getEpubProgression(config: ServerConfig, bookId: String) =
        apiClient.getEpubProgression(config, bookId)

    /** Guarda el localizador en la cuenta y servidor capturados, aunque cambie la sesión; solo 204 es éxito. */
    suspend fun putEpubProgression(config: ServerConfig, bookId: String, progression: com.sunvader.komgasun.data.model.EpubProgressionDto) =
        apiClient.putEpubProgression(config, bookId, progression)

    /**
     * Publica inmediatamente el progreso local y lanza su envío en el ámbito de E/S del repositorio.
     * [page] conserva la numeración de Komga; el fallo se registra sin revertir el estado ni devolverse.
     * La configuración se resuelve al enviar, no al encolar.
     */
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

    /**
     * Publica el progreso reciente antes de buscar sesión y envía la página sin conversión ni validación.
     * Devuelve fallo sin sesión o ante error remoto; no revierte la actualización local si falla.
     */
    suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean): Result<Unit> {
        val updated = ReadProgressDto(page = page, completed = completed)
        _recentProgress.update { it + (bookId to updated) }
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.updateReadProgress(config, bookId, page, completed)
    }

    /**
     * Marca leído o elimina el progreso remoto al marcar sin leer; sin sesión devuelve fallo.
     * Solo tras éxito actualiza la copia reciente: usa pagesCount al completar y cero al reiniciar.
     * Al reiniciar EPUB borra además el resumen local en E/S; sus errores se propagan.
     */
    suspend fun setBookReadStatus(book: BookDto, completed: Boolean): Result<Unit> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        val result = apiClient.setBookReadStatus(config, book.id, completed)
        if (result.isSuccess) {
            if (!completed && book.detectBookType() == BookType.EPUB) {
                withContext(IO) { preferencesManager.epubCoverProgress.clear(config, book.id) }
            }
            val progress = ReadProgressDto(
                page = if (completed) book.media.pagesCount else 0,
                completed = completed
            )
            _recentProgress.update { it + (book.id to progress) }
        }
        return result
    }

    /** Devuelve las páginas del servidor o un fallo remoto; sin sesión devuelve fallo sin consultar la API. */
    suspend fun getBookPages(bookId: String): Result<List<BookPageDto>> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.getBookPages(config, bookId)
    }

    /**
     * Descarga el libro y sustituye el destino mediante un temporal; sin sesión devuelve fallo.
     * Devuelve el archivo o el fallo de la API; puede quedar un temporal si falla.
     * [onProgress] recibe la proporción de bytes en E/S cuando se conoce la longitud.
     */
    suspend fun downloadBookFile(
        bookId: String,
        destinationFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.downloadBookFile(config, bookId, destinationFile, onProgress)
    }

    /** Solicita eliminar el archivo remoto del libro; devuelve fallo sin sesión o el Result de la API. */
    suspend fun deleteBookFile(bookId: String): Result<Unit> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.deleteBookFile(config, bookId)
    }

    /** Solicita eliminar los archivos remotos de la serie; devuelve fallo sin sesión o el Result de la API. */
    suspend fun deleteSeriesFile(seriesId: String): Result<Unit> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.deleteSeriesFile(config, seriesId)
    }

    /** Solicita vaciar la papelera remota de la biblioteca; devuelve fallo sin sesión o el Result de la API. */
    suspend fun emptyTrash(libraryId: String): Result<Unit> {
        val config = getActiveConfig() ?: return Result.failure(IOException("Sesión no iniciada"))
        return apiClient.emptyTrash(config, libraryId)
    }

//    /** Devuelve la URL de miniatura sin autenticar, o una cadena vacía si no hay sesión. */
//    suspend fun getSeriesThumbnailUrl(seriesId: String): String {
//        val config = getActiveConfig() ?: return ""
//        return KomgaApiClient.getSeriesThumbnailUrl(config.serverUrl, seriesId)
//    }
//
//    /** Devuelve la URL de portada sin autenticar, o una cadena vacía si no hay sesión. */
//    suspend fun getBookThumbnailUrl(bookId: String): String {
//        val config = getActiveConfig() ?: return ""
//        return KomgaApiClient.getBookThumbnailUrl(config.serverUrl, bookId)
//    }
//
//    /** Devuelve la URL de imagen con la numeración del servidor sin validarla, o una cadena vacía sin sesión. */
//    suspend fun getPageImageUrl(bookId: String, pageNumber: Int): String {
//        val config = getActiveConfig() ?: return ""
//        return KomgaApiClient.getPageImageUrl(config.serverUrl, bookId, pageNumber)
//    }
}
