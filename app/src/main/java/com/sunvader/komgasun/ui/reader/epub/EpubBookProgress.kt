package com.sunvader.komgasun.ui.reader.epub

/** Cada capítulo pesa lo mismo, de acuerdo con el porcentaje global del lector, no con sus páginas. */
internal object EpubBookProgress {
    /** Combina índice base cero y fracción local; sin capítulos devuelve cero y limita el resultado a 0..1. */
    fun fraction(chapter: Int, proportion: Double, chapters: Int): Double =
        if (chapters < 1) 0.0 else
            ((chapter + proportion.coerceIn(0.0, 1.0)) / chapters).coerceIn(0.0, 1.0)

    /** Divide la fracción entre capítulos; entrada no finita o libro vacío da inicio, y 1 da final del último. */
    fun position(fraction: Double, chapters: Int): Pair<Int, Double> {
        if (chapters < 1 || !fraction.isFinite()) return 0 to 0.0
        val scaled = fraction.coerceIn(0.0, 1.0) * chapters
        val chapter = scaled.toInt().coerceIn(0, chapters - 1)
        return chapter to (scaled - chapter).coerceIn(0.0, 1.0)
    }
}
