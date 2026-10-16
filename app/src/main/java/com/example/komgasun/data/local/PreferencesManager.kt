package com.example.komgasun.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
