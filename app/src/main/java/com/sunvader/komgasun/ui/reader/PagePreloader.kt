package com.sunvader.komgasun.ui.reader

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.delay

/** Crea la solicitud sin ejecutarla; visualización y precarga comparten tamaño original y clave de caché por URL de Coil. */
fun readerPageRequest(context: Context, url: String): ImageRequest =
    ImageRequest.Builder(context).data(url).size(Size.ORIGINAL).crossfade(true).build()

/**
 * Precarga secuencialmente las vecinas de [current] (índice desde cero) tras esperar 150 ms.
 * El cambio de página o de URLs cancela el trabajo anterior; no vuelve a solicitar la página actual.
 * [diagnostics], si existe, registra números de página desde uno. La caché pertenece a Coil.
 */
@Composable
fun PagePreloader(current: Int, urls: List<String>, diagnostics: ReaderDiagnostics? = null) {
    val context = LocalContext.current
    LaunchedEffect(current, urls) {
        // Da ventaja a la solicitud visible; una nueva página cancela el trabajo obsoleto.
        delay(150)
        readerPageWindow(current, urls.size).drop(1).forEach { page ->
            val request = readerPageRequest(context, urls[page])
            context.imageLoader.execute(if (diagnostics == null) request else request.newBuilder()
                .listener(diagnostics.requestListener(page + 1, ReaderDiagnostics.Kind.PRELOAD)).build())
        }
    }
}
