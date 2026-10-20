package com.sunvader.komgasun.ui.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.SocketTimeoutException

/** Registro de sesión acotado y seguro entre hilos; capacidad positiva y reloj en ms. No admite URLs, títulos ni mensajes de excepción. */
class ReaderDiagnostics(private val capacity: Int = 64, private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    enum class Kind { VISIBLE, PRELOAD }
    enum class Status { LOADING, SUCCESS, ERROR, CANCELLED }
    enum class Source { MEMORY, DISK, NETWORK, UNKNOWN }
    enum class Failure { TIMEOUT, NETWORK, OTHER }
    data class Event(val id: Long, val page: Int, val kind: Kind, val startedMs: Long,
                     val status: Status = Status.LOADING, val elapsedMs: Long? = null,
                     val source: Source = Source.UNKNOWN, val failure: Failure? = null)
    private val sessionStartedMs = clock()
    private var sequence = 0L
    private val mutableEvents = MutableStateFlow<List<Event>>(emptyList())
    val events = mutableEvents.asStateFlow()
    init { require(capacity > 0) }

    /** Añade una carga de página numerada desde uno, descarta los eventos más antiguos y devuelve su identificador único en la sesión. */
    @Synchronized fun start(page: Int, kind: Kind): Long {
        val event = Event(++sequence, page, kind, clock())
        mutableEvents.value = (mutableEvents.value + event).takeLast(capacity)
        return event.id
    }

    /** Finaliza solo un evento aún en carga; ignora identificadores expulsados o ya finalizados y calcula duración no negativa en ms. */
    @Synchronized fun finish(id: Long, status: Status, source: Source = Source.UNKNOWN, failure: Failure? = null) {
        mutableEvents.value = mutableEvents.value.map {
            if (it.id == id && it.status == Status.LOADING) it.copy(status = status,
                elapsedMs = (clock() - it.startedMs).coerceAtLeast(0), source = source, failure = failure) else it
        }
    }

    /** Finaliza con error clasificado sin conservar el mensaje ni la excepción recibida. */
    fun fail(id: Long, error: Throwable) = finish(id, Status.ERROR, failure = when (error) {
        is SocketTimeoutException -> Failure.TIMEOUT
        is java.io.IOException -> Failure.NETWORK
        else -> Failure.OTHER
    })

    /** Devuelve una instantánea textual para [page] (desde uno); las medidas opcionales de memoria global de Coil se expresan en bytes. */
    fun report(page: Int, memoryBytes: Long?, memoryLimitBytes: Long?): String {
        val snapshot = events.value
        val now = clock()
        /** Formatea tiempos relativos al inicio de sesión, incluida la espera de cargas pendientes. */
        fun Event.line(): String {
            val start = (startedMs - sessionStartedMs).coerceAtLeast(0)
            val end = elapsedMs?.let { "${start + it}ms" } ?: "pendiente"
            return "#$id Página ${this.page} $kind $status; inicio=${start}ms; fin=$end; origen=$source; total=${elapsedMs?.let { "${it}ms" } ?: "pendiente (${(now - startedMs).coerceAtLeast(0)}ms)"}; error=${failure ?: "ninguno"}"
        }
        return buildString {
            appendLine("Diagnóstico de lectura · página $page")
            appendLine("Memoria Coil (global): ${memoryBytes ?: "no disponible"} / ${memoryLimitBytes ?: "no disponible"} bytes")
            appendLine("Respuesta/descarga de red y decodificación: no disponible por separado; total incluye espera, caché y procesamiento.")
            appendLine("No determina por sí solo la causa ni demuestra falta de caché.")
            appendLine("Actual: ${snapshot.lastOrNull { it.page == page && it.kind == Kind.VISIBLE }?.line() ?: "sin solicitud observada"}")
            appendLine("Precarga: ${snapshot.lastOrNull { it.page == page && it.kind == Kind.PRELOAD }?.line() ?: "sin solicitud observada"}")
            appendLine("Eventos recientes (máximo $capacity; orden de inicio, más antiguo primero; tiempos desde inicio de sesión en ms):")
            snapshot.forEach { appendLine(it.line()) }
        }
    }
}

/** Crea un listener por solicitud para [page] (desde uno); solo guarda categorías saneadas, nunca datos de la solicitud. */
fun ReaderDiagnostics.requestListener(page: Int, kind: ReaderDiagnostics.Kind): coil.request.ImageRequest.Listener =
    object : coil.request.ImageRequest.Listener {
        private var id: Long? = null
        override fun onStart(request: coil.request.ImageRequest) { id = start(page, kind) }
        override fun onCancel(request: coil.request.ImageRequest) { id?.let { finish(it, ReaderDiagnostics.Status.CANCELLED) } }
        override fun onError(request: coil.request.ImageRequest, result: coil.request.ErrorResult) { id?.let { fail(it, result.throwable) } }
        override fun onSuccess(request: coil.request.ImageRequest, result: coil.request.SuccessResult) {
            id?.let { finish(it, ReaderDiagnostics.Status.SUCCESS, when (result.dataSource) {
                coil.decode.DataSource.MEMORY_CACHE -> ReaderDiagnostics.Source.MEMORY
                coil.decode.DataSource.DISK -> ReaderDiagnostics.Source.DISK
                coil.decode.DataSource.NETWORK -> ReaderDiagnostics.Source.NETWORK
                else -> ReaderDiagnostics.Source.UNKNOWN
            }) }
        }
    }
