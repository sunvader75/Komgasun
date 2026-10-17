package com.sunvader.komgasun.ui.reader.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun pdfPageHeight(width: Float, height: Float, imageWidth: Float, imageHeight: Float): Float {
    if (!imageWidth.isFinite() || !imageHeight.isFinite() || imageWidth <= 0f || imageHeight <= 0f) return height
    val fittedHeight = width * (imageHeight / imageWidth)
    if (!fittedHeight.isFinite()) return height
    return if (width > height) fittedHeight else minOf(height, fittedHeight)
}

/** Used only for loaded pages; placeholders remain constrained by the viewport. */
@Composable
internal fun PdfPageContent(
    imageWidth: Float,
    imageHeight: Float,
    resetKey: Any?,
    scrollEnabled: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            val pageHeight = pdfPageHeight(maxWidth.value, maxHeight.value, imageWidth, imageHeight).dp
            key(resetKey) {
                val scrollState = rememberScrollState()
                Box(Modifier.fillMaxSize().verticalScroll(scrollState, enabled = scrollEnabled)) {
                    content(Modifier.fillMaxWidth().height(pageHeight))
                }
            }
        } else {
            content(Modifier.fillMaxSize())
        }
    }
}
