package com.sunvader.komgasun.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sunvader.komgasun.data.model.ItemFilter
import com.sunvader.komgasun.data.model.SeriesSortOrder
import com.sunvader.komgasun.data.model.ServerConfig
import com.sunvader.komgasun.data.model.ServerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.security.GeneralSecurityException
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "komgasun_prefs")

class PreferencesManager(
    private val context: Context,
    private val store: DataStore<Preferences> = context.dataStore,
    private val credentialCipher: CredentialCipher = CredentialCipher()
) {
    val epubCoverProgress = EpubCoverProgressStore(context.getSharedPreferences("epub_cover_progress", Context.MODE_PRIVATE))

    companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_LOCAL_SERVER_URL = stringPreferencesKey("local_server_url")
        val KEY_REMOTE_SERVER_URL = stringPreferencesKey("remote_server_url")
        val KEY_ACTIVE_SERVER_TYPE = stringPreferencesKey("active_server_type")
        val KEY_USERNAME = stringPreferencesKey("username")
        // Legacy plaintext key: read only for migration, never written by the app.
        val KEY_PASSWORD = stringPreferencesKey("password")
        private val KEY_ENCRYPTED_PASSWORD = stringPreferencesKey("encrypted_password_v1")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_LIBRARIES_FILTER = stringPreferencesKey("libraries_filter")
        val KEY_SERIES_FILTER = stringPreferencesKey("series_filter")
        val KEY_BOOKS_FILTER = stringPreferencesKey("books_filter")
        val KEY_SERIES_SORT = stringPreferencesKey("series_sort")
    }

    /** Recorta espacios y una barra final; añade HTTP si falta el esquema, sin validar la URL. */
    private fun normalizeUrl(url: String): String {
        var clean = url.trim().removeSuffix("/")
        if (clean.isNotBlank() && !clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            clean = "http://$clean"
        }
        return clean
    }

    /** Devuelve la contraseña descifrada, o `null` si falta, tiene un formato inválido o falla la seguridad. */
    private fun decryptPassword(value: String?): String? {
        if (value == null) return null
        return try {
            credentialCipher.decrypt(value)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    val serverConfigFlow: Flow<ServerConfig?> = store.data
        .onStart {
            store.edit { preferences ->
                val legacy = preferences[KEY_PASSWORD]
                if (legacy != null && preferences[KEY_ENCRYPTED_PASSWORD] == null) {
                    try {
                        preferences[KEY_ENCRYPTED_PASSWORD] = credentialCipher.encrypt(legacy)
                    } catch (_: GeneralSecurityException) {
                        preferences[KEY_IS_LOGGED_IN] = false
                    }
                }
                preferences.remove(KEY_PASSWORD)
                if (decryptPassword(preferences[KEY_ENCRYPTED_PASSWORD]) == null) {
                    preferences.remove(KEY_ENCRYPTED_PASSWORD)
                    preferences[KEY_IS_LOGGED_IN] = false
                }
            }
        }
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val url = preferences[KEY_SERVER_URL]
            val username = preferences[KEY_USERNAME]
            val password = decryptPassword(preferences[KEY_ENCRYPTED_PASSWORD])
            val isLoggedIn = preferences[KEY_IS_LOGGED_IN] ?: false

            if (isLoggedIn && password != null && !url.isNullOrBlank() && !username.isNullOrBlank()) {
                ServerConfig(
                    serverUrl = url,
                    username = username,
                    password = password
                )
            } else {
                null
            }
        }

    val savedUrlFlow: Flow<String> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SERVER_URL] ?: "" }

    val localUrlFlow: Flow<String> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LOCAL_SERVER_URL] ?: it[KEY_SERVER_URL] ?: "" }

    val remoteUrlFlow: Flow<String> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_REMOTE_SERVER_URL] ?: "" }

    val activeServerTypeFlow: Flow<ServerType> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { ServerType.fromName(it[KEY_ACTIVE_SERVER_TYPE]) }

    val savedUsernameFlow: Flow<String> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_USERNAME] ?: "" }

    /** Recupera filtros separados por comas; ignora nombres desconocidos, duplicados y ALL. */
    private fun parseFilterSet(raw: String?): Set<ItemFilter> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(",")
            .mapNotNull { name -> ItemFilter.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } }
            .filter { it != ItemFilter.ALL }
            .toSet()
    }

    val librariesFilterFlow: Flow<Set<ItemFilter>> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { parseFilterSet(it[KEY_LIBRARIES_FILTER]) }

    val seriesFilterFlow: Flow<Set<ItemFilter>> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { parseFilterSet(it[KEY_SERIES_FILTER]) }

    val booksFilterFlow: Flow<Set<ItemFilter>> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { parseFilterSet(it[KEY_BOOKS_FILTER]) }

    val seriesSortFlow: Flow<SeriesSortOrder> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { SeriesSortOrder.fromName(it[KEY_SERIES_SORT]) }

    suspend fun saveLibrariesFilter(filters: Set<ItemFilter>) {
        store.edit { preferences ->
            preferences[KEY_LIBRARIES_FILTER] = filters.joinToString(",") { it.name }
        }
    }

    suspend fun saveSeriesFilter(filters: Set<ItemFilter>) {
        store.edit { preferences ->
            preferences[KEY_SERIES_FILTER] = filters.joinToString(",") { it.name }
        }
    }

    suspend fun saveBooksFilter(filters: Set<ItemFilter>) {
        store.edit { preferences ->
            preferences[KEY_BOOKS_FILTER] = filters.joinToString(",") { it.name }
        }
    }

    suspend fun saveSeriesSort(order: SeriesSortOrder) {
        store.edit { preferences ->
            preferences[KEY_SERIES_SORT] = order.name
        }
    }

    /**
     * Normaliza ambas direcciones y selecciona el tipo activo sin comprobar la conexión.
     * Las direcciones vacías no sustituyen las guardadas; tampoco vacían la URL activa.
     * Los errores de persistencia se propagan.
     */
    suspend fun saveServerUrls(localUrl: String, remoteUrl: String, activeType: ServerType) {
        val cleanLocal = normalizeUrl(localUrl)
        val cleanRemote = normalizeUrl(remoteUrl)
        val activeUrl = if (activeType == ServerType.REMOTE) cleanRemote else cleanLocal
        store.edit { preferences ->
            if (cleanLocal.isNotBlank()) preferences[KEY_LOCAL_SERVER_URL] = cleanLocal
            if (cleanRemote.isNotBlank()) preferences[KEY_REMOTE_SERVER_URL] = cleanRemote
            preferences[KEY_ACTIVE_SERVER_TYPE] = activeType.name
            if (activeUrl.isNotBlank()) {
                preferences[KEY_SERVER_URL] = activeUrl
            }
        }
    }

    /**
     * Cambia el tipo activo y devuelve su URL guardada, o una cadena vacía si no existe.
     * Para LOCAL admite la URL antigua como alternativa; un destino vacío conserva la URL activa.
     * No autentica de nuevo y propaga los errores de persistencia.
     */
    suspend fun switchActiveServer(type: ServerType): String {
        var targetUrl = ""
        store.edit { preferences ->
            val localUrl = preferences[KEY_LOCAL_SERVER_URL] ?: preferences[KEY_SERVER_URL] ?: ""
            val remoteUrl = preferences[KEY_REMOTE_SERVER_URL] ?: ""
            targetUrl = if (type == ServerType.REMOTE) remoteUrl else localUrl
            preferences[KEY_ACTIVE_SERVER_TYPE] = type.name
            if (targetUrl.isNotBlank()) {
                preferences[KEY_SERVER_URL] = targetUrl
            }
        }
        return targetUrl
    }

    /**
     * Cifra la contraseña antes de guardar la sesión y elimina su antigua copia en texto plano.
     * Normaliza las URL y recorta el usuario; [otherUrl], si no está vacía, actualiza el otro tipo.
     * Marca la sesión como iniciada sin autenticarla. Propaga errores de cifrado y persistencia.
     */
    suspend fun saveCredentials(
        serverConfig: ServerConfig,
        type: ServerType = ServerType.LOCAL,
        otherUrl: String? = null
    ) {
        val cleanUrl = normalizeUrl(serverConfig.serverUrl)
        val cleanOther = otherUrl?.let { normalizeUrl(it) }
        val encryptedPassword = credentialCipher.encrypt(serverConfig.password)
        store.edit { preferences ->
            preferences[KEY_SERVER_URL] = cleanUrl
            preferences[KEY_USERNAME] = serverConfig.username.trim()
            preferences[KEY_ENCRYPTED_PASSWORD] = encryptedPassword
            preferences.remove(KEY_PASSWORD)
            preferences[KEY_IS_LOGGED_IN] = true
            preferences[KEY_ACTIVE_SERVER_TYPE] = type.name
            if (type == ServerType.LOCAL) {
                preferences[KEY_LOCAL_SERVER_URL] = cleanUrl
                if (!cleanOther.isNullOrBlank()) {
                    preferences[KEY_REMOTE_SERVER_URL] = cleanOther
                }
            } else {
                preferences[KEY_REMOTE_SERVER_URL] = cleanUrl
                if (!cleanOther.isNullOrBlank()) {
                    preferences[KEY_LOCAL_SERVER_URL] = cleanOther
                }
            }
        }
    }

    /** Cierra la sesión y elimina ambas formas de contraseña; conserva usuario, URL y preferencias. */
    suspend fun clearCredentials() {
        store.edit { preferences ->
            preferences[KEY_IS_LOGGED_IN] = false
            preferences.remove(KEY_PASSWORD)
            preferences.remove(KEY_ENCRYPTED_PASSWORD)
        }
    }
}
