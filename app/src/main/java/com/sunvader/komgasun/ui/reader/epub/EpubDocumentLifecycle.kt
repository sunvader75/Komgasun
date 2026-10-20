package com.sunvader.komgasun.ui.reader.epub

/** Identidad del documento y propiedad de su instalación asíncrona; uso exclusivo desde el hilo UI. */
internal class EpubDocumentLifecycle {
    var url: String = ""
        private set
    private var epoch = 0L
    private var pending: Long? = null
    /** Inicia una época, incluso con la misma URL, e invalida la reserva anterior. */
    fun begin(url: String) { this.url = url; epoch++; pending = null }
    /** Reserva la instalación solo para la URL actual no vacía y sin otra reserva; devuelve ticket o null. */
    fun reserve(callbackUrl: String?): Long? {
        if (callbackUrl != url || url.isEmpty() || pending != null) return null
        return epoch.also { pending = it }
    }
    /** Consume la reserva vigente y devuelve success; un ticket antiguo no modifica el estado. */
    fun complete(ticket: Long, success: Boolean): Boolean {
        if (!isCurrent(ticket) || pending != ticket) return false
        pending = null
        return success
    }
    /** Comprueba la época, no que siga pendiente una reserva. */
    fun isCurrent(ticket: Long) = ticket == epoch
}
