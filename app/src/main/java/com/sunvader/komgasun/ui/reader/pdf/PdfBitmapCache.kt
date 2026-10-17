package com.sunvader.komgasun.ui.reader.pdf

/** Caller serializes access. Eviction drops references, never recycles displayed bitmaps. */
internal class PdfPageCache<T>(private val maxBytes: Long, private val bytes: (T) -> Long) {
    private val entries = LinkedHashMap<Int, T>(8, 0.75f, true)
    operator fun get(page: Int): T? = entries[page]
    fun put(page: Int, value: T) {
        if (bytes(value) > maxBytes) return
        entries[page] = value
        while (entries.values.sumOf(bytes) > maxBytes) {
            entries.remove(entries.keys.first())
        }
    }
    fun retain(pages: List<Int>) { entries.keys.retainAll(pages.toSet()) }
    fun clear() { entries.clear() }
}
