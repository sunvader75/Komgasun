package com.sunvader.komgasun.ui.common

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

internal fun isReaderScaled(scale: Float): Boolean = abs(scale - 1f) > 0.01f

/** Keeps a scaled page reachable while allowing one-finger movement in either zoom direction. */
internal fun readerPanOffset(
    offset: Offset,
    pan: Offset,
    scale: Float,
    width: Float,
    height: Float
): Offset {
    if (!isReaderScaled(scale)) return Offset.Zero
    val bounds = readerPanBounds(scale, width, height)
    return Offset(
        (offset.x + pan.x).coerceIn(-bounds.x, bounds.x),
        (offset.y + pan.y).coerceIn(-bounds.y, bounds.y)
    )
}

internal fun readerPanBounds(scale: Float, width: Float, height: Float): Offset =
    Offset(width * abs(scale - 1f) / 2f, height * abs(scale - 1f) / 2f)

/** Only scaled pages use the outer 15 percent for page navigation. */
internal fun readerTapDirection(scale: Float, horizontalFraction: Float): Int = when {
    !isReaderScaled(scale) -> 0
    horizontalFraction < 0.15f -> -1
    horizontalFraction > 0.85f -> 1
    else -> 0
}

/** Translation of the centered zoom layer that places the fitted page origin at the viewport origin. */
internal fun readerPageStartOffset(
    scale: Float,
    width: Float,
    height: Float,
    imageWidth: Float,
    imageHeight: Float
): Offset {
    if (scale <= 1f) return Offset.Zero
    if (!imageWidth.isFinite() || !imageHeight.isFinite() || imageWidth <= 0f || imageHeight <= 0f) return Offset.Zero
    val fittedHeight = width * (imageHeight / imageWidth)
    val pageHeight = if (width > height) fittedHeight else minOf(height, fittedHeight)
    val pageWidth = if (width > height) width else minOf(width, pageHeight * imageWidth / imageHeight)
    return Offset(
        (pageWidth * scale - width) / 2f,
        if (width > height) height * (scale - 1f) / 2f else (pageHeight * scale - height) / 2f
    )
}

/** Estado compartido por las páginas de un lector; no se guarda entre libros. */
class ReaderZoomState {
    internal val scale = mutableFloatStateOf(1f)
    internal val offset = mutableStateOf(Offset.Zero)
    val isScaled: Boolean get() = isReaderScaled(scale.floatValue)
}
