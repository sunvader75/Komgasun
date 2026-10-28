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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

internal val LocalReaderFitScale = staticCompositionLocalOf<androidx.compose.runtime.MutableState<Float>?> { null }

/** Conserva el mínimo habitual salvo que la página necesite reducirse más para entrar completa. */
internal fun readerMinimumScale(minScale: Float, fitScale: Float): Float =
    if (fitScale.isFinite() && fitScale > 0f) minOf(minScale, fitScale) else minScale

internal val LocalReaderPageReduction = staticCompositionLocalOf { 1f }

/**
 * Pellizco hasta [maxScale]; el mínimo baja de [minScale] si hace falta para ver la página completa.
 * Arrastre con inercia tanto al ampliar como al reducir.
 * A escala distinta de 1, los bordes navegan y el centro muestra los controles.
 * El multitáctil nunca se interpreta como tap. Un [zoomState] compartido conserva la
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
    val pageFitScale = remember(resetKey) { mutableFloatStateOf(1f) }
    val effectiveMinScale by rememberUpdatedState(readerMinimumScale(minScale, pageFitScale.floatValue))
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
            // Intercept multitouch early in Initial pass to disable parent scroll immediately
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
                            val targetScale = 2.5f.coerceIn(effectiveMinScale, maxScale)
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

                            val newScale = (scale * zoomChange).coerceIn(effectiveMinScale, maxScale)
                            scale = newScale
                            val isZoomed = isReaderScaled(scale)
                            currentZoomChanged?.invoke(isZoomed)

                            offset = if (isReaderScaled(scale) && boxSize.width > 0 && boxSize.height > 0) {
                                readerPanOffset(
                                    offset, panChange, scale,
                                    boxSize.width.toFloat(), boxSize.height.toFloat()
                                )
                            } else {
                                Offset.Zero
                            }
                            event.changes.forEach { it.consume() }
                        } else if (pressedPointers == 1 && isReaderScaled(scale)) {
                            // One finger at either scaled size: pan around without paging
                            val panChange = event.calculatePan()
                            drag += panChange
                            if (drag.getDistance() > viewConfiguration.touchSlop) dragging = true
                            if (!dragging) continue
                            if (boxSize.width > 0 && boxSize.height > 0) {
                                offset = readerPanOffset(
                                    offset, panChange, scale,
                                    boxSize.width.toFloat(), boxSize.height.toFloat()
                                )
                            }
                            event.changes.forEach { it.consume() }
                        }
                        // At normal size, leave one-finger drags to the pager; reduced pages disable it.
                    } while (event.changes.any { it.pressed })

                    if (dragging && !hadMultiTouch && isReaderScaled(scale) &&
                        boxSize.width > 0 && boxSize.height > 0
                    ) {
                        val velocity = velocityTracker.calculateVelocity()
                        val bounds = readerPanBounds(scale, boxSize.width.toFloat(), boxSize.height.toFloat())
                        flingJob = motionScope.launch {
                            val motion = Animatable(offset, Offset.VectorConverter)
                            motion.updateBounds(
                                lowerBound = -bounds,
                                upperBound = bounds
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
        // En apaisado la reducción ocurre en la página, antes del recorte del scroll.
        val reducePage = boxSize.width > boxSize.height && scale < 1f
        CompositionLocalProvider(
            LocalReaderPageReduction provides if (reducePage) scale else 1f,
            LocalReaderFitScale provides pageFitScale
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = if (reducePage) 1f else scale
                    scaleY = if (reducePage) 1f else scale
                    translationX = offset.x
                    translationY = offset.y
                },
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}
