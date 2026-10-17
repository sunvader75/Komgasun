package com.sunvader.komgasun.ui.reader.epub

import java.net.URI

/** ZIP names are decoded paths; references are URI-encoded and decoded exactly once. */
object EpubPath {
    fun resolve(basePath: String, reference: String): String {
        val uri = try { URI(reference.replace(" ", "%20")) } catch (e: Exception) {
            throw IllegalArgumentException("Ruta EPUB inválida: $reference", e)
        }
        require(!uri.isAbsolute && uri.rawAuthority == null && uri.rawQuery == null) {
            "Referencia EPUB externa o no admitida: $reference"
        }
        val path = uri.path.orEmpty()
        require(!path.startsWith('/') && '\\' !in path && path.none { it.code < 32 }) {
            "Ruta EPUB insegura: $reference"
        }
        val combined = if (path.isEmpty()) basePath else basePath.substringBeforeLast('/', "").let {
            if (it.isEmpty()) path else "$it/$path"
        }
        val parts = mutableListOf<String>()
        for (part in combined.split('/')) when (part) {
            "", "." -> Unit
            ".." -> { require(parts.isNotEmpty()) { "Ruta EPUB fuera del archivo: $reference" }; parts.removeAt(parts.lastIndex) }
            else -> parts.add(part)
        }
        return parts.joinToString("/").also { require(it.isNotEmpty()) { "Ruta EPUB vacía" } }
    }
}
