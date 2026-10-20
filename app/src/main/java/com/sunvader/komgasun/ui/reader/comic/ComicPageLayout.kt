package com.sunvader.komgasun.ui.reader.comic

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.SubcomposeAsyncImageScope

/**
 * Devuelve la altura en las unidades del área disponible (dp en la vista); las dimensiones de imagen solo aportan su proporción.
 * En horizontal usa todo el ancho; en vertical limita la altura al área visible.
 * Si las dimensiones de imagen no son finitas o positivas devuelve [height].
 */
internal fun comicPageHeight(width: Float, height: Float, imageWidth: Float, imageHeight: Float): Float {
    if (!imageWidth.isFinite() || !imageHeight.isFinite() || imageWidth <= 0f || imageHeight <= 0f) return height
    val fittedHeight = width * (imageHeight / imageWidth)
    return if (width > height) fittedHeight else minOf(height, fittedHeight)
}

/** Muestra una imagen ya cargada, ajustada al ancho y desplazable en horizontal si [scrollEnabled]; en vertical conserva el ajuste al área visible. */
@Composable
internal fun SubcomposeAsyncImageScope.ComicPageContent(scrollEnabled: Boolean) {
    val imageSize = painter.intrinsicSize
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Solo las imágenes cargadas reciben espacio vertical sin límite; carga y error ocupan el área visible.
            val pageHeight = comicPageHeight(maxWidth.value, maxHeight.value, imageSize.width, imageSize.height)
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = scrollEnabled)
            ) {
                this@ComicPageContent.SubcomposeAsyncImageContent(
                    modifier = Modifier.fillMaxWidth().height(androidx.compose.ui.unit.Dp(pageHeight)),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            this@ComicPageContent.SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}
