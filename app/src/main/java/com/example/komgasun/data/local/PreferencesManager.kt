package com.example.komgasun.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.komgasun.data.model.ItemFilter
import com.example.komgasun.data.model.SeriesSortOrder
import com.example.komgasun.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "komgasun_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_LIBRARIES_FILTER = stringPreferencesKey("libraries_filter")
        val KEY_SERIES_FILTER = stringPreferencesKey("series_filter")
        val KEY_BOOKS_FILTER = stringPreferencesKey("books_filter")
        val KEY_SERIES_SORT = stringPreferencesKey("series_sort")
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

    suspend fun saveCredentials(serverConfig: ServerConfig) {
        val cleanUrl = serverConfig.serverUrl.trim().removeSuffix("/")
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = cleanUrl
            preferences[KEY_USERNAME] = serverConfig.username.trim()
            preferences[KEY_PASSWORD] = serverConfig.password
            preferences[KEY_IS_LOGGED_IN] = true
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_LOGGED_IN] = false
            preferences.remove(KEY_PASSWORD)
        }
    }
}
