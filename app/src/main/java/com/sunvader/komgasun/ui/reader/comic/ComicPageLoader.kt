package com.sunvader.komgasun.ui.reader.comic

import com.sunvader.komgasun.ui.reader.readerPageWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Planificador de sesión confinado al hilo principal, incluido [scope]: como máximo dos cargas y cinco resultados retenidos.
 * [load] recibe índices desde cero; la vista consume el resultado ya decodificado sin otra solicitud por URL.
 */
internal class ComicPageLoader<T : Any>(
    private val scope: CoroutineScope,
    private val total: Int,
    private val load: suspend (Int) -> T
) {
    data class Page<T>(val value: T? = null, val error: Throwable? = null)
    private val mutablePages = MutableStateFlow<Map<Int, Page<T>>>(emptyMap())
    val pages = mutablePages.asStateFlow()
    private val jobs = mutableMapOf<Int, Job>()
    private var window = emptyList<Int>()
    private var closed = false

    /** Recalcula la ventana desde [current], cancela cargas ajenas y prioriza la actual; tras cerrar no hace nada. */
    fun moveTo(current: Int) {
        if (closed) return
        window = readerPageWindow(current, total)
        jobs.keys.toList().filter { it !in window }.forEach { jobs.remove(it)?.cancel() }
        mutablePages.value = mutablePages.value.filterKeys { it in window }
        // Al retroceder, ambas cargas pueden ser especulativas. Conserva la actual si ya
        // estaba en curso; en otro caso cancela la vecina de menor prioridad para no retrasarla.
        if (current in window && current !in jobs && current !in mutablePages.value && jobs.size >= 2) {
            window.lastOrNull { it in jobs && it != current }?.let { page ->
                jobs.remove(page)?.cancel()
            }
        }
        pump()
    }

    /** Descarta el resultado de [page] y replanifica; ignora páginas fuera de ventana, en curso o posteriores al cierre. */
    fun retry(page: Int) {
        if (closed || page !in window || jobs.containsKey(page)) return
        mutablePages.value = mutablePages.value - page
        moveTo(window.first())
    }

    /** Ocupa los huecos por prioridad, publica resultados o errores y propaga cancelaciones sin convertirlas en fallos. */
    private fun pump() {
        if (closed) return
        for (page in window) {
            if (jobs.size >= 2) break
            if (page in jobs || page in mutablePages.value) continue
            // Una sola carga especulativa deja capacidad para una nueva página actual.
            if (page != window.first() && jobs.keys.any { it != window.first() }) continue
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val result = load(page)
                    if (page in window && !closed) mutablePages.value = mutablePages.value + (page to Page(value = result))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (page in window && !closed) mutablePages.value = mutablePages.value + (page to Page<T>(error = error))
                } finally {
                    // Un trabajo retirado o sustituido no debe quitar el hueco de su sustituto.
                    if (jobs[page] == coroutineContext[Job]) {
                        jobs.remove(page)
                        pump()
                    }
                }
            }
            jobs[page] = job
            job.start()
        }
    }

    /** Cierra definitivamente la sesión, cancela sus trabajos y suelta resultados sin destruir los recursos que estos contengan. */
    fun close() {
        closed = true
        jobs.values.toList().forEach { it.cancel() }
        jobs.clear()
        mutablePages.value = emptyMap()
    }
}
