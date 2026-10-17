package com.sunvader.komgasun.ui.reader

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.delay

/** Display and prefetch deliberately share original-size decoding and Coil's URL cache key. */
fun readerPageRequest(context: Context, url: String): ImageRequest =
    ImageRequest.Builder(context).data(url).size(Size.ORIGINAL).crossfade(true).build()

@Composable
fun PagePreloader(current: Int, urls: List<String>, diagnostics: ReaderDiagnostics? = null) {
    val context = LocalContext.current
    LaunchedEffect(current, urls) {
        // Give the visible request a head start; a new page cancels obsolete work.
        delay(150)
        readerPageWindow(current, urls.size).drop(1).forEach { page ->
            val request = readerPageRequest(context, urls[page])
            context.imageLoader.execute(if (diagnostics == null) request else request.newBuilder()
                .listener(diagnostics.requestListener(page + 1, ReaderDiagnostics.Kind.PRELOAD)).build())
        }
    }
}
