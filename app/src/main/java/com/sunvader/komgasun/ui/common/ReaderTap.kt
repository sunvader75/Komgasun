package com.sunvader.komgasun.ui.common

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

internal fun isReaderScaled(scale: Float): Boolean = abs(scale - 1f) > 0.01f

/** Only scaled pages use the outer 15 percent for page navigation. */
internal fun readerTapDirection(scale: Float, horizontalFraction: Float): Int = when {
    !isReaderScaled(scale) -> 0
    horizontalFraction < 0.15f -> -1
    horizontalFraction > 0.85f -> 1
    else -> 0
}

/** Estado compartido por las páginas de un lector; no se guarda entre libros. */
class ReaderZoomState {
    internal val scale = mutableFloatStateOf(1f)
    internal val offset = mutableStateOf(Offset.Zero)
    val isScaled: Boolean get() = isReaderScaled(scale.floatValue)
}
