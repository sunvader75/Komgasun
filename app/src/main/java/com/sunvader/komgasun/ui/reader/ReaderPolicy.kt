package com.sunvader.komgasun.ui.reader

/** Current page first, then up to three ahead and one behind. Indices are zero-based. */
fun readerPageWindow(current: Int, total: Int): List<Int> {
    if (current !in 0 until total) return emptyList()
    return (listOf(current) + (1..3).map { current + it } + listOf(current - 1))
        .filter { it in 0 until total }
}

enum class ReaderLoadStatus { ERROR, LOADING, READY }

fun readerLoadStatus(loading: Boolean, ready: Boolean, error: String?): ReaderLoadStatus = when {
    error != null -> ReaderLoadStatus.ERROR
    loading || !ready -> ReaderLoadStatus.LOADING
    else -> ReaderLoadStatus.READY
}
