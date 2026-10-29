package com.sunvader.komgasun.ui.reader.comic

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import com.sunvader.komgasun.ui.common.LocalReaderFitScale
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.sunvader.komgasun.ui.common.LocalReaderPageReduction
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.SubcomposeAsyncImageScope

/**
 * Devuelve la altura en las unidades del área disponible (dp en la vista); las dimensiones de imagen solo aportan su proporción.
 * En horizontal usa todo el ancho; en vertical limita la altura al área visible.
 * Si las dimensiones de imagen no son finitas o positivas devuelve [height].
 */
internal fun comicPageHeight(width: Float, height: Float, imageWidth: Float, imageHeight: Float, reduction: Float = 1f): Float {
    if (!imageWidth.isFinite() || !imageHeight.isFinite() || imageWidth <= 0f || imageHeight <= 0f) return height
    val fittedHeight = width * (imageHeight / imageWidth)
    return if (width > height) fittedHeight * reduction else minOf(height, fittedHeight)
}

/** Translation of the centered zoom layer that places the fitted page origin at the viewport origin. */
internal fun comicPageStartOffset(scale: Float, width: Float, height: Float, imageWidth: Float, imageHeight: Float): androidx.compose.ui.geometry.Offset {
    val pageHeight = comicPageHeight(width, height, imageWidth, imageHeight)
    val pageWidth = if (width > height) width else minOf(width, pageHeight * imageWidth / imageHeight)
    return androidx.compose.ui.geometry.Offset(
        (pageWidth * scale - width) / 2f,
        if (width > height) height * (scale - 1f) / 2f else (pageHeight * scale - height) / 2f
    )
}

/** Muestra una imagen ya cargada, ajustada al ancho y desplazable en horizontal si [scrollEnabled]; en vertical conserva el ajuste al área visible. */
@Composable
internal fun SubcomposeAsyncImageScope.ComicPageContent(
    scrollEnabled: Boolean,
    restartScale: Float? = null,
    onRestart: (androidx.compose.ui.geometry.Offset) -> Unit = {}
) {
    val imageSize = painter.intrinsicSize
    val fitScale = LocalReaderFitScale.current
    val reduction = LocalReaderPageReduction.current
    val scrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        androidx.compose.runtime.LaunchedEffect(restartScale, maxWidth, maxHeight, imageSize) {
            if (restartScale != null && imageSize.width > 0f && imageSize.height > 0f) {
                scrollState.scrollTo(0)
                onRestart(comicPageStartOffset(
                    restartScale, with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() },
                    imageSize.width, imageSize.height
                ))
            }
        }
        // Usar la altura sin reducir evita que el límite dependa del zoom actual.
        val fullPageHeight = comicPageHeight(maxWidth.value, maxHeight.value, imageSize.width, imageSize.height)
        SideEffect { fitScale?.value = maxHeight.value / fullPageHeight }
        if (maxWidth > maxHeight) {
            // Solo las imágenes cargadas reciben espacio vertical sin límite; carga y error ocupan el área visible.
            val pageHeight = comicPageHeight(maxWidth.value, maxHeight.value, imageSize.width, imageSize.height, reduction)
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().verticalScroll(scrollState, enabled = scrollEnabled),
                contentAlignment = Alignment.TopCenter
            ) {
                this@ComicPageContent.SubcomposeAsyncImageContent(
                    modifier = Modifier.fillMaxWidth(reduction).height(androidx.compose.ui.unit.Dp(pageHeight)),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            this@ComicPageContent.SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}
