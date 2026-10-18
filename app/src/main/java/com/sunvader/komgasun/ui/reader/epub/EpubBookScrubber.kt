package com.sunvader.komgasun.ui.reader.epub

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

@Composable
internal fun EpubBookScrubber(
    pages: EpubBookPageMap?,
    currentPage: Int,
    ready: Boolean,
    failed: Boolean,
    dark: Boolean,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bookFraction: Float = 0f,
    onSeekFraction: (Float) -> Unit = {},
    calculationProgress: String = ""
) {
    var fraction by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragPages by remember { mutableStateOf<EpubBookPageMap?>(null) }
    val displayedPages = if (dragging) dragPages else pages
    SideEffect {
        if (!dragging) fraction = pages?.sliderFraction(currentPage) ?: bookFraction
    }
    Column(modifier) {
        Text(
            text = when {
                displayedPages != null -> "${displayedPages.sliderPage(fraction)} / ${displayedPages.total}"
                failed -> "${(fraction * 100).toInt()} % · Total no disponible"
                else -> "${(fraction * 100).toInt()} % · Calculando páginas… $calculationProgress".trimEnd()
            },
            color = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag("epub-page-indicator")
        )
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = {
                if (!dragging) { dragPages = pages; dragging = true }
                fraction = it
            },
            onValueChangeFinished = {
                val targetPages = if (dragging) dragPages else pages
                if (targetPages != null) onSeek(targetPages.sliderPage(fraction)) else onSeekFraction(fraction)
                dragging = false
            },
            valueRange = 0f..1f,
            enabled = ready && (displayedPages == null || displayedPages.total > 1),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = if (dark) Color.DarkGray else Color.LightGray
            ),
            modifier = Modifier.testTag("epub-book-slider")
        )
    }
}
