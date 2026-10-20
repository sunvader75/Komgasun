package com.sunvader.komgasun.ui.reader.epub

import java.io.File
import java.security.MessageDigest

/** Solo datos derivados prescindibles; el llamador ejecuta las operaciones en IO, no durante el layout de Compose. */
internal class EpubPaginationCache(private val directory: File) {
    /** Lee el formato versionado y exige un recuento válido por capítulo; cualquier fallo se convierte en null. */
    fun load(key: String, chapters: Int): EpubBookPageMap? = runCatching {
        require(key.matches(Regex("[a-f0-9]{64}")))
        val file = File(directory, key)
        require(file.length() in 1..100_000)
        val lines = file.readLines()
        require(lines.size == 2 && lines[0] == "1")
        val counts = lines[1].split(',').map { it.toInt() }
        require(counts.size == chapters && counts.all { it in 1..100_000 })
        EpubBookPageMap(counts)
    }.getOrNull()

    /**
     * Publica mediante fichero provisional y renombrado en el mismo directorio; limpia el provisional
     * incluso si falla. Intenta eliminar claves salvo las 24 más recientes; no comprueba el resultado
     * de los borrados. Los errores de escritura y renombrado se propagan.
     */
    fun save(key: String, pages: EpubBookPageMap) {
        require(key.matches(Regex("[a-f0-9]{64}")))
        check(directory.isDirectory || directory.mkdirs())
        val staging = File.createTempFile("pagination-", ".tmp", directory)
        try {
            staging.writeText("1\n${pages.counts.joinToString(",")}")
            check(staging.renameTo(File(directory, key)))
            directory.listFiles()?.filter { it.name.matches(Regex("[a-f0-9]{64}")) }
                ?.sortedByDescending { it.lastModified() }?.drop(24)?.forEach { it.delete() }
        } finally { staging.delete() }
    }

    companion object {
        /** Incluye libro, dimensiones, tamaño de fuente y descriptor del motor en una clave SHA-256 versionada. */
        fun key(book: String, width: Int, height: Int, font: Int, renderer: String): String =
            hash("v1|$book|$width|$height|$font|$renderer")
        /** Resume texto UTF-8 en hexadecimal SHA-256, apto como nombre sin segmentos de ruta. */
        fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
