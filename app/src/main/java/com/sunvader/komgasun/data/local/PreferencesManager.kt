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
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "komgasun_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_LOCAL_SERVER_URL = stringPreferencesKey("local_server_url")
        val KEY_REMOTE_SERVER_URL = stringPreferencesKey("remote_server_url")
        val KEY_ACTIVE_SERVER_TYPE = stringPreferencesKey("active_server_type")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_LIBRARIES_FILTER = stringPreferencesKey("libraries_filter")
        val KEY_SERIES_FILTER = stringPreferencesKey("series_filter")
        val KEY_BOOKS_FILTER = stringPreferencesKey("books_filter")
        val KEY_SERIES_SORT = stringPreferencesKey("series_sort")
    }

    private fun normalizeUrl(url: String): String {
        var clean = url.trim().removeSuffix("/")
        if (clean.isNotBlank() && !clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            clean = "http://$clean"
        }
        return clean
    }

    val serverConfigFlow: Flow<ServerConfig?> = context.dataStore.data
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
            val password = preferences[KEY_PASSWORD]
            val isLoggedIn = preferences[KEY_IS_LOGGED_IN] ?: false

            if (isLoggedIn && !url.isNullOrBlank() && !username.isNullOrBlank()) {
                ServerConfig(
                    serverUrl = url,
                    username = username,
                    password = password ?: ""
                )
            } else {
                null
            }
        }

    val savedUrlFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SERVER_URL] ?: "" }

    val localUrlFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LOCAL_SERVER_URL] ?: it[KEY_SERVER_URL] ?: "" }

    val remoteUrlFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_REMOTE_SERVER_URL] ?: "" }

    val activeServerTypeFlow: Flow<ServerType> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { ServerType.fromName(it[KEY_ACTIVE_SERVER_TYPE]) }

    val savedUsernameFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_USERNAME] ?: "" }

    private fun parseFilterSet(raw: String?): Set<ItemFilter> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(",")
            .mapNotNull { name -> ItemFilter.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } }
            .filter { it != ItemFilter.ALL }
            .toSet()
    }

    val librariesFilterFlow: Flow<Set<ItemFilter>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { parseFilterSet(it[KEY_LIBRARIES_FILTER]) }

    val seriesFilterFlow: Flow<Set<ItemFilter>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { parseFilterSet(it[KEY_SERIES_FILTER]) }

    val booksFilterFlow: Flow<Set<ItemFilter>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { parseFilterSet(it[KEY_BOOKS_FILTER]) }

    val seriesSortFlow: Flow<SeriesSortOrder> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { SeriesSortOrder.fromName(it[KEY_SERIES_SORT]) }

    suspend fun saveLibrariesFilter(filters: Set<ItemFilter>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LIBRARIES_FILTER] = filters.joinToString(",") { it.name }
        }
    }

    suspend fun saveSeriesFilter(filters: Set<ItemFilter>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SERIES_FILTER] = filters.joinToString(",") { it.name }
        }
    }

    suspend fun saveBooksFilter(filters: Set<ItemFilter>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BOOKS_FILTER] = filters.joinToString(",") { it.name }
        }
    }

    suspend fun saveSeriesSort(order: SeriesSortOrder) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SERIES_SORT] = order.name
        }
    }

    suspend fun saveServerUrls(localUrl: String, remoteUrl: String, activeType: ServerType) {
        val cleanLocal = normalizeUrl(localUrl)
        val cleanRemote = normalizeUrl(remoteUrl)
        val activeUrl = if (activeType == ServerType.REMOTE) cleanRemote else cleanLocal
        context.dataStore.edit { preferences ->
            if (cleanLocal.isNotBlank()) preferences[KEY_LOCAL_SERVER_URL] = cleanLocal
            if (cleanRemote.isNotBlank()) preferences[KEY_REMOTE_SERVER_URL] = cleanRemote
            preferences[KEY_ACTIVE_SERVER_TYPE] = activeType.name
            if (activeUrl.isNotBlank()) {
                preferences[KEY_SERVER_URL] = activeUrl
            }
        }
    }

    suspend fun switchActiveServer(type: ServerType): String {
        var targetUrl = ""
        context.dataStore.edit { preferences ->
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

    suspend fun saveCredentials(
        serverConfig: ServerConfig,
        type: ServerType = ServerType.LOCAL,
        otherUrl: String? = null
    ) {
        val cleanUrl = normalizeUrl(serverConfig.serverUrl)
        val cleanOther = otherUrl?.let { normalizeUrl(it) }
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = cleanUrl
            preferences[KEY_USERNAME] = serverConfig.username.trim()
            preferences[KEY_PASSWORD] = serverConfig.password
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

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_LOGGED_IN] = false
            preferences.remove(KEY_PASSWORD)
        }
    }
}
