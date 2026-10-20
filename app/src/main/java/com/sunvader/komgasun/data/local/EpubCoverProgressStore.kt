package com.sunvader.komgasun.data.local

import android.content.SharedPreferences
import com.sunvader.komgasun.data.model.EpubCoverProgress
import com.sunvader.komgasun.data.model.ServerConfig
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import androidx.core.content.edit

/** Resúmenes privados y acotados para presentación; no envían localizadores ni cambian el estado de lectura. */
class EpubCoverProgressStore(private val preferences: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Devuelve el resumen válido de servidor, usuario y libro, o `null` si falta o falla la lectura. */
    fun read(config: ServerConfig, bookId: String): EpubCoverProgress? =
        runCatching {
            preferences.getString(key(config, bookId), null)?.let {
                json.decodeFromString<EpubCoverProgress>(it).takeIf { summary -> summary.isValid }
            }
        }.getOrNull()

    /**
     * Guarda un resumen válido; rechaza otro con IllegalArgumentException y omite escrituras idénticas.
     * Al añadir una clave, elimina las primeras claves en orden lexicográfico para limitarse a 256.
     * La actualización en disco es asíncrona, según SharedPreferences.
     */
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

    /** Elimina únicamente el resumen de este servidor, usuario y libro; no modifica el progreso remoto. */
    fun clear(config: ServerConfig, bookId: String) {
        preferences.edit { remove(key(config, bookId))}
    }

    /** Deriva una clave SHA-256 de URL, usuario e ID con longitudes explícitas para evitar ambigüedades. */
    private fun key(config: ServerConfig, bookId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(listOf(config.serverUrl, config.username, bookId).joinToString("") { "${it.length}:$it" }.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
