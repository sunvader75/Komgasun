package com.sunvader.komgasun.data.local

import android.content.SharedPreferences
import com.sunvader.komgasun.data.model.EpubCoverProgress
import com.sunvader.komgasun.data.model.ServerConfig
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import androidx.core.content.edit

/** Private, bounded presentation summaries; never used to send a locator or change read status. */
class EpubCoverProgressStore(private val preferences: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(config: ServerConfig, bookId: String): EpubCoverProgress? =
        runCatching {
            preferences.getString(key(config, bookId), null)?.let {
                json.decodeFromString<EpubCoverProgress>(it).takeIf { summary -> summary.isValid }
            }
        }.getOrNull()

    @Synchronized
    fun save(config: ServerConfig, bookId: String, summary: EpubCoverProgress) {
        require(summary.isValid)
        if (read(config, bookId) == summary) return
        val key = key(config, bookId)
        preferences.edit {
            if (!preferences.contains(key)) {
                preferences.all.keys.sorted().take((preferences.all.size - 255).coerceAtLeast(0))
                    .forEach(this::remove)
            }
            putString(key, json.encodeToString(summary))
        }
    }

    fun clear(config: ServerConfig, bookId: String) {
        preferences.edit { remove(key(config, bookId))}
    }

    private fun key(config: ServerConfig, bookId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(listOf(config.serverUrl, config.username, bookId).joinToString("") { "${it.length}:$it" }.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
