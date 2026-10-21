package com.sunvader.komgasun.ui.common

import kotlin.math.abs

internal fun isReaderScaled(scale: Float): Boolean = abs(scale - 1f) > 0.01f

/** Only scaled pages use the outer 15 percent for page navigation. */
internal fun readerTapDirection(scale: Float, horizontalFraction: Float): Int = when {
    !isReaderScaled(scale) -> 0
    horizontalFraction < 0.15f -> -1
    horizontalFraction > 0.85f -> 1
    else -> 0
}
