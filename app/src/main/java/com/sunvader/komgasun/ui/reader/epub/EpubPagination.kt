package com.sunvader.komgasun.ui.reader.epub

import kotlin.math.roundToInt

data class EpubPosition(val chapter: Int, val page: Int)

/** Physical pages are viewport-local. Server progress always remains chapter-based. */
object EpubPagination {
    fun move(position: EpubPosition, pages: Int, chapters: Int, direction: Int): EpubPosition {
        if (pages < 1 || chapters < 1) return position
        return when {
            direction > 0 && position.page < pages - 1 -> position.copy(page = position.page + 1)
            direction > 0 && position.chapter < chapters - 1 -> EpubPosition(position.chapter + 1, 0)
            direction < 0 && position.page > 0 -> position.copy(page = position.page - 1)
            direction < 0 && position.chapter > 0 -> EpubPosition(position.chapter - 1, Int.MAX_VALUE)
            else -> position
        }
    }
    fun completed(chapter: Int, chapters: Int, page: Int, pages: Int) =
        chapters > 0 && chapter == chapters - 1 && pages > 0 && page == pages - 1
    fun proportion(page: Int, pages: Int): Double = if (pages <= 1) 0.0 else page.coerceIn(0, pages - 1).toDouble() / (pages - 1)
    fun page(proportion: Double, pages: Int): Int =
        if (!proportion.isFinite() || pages < 1) 0 else (proportion.coerceIn(0.0, 1.0) * (pages - 1)).roundToInt()
}
