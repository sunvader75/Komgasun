package com.sunvader.komgasun.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * Aplica ampliación y desplazamiento acotado al contenido sin bloquear el paginador a escala normal.
 * El pellizco limita la escala entre [minScale] y [maxScale], que deben formar un intervalo válido.
 * El doble toque alterna entre 1 y 2,5, independientemente de esos límites. Cambiar [resetKey]
 * restablece escala y desplazamiento y notifica que ya no hay ampliación ni gesto multitáctil.
 * [onZoomChanged] usa el umbral 1,05; [onMultiTouchChanged] permite suspender el desplazamiento
 * del contenedor desde la fase inicial del gesto. Con ampliación se consume el arrastre de un
 * dedo; sin ella se deja libre para el paginador. [onTap] recibe el toque simple.
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    resetKey: Any? = null,
    onTap: (() -> Unit)? = null,
    onZoomChanged: ((Boolean) -> Unit)? = null,
    onMultiTouchChanged: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(resetKey) {
        scale = 1f
        offset = Offset.Zero
        onZoomChanged?.invoke(false)
        onMultiTouchChanged?.invoke(false)
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            // Intercept multi-touch early in Initial pass to disable parent scroll immediately
            .pointerInput(Unit) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            onMultiTouchChanged?.invoke(true)
                        }
                    } while (event.changes.any { it.pressed })
                    onMultiTouchChanged?.invoke(false)
                }
            }
            // Tap & Double-tap
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap?.invoke() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged?.invoke(false)
                        } else {
                            val targetScale = 2.5f
                            if (boxSize.width > 0 && boxSize.height > 0) {
                                val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                                val maxOffsetX = (boxSize.width * (targetScale - 1f)) / 2f
                                val maxOffsetY = (boxSize.height * (targetScale - 1f)) / 2f
                                val targetOffset = (center - tapOffset) * (targetScale - 1f)
                                offset = Offset(
                                    x = targetOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                    y = targetOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            }
                            scale = targetScale
                            onZoomChanged?.invoke(true)
                        }
                    }
                )
            }
            // Pinch-zoom & Pan
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.count { it.pressed }

                        if (pressedPointers >= 2) {
                            // Two or more fingers: active pinch-to-zoom
                            onMultiTouchChanged?.invoke(true)
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
                            scale = newScale
                            val isZoomed = scale > 1.05f
                            onZoomChanged?.invoke(isZoomed)

                            if (isZoomed && boxSize.width > 0 && boxSize.height > 0) {
                                val maxOffsetX = (boxSize.width * (scale - 1f)) / 2f
                                val maxOffsetY = (boxSize.height * (scale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                            event.changes.forEach { it.consume() }
                        } else if (pressedPointers == 1 && scale > 1.05f) {
                            // One finger while zoomed in: pan around without paging
                            val panChange = event.calculatePan()
                            if (boxSize.width > 0 && boxSize.height > 0) {
                                val maxOffsetX = (boxSize.width * (scale - 1f)) / 2f
                                val maxOffsetY = (boxSize.height * (scale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            }
                            event.changes.forEach { it.consume() }
                        }
                        // When 1 finger and not zoomed in: leave unconsumed for HorizontalPager swipe!
                    } while (event.changes.any { it.pressed })

                    onMultiTouchChanged?.invoke(false)
                    if (scale <= 1.05f) {
                        scale = 1f
                        offset = Offset.Zero
                        onZoomChanged?.invoke(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
