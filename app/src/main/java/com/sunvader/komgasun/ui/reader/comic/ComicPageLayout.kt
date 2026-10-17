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

/** Landscape uses the whole width; portrait keeps the existing fit policy. */
internal fun comicPageHeight(width: Float, height: Float, imageWidth: Float, imageHeight: Float): Float {
    if (!imageWidth.isFinite() || !imageHeight.isFinite() || imageWidth <= 0f || imageHeight <= 0f) return height
    val fittedHeight = width * (imageHeight / imageWidth)
    return if (width > height) fittedHeight else minOf(height, fittedHeight)
}

@Composable
internal fun SubcomposeAsyncImageScope.ComicPageContent(scrollEnabled: Boolean) {
    val imageSize = painter.intrinsicSize
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Only successful images get unbounded vertical space. Loading/error stay viewport-sized.
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
