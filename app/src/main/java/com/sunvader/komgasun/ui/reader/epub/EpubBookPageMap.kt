package com.sunvader.komgasun.ui.reader.epub

import kotlin.math.roundToInt

/** Recuentos completos según viewport y orden de spine; copia la lista y exige positivos sin desbordamiento. */
internal class EpubBookPageMap(counts: List<Int>) {
    val counts = counts.toList()
    init {
        require(counts.isNotEmpty() && counts.all { it > 0 })
        require(counts.sumOf { it.toLong() } <= Int.MAX_VALUE)
    }
    val total = counts.sum()
    /** Convierte capítulo y página base cero a página global base uno; exige capítulo válido y limita la página. */
    fun absolutePage(chapter: Int, page: Int): Int =
        counts.take(chapter).sum() + page.coerceIn(0, counts[chapter] - 1) + 1

    /** Limita la página global a 1..total y devuelve capítulo y página locales de base cero. */
    fun position(page: Int): EpubPosition {
        var remaining = page.coerceIn(1, total) - 1
        counts.forEachIndexed { chapter, count ->
            if (remaining < count) return EpubPosition(chapter, remaining)
            remaining -= count
        }
        error("Page outside measured book")
    }
    /** Devuelve fracción local entre extremos; exige capítulo válido y limita la página a su recuento. */
    fun proportion(chapter: Int, page: Int) = EpubPagination.proportion(page, counts[chapter])
    /** Redondea una fracción a página global base uno; una fracción no finita equivale al inicio. */
    fun sliderPage(fraction: Float) =
        1 + ((if (fraction.isFinite()) fraction else 0f).coerceIn(0f, 1f) * (total - 1)).roundToInt()
    /** Convierte página global limitada al libro en fracción del deslizador; un libro de una página da cero. */
    fun sliderFraction(page: Int): Float =
        if (total == 1) 0f else (page.coerceIn(1, total) - 1).toFloat() / (total - 1)
}
