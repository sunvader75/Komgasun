package com.sunvader.komgasun.ui.reader.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import com.sunvader.komgasun.ui.common.LocalReaderFitScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import com.sunvader.komgasun.ui.common.LocalReaderPageReduction
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Calcula la altura en las unidades de [width] y [height] (dp en la vista), usando solo la proporción de la imagen.
 * En horizontal ajusta al ancho; en vertical limita a la altura disponible. Si la imagen o el cálculo no son válidos, devuelve [height].
 */
internal fun pdfPageHeight(width: Float, height: Float, imageWidth: Float, imageHeight: Float, reduction: Float = 1f): Float {
    if (!imageWidth.isFinite() || !imageHeight.isFinite() || imageWidth <= 0f || imageHeight <= 0f) return height
    val fittedHeight = width * (imageHeight / imageWidth)
    if (!fittedHeight.isFinite()) return height
    return if (width > height) fittedHeight * reduction else minOf(height, fittedHeight)
}

/**
 * Distribuye solo páginas cargadas; los marcadores de carga permanecen limitados al área visible.
 * En horizontal habilita desplazamiento vertical según [scrollEnabled] y lo reinicia al cambiar [resetKey].
 * [imageWidth] e [imageHeight] deben usar la misma unidad; [content] recibe el modificador de tamaño de página.
 */
@Composable
internal fun PdfPageContent(
    imageWidth: Float,
    imageHeight: Float,
    resetKey: Any?,
    scrollEnabled: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    val fitScale = LocalReaderFitScale.current
    val reduction = LocalReaderPageReduction.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Usar la altura sin reducir evita que el límite dependa del zoom actual.
        val fullPageHeight = pdfPageHeight(maxWidth.value, maxHeight.value, imageWidth, imageHeight)
        SideEffect { fitScale?.value = maxHeight.value / fullPageHeight }
        if (maxWidth > maxHeight) {
            val pageHeight = pdfPageHeight(maxWidth.value, maxHeight.value, imageWidth, imageHeight, reduction).dp
            key(resetKey) {
                val scrollState = rememberScrollState()
                Box(
                    Modifier.fillMaxSize().verticalScroll(scrollState, enabled = scrollEnabled),
                    contentAlignment = Alignment.TopCenter
                ) {
                    content(Modifier.fillMaxWidth(reduction).height(pageHeight))
                }
            }
        } else {
            content(Modifier.fillMaxSize())
        }
    }
}
