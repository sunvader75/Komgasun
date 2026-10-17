package com.sunvader.komgasun.ui.reader.epub

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

/** Identity never contains credentials or untrusted path segments. */
object EpubReaderStorage {
    fun identity(server: String, account: String, book: String): String = MessageDigest.getInstance("SHA-256")
        .digest(listOf(server, account, book).joinToString("") { "${it.length}:$it" }.toByteArray())
        .joinToString("") { "%02x".format(it) }

    /** A corrupt cache is bypassed once; a corrupt fresh download is reported, never looped. */
    suspend fun load(file: File, download: suspend (File) -> File): EpubBook {
        if (file.isFile && file.length() > 0) {
            try { return EpubParser.parse(file) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Retry with the repository's atomic temporary-file downloader. */ }
        }
        return EpubParser.parse(download(file))
    }

    fun resumeLocator(value: String?, chapters: Int, serverPage: Int, completed: Boolean): Pair<Int, Double>? {
        if (completed || serverPage < 1) return null
        return decodeLocator(value, chapters)?.takeIf { it.first == serverPage - 1 }
    }

    fun decodeLocator(value: String?, chapters: Int): Pair<Int, Double>? {
        val parts = value?.split(':') ?: return null
        if (parts.size != 2) return null
        val chapter = parts[0].toIntOrNull() ?: return null
        val fraction = parts[1].toDoubleOrNull() ?: return null
        if (chapter !in 0 until chapters || !fraction.isFinite() || fraction !in 0.0..1.0) return null
        return chapter to fraction
    }
}
