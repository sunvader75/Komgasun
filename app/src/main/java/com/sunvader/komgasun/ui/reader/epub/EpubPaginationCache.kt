package com.sunvader.komgasun.ui.reader.epub

import java.io.File
import java.security.MessageDigest

/** Disposable derived data only. All calls run on IO, never during Compose layout. */
internal class EpubPaginationCache(private val directory: File) {
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
        fun key(book: String, width: Int, height: Int, font: Int, renderer: String): String =
            hash("v1|$book|$width|$height|$font|$renderer")
        fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
