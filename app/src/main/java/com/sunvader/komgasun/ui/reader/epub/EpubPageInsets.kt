package com.sunvader.komgasun.ui.reader.epub

/** Pixel geometry shared by live layout, snapshots and the original text-relative tap zones. */
internal data class EpubPageInsets(val horizontal: Int, val vertical: Int) {
    fun viewport(width: Int, height: Int): EpubTextViewport {
        val left = horizontal.coerceAtMost(width / 2)
        val top = vertical.coerceAtMost(height / 2)
        return EpubTextViewport(left, top, (width - 2 * left).coerceAtLeast(0),
            (height - 2 * top).coerceAtLeast(0))
    }
}

internal data class EpubTextViewport(val left: Int, val top: Int, val width: Int, val height: Int) {
    fun tapFraction(x: Float): Float = ((x - left) / width.coerceAtLeast(1)).coerceIn(0f, 1f)
}
