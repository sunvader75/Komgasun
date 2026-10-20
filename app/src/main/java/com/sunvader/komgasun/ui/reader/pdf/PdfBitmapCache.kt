package com.sunvader.komgasun.ui.reader.pdf

/** El llamante serializa el acceso. Presupuesto y coste en bytes; la expulsión suelta referencias, nunca recicla bitmaps visibles. */
internal class PdfPageCache<T>(private val maxBytes: Long, private val bytes: (T) -> Long) {
    private val entries = LinkedHashMap<Int, T>(8, 0.75f, true)
    /** Busca por índice de página desde cero y promueve el acierto como entrada usada más recientemente. */
    operator fun get(page: Int): T? = entries[page]
    /** Guarda o sustituye por índice; rechaza valores que superen el presupuesto y expulsa los menos usados hasta cumplirlo. */
    fun put(page: Int, value: T) {
        if (bytes(value) > maxBytes) return
        entries[page] = value
        while (entries.values.sumOf(bytes) > maxBytes) {
            entries.remove(entries.keys.first())
        }
    }
    /** Conserva únicamente los índices indicados, sin liberar de forma explícita los recursos de las entradas retiradas. */
    fun retain(pages: List<Int>) { entries.keys.retainAll(pages.toSet()) }
    /** Suelta todas las referencias; los consumidores pueden seguir usando los bitmaps sin reciclarlos. */
    fun clear() { entries.clear() }
}
