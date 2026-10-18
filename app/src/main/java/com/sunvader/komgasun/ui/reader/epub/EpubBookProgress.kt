package com.sunvader.komgasun.ui.reader.epub

/** Equal chapter spans, matching the reader's existing whole-book percentage. */
internal object EpubBookProgress {
    fun fraction(chapter: Int, proportion: Double, chapters: Int): Double =
        if (chapters < 1) 0.0 else
            ((chapter + proportion.coerceIn(0.0, 1.0)) / chapters).coerceIn(0.0, 1.0)

    fun position(fraction: Double, chapters: Int): Pair<Int, Double> {
        if (chapters < 1 || !fraction.isFinite()) return 0 to 0.0
        val scaled = fraction.coerceIn(0.0, 1.0) * chapters
        val chapter = scaled.toInt().coerceIn(0, chapters - 1)
        return chapter to (scaled - chapter).coerceIn(0.0, 1.0)
    }
}
