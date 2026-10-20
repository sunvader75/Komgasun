package com.sunvader.komgasun.ui.reader.epub

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

/** La identidad resultante no contiene credenciales ni segmentos de ruta aportados por el contenido. */
object EpubReaderStorage {
    /** Separa servidor, cuenta y libro con longitudes antes de SHA-256 para evitar concatenaciones ambiguas. */
    fun identity(server: String, account: String, book: String): String = MessageDigest.getInstance("SHA-256")
        .digest(listOf(server, account, book).joinToString("") { "${it.length}:$it" }.toByteArray())
        .joinToString("") { "%02x".format(it) }

    /**
     * Analiza la caché existente o solicita una única descarga si falta o está dañada. Propaga la
     * cancelación y el fallo de la descarga o de su análisis; no reintenta indefinidamente.
     * El callback es responsable de publicar el fichero descargado y de la seguridad de sus escrituras.
     */
    suspend fun load(file: File, download: suspend (File) -> File): EpubBook {
        if (file.isFile && file.length() > 0) {
            try { return EpubParser.parse(file) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Retry with the repository's atomic temporary-file downloader. */ }
        }
        return EpubParser.parse(download(file))
    }

    /** Compatibilidad: acepta el local solo si coincide con serverPage (base uno) y no figura como completado. */
    fun resumeLocator(value: String?, chapters: Int, serverPage: Int, completed: Boolean): Pair<Int, Double>? {
        if (completed || serverPage < 1) return null
        return decodeLocator(value, chapters)?.takeIf { it.first == serverPage - 1 }
    }

    /** Lee capítulo base cero y fracción entre 0 y 1, con fecha opcional válida; devuelve null si no encajan. */
    fun decodeLocator(value: String?, chapters: Int): Pair<Int, Double>? {
        val parts = value?.split(':', limit = 3) ?: return null
        if (parts.size !in 2..3) return null
        if (parts.size == 3 && EpubProgressionSync.epoch(parts[2]) == null) return null
        val chapter = parts[0].toIntOrNull() ?: return null
        val fraction = parts[1].toDoubleOrNull() ?: return null
        if (chapter !in 0 until chapters || !fraction.isFinite() || fraction !in 0.0..1.0) return null
        return chapter to fraction
    }
}
