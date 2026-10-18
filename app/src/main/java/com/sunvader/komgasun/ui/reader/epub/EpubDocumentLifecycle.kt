package com.sunvader.komgasun.ui.reader.epub

/** UI-thread document identity and asynchronous bootstrap ownership. */
internal class EpubDocumentLifecycle {
    var url: String = ""
        private set
    private var epoch = 0L
    private var pending: Long? = null
    fun begin(url: String) { this.url = url; epoch++; pending = null }
    fun reserve(callbackUrl: String?): Long? {
        if (callbackUrl != url || url.isEmpty() || pending != null) return null
        return epoch.also { pending = it }
    }
    fun complete(ticket: Long, success: Boolean): Boolean {
        if (!isCurrent(ticket) || pending != ticket) return false
        pending = null
        return success
    }
    fun isCurrent(ticket: Long) = ticket == epoch
}
