package com.sunvader.komgasun.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * Pellizco entre [minScale] y [maxScale]; arrastre con inercia solo al ampliar.
 * A escala distinta de 1, los bordes navegan y el centro muestra los controles.
 * El multitáctil nunca se interpreta como tap. Un [zoomState] compartido conserva
 * escala y posición entre páginas; sin él, [resetKey] restablece la página.
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 0.5f,
    maxScale: Float = 5f,
    resetKey: Any? = null,
    onTap: (() -> Unit)? = null,
    onNavigate: ((Int) -> Unit)? = null,
    onZoomChanged: ((Boolean) -> Unit)? = null,
    onMultiTouchChanged: ((Boolean) -> Unit)? = null,
    zoomState: ReaderZoomState = remember(resetKey) { ReaderZoomState() },
    content: @Composable () -> Unit
) {
    val currentTap by rememberUpdatedState(onTap)
    val currentNavigate by rememberUpdatedState(onNavigate)
    val currentZoomChanged by rememberUpdatedState(onZoomChanged)
    val currentMultiTouchChanged by rememberUpdatedState(onMultiTouchChanged)
    var multiTouchGesture by remember { mutableStateOf(false) }
    var scale by zoomState.scale
    var offset by zoomState.offset
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val motionScope = rememberCoroutineScope()
    var flingJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(resetKey, zoomState) {
        flingJob?.cancel()
    }

    LaunchedEffect(resetKey, zoomState) {
        currentZoomChanged?.invoke(zoomState.isScaled)
        currentMultiTouchChanged?.invoke(false)
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            // Intercept multi-touch early in Initial pass to disable parent scroll immediately
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    flingJob?.cancel()
                    multiTouchGesture = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            multiTouchGesture = true
                            currentMultiTouchChanged?.invoke(true)
                        }
                    } while (event.changes.any { it.pressed })
                    currentMultiTouchChanged?.invoke(false)
                }
            }
            // Tap & Double-tap
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { position ->
                        if (!multiTouchGesture) {
                            val direction = readerTapDirection(scale, position.x / boxSize.width.coerceAtLeast(1))
                            if (direction != 0) currentNavigate?.invoke(direction)
                            else currentTap?.invoke()
                        }
                    },
                    onDoubleTap = { tapOffset ->
                        if (multiTouchGesture) return@detectTapGestures
                        if (isReaderScaled(scale)) {
                            scale = 1f
                            offset = Offset.Zero
                            currentZoomChanged?.invoke(false)
                        } else {
                            val targetScale = 2.5f.coerceIn(minScale, maxScale)
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
                            currentZoomChanged?.invoke(true)
                        }
                    }
                )
            }
            // Pinch-zoom & Pan
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    var hadMultiTouch = false
                    var drag = Offset.Zero
                    var dragging = false
                    do {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.count { it.pressed }
                        if (!hadMultiTouch) {
                            event.changes.firstOrNull { it.id == down.id }?.let {
                                velocityTracker.addPosition(it.uptimeMillis, it.position)
                            }
                        }

                        if (pressedPointers >= 2) {
                            // Two or more fingers: active pinch-to-zoom
                            hadMultiTouch = true
                            currentMultiTouchChanged?.invoke(true)
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
                            scale = newScale
                            val isZoomed = isReaderScaled(scale)
                            currentZoomChanged?.invoke(isZoomed)

                            if (scale > 1f && boxSize.width > 0 && boxSize.height > 0) {
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
                        } else if (pressedPointers == 1 && scale > 1f) {
                            // One finger while zoomed in: pan around without paging
                            val panChange = event.calculatePan()
                            drag += panChange
                            if (drag.getDistance() > viewConfiguration.touchSlop) dragging = true
                            if (!dragging) continue
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
                        // At normal size, leave one-finger drags to the pager; reduced pages disable it.
                    } while (event.changes.any { it.pressed })

                    if (dragging && !hadMultiTouch && scale > 1f &&
                        boxSize.width > 0 && boxSize.height > 0
                    ) {
                        val velocity = velocityTracker.calculateVelocity()
                        val maxOffsetX = boxSize.width * (scale - 1f) / 2f
                        val maxOffsetY = boxSize.height * (scale - 1f) / 2f
                        flingJob = motionScope.launch {
                            val motion = Animatable(offset, Offset.VectorConverter)
                            motion.updateBounds(
                                lowerBound = Offset(-maxOffsetX, -maxOffsetY),
                                upperBound = Offset(maxOffsetX, maxOffsetY)
                            )
                            motion.animateDecay(
                                initialVelocity = Offset(velocity.x, velocity.y),
                                animationSpec = exponentialDecay(frictionMultiplier = 0.6f)
                            ) {
                                offset = value
                            }
                        }
                    }
                    currentMultiTouchChanged?.invoke(false)
                    if (!isReaderScaled(scale)) {
                        scale = 1f
                        offset = Offset.Zero
                        currentZoomChanged?.invoke(false)
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
