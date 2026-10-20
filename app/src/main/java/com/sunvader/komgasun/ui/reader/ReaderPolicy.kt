package com.sunvader.komgasun.ui.reader

/**
 * Devuelve [current], hasta tres páginas posteriores y una anterior, en ese orden y con índices desde cero.
 * Excluye posiciones fuera de [total]; si la actual no existe, devuelve una lista vacía.
 */
fun readerPageWindow(current: Int, total: Int): List<Int> {
    if (current !in 0 until total) return emptyList()
    return (listOf(current) + (1..3).map { current + it } + listOf(current - 1))
        .filter { it in 0 until total }
}

enum class ReaderLoadStatus { ERROR, LOADING, READY }

/** Da prioridad al error; sin error, solo considera listo un lector preparado y que no está cargando. */
fun readerLoadStatus(loading: Boolean, ready: Boolean, error: String?): ReaderLoadStatus = when {
    error != null -> ReaderLoadStatus.ERROR
    loading || !ready -> ReaderLoadStatus.LOADING
    else -> ReaderLoadStatus.READY
}
