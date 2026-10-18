package com.sunvader.komgasun.ui.reader.epub

import kotlin.math.roundToInt

/** Exact, viewport-dependent page counts in spine order. Public page numbers are one-based. */
internal class EpubBookPageMap(counts: List<Int>) {
    val counts = counts.toList()
    init {
        require(counts.isNotEmpty() && counts.all { it > 0 })
        require(counts.sumOf { it.toLong() } <= Int.MAX_VALUE)
    }
    val total = counts.sum()
    fun absolutePage(chapter: Int, page: Int): Int =
        counts.take(chapter).sum() + page.coerceIn(0, counts[chapter] - 1) + 1

    fun position(page: Int): EpubPosition {
        var remaining = page.coerceIn(1, total) - 1
        counts.forEachIndexed { chapter, count ->
            if (remaining < count) return EpubPosition(chapter, remaining)
            remaining -= count
        }
        error("Page outside measured book")
    }
    fun proportion(chapter: Int, page: Int) = EpubPagination.proportion(page, counts[chapter])
    fun sliderPage(fraction: Float) =
        1 + ((if (fraction.isFinite()) fraction else 0f).coerceIn(0f, 1f) * (total - 1)).roundToInt()
    fun sliderFraction(page: Int): Float =
        if (total == 1) 0f else (page.coerceIn(1, total) - 1).toFloat() / (total - 1)
}
