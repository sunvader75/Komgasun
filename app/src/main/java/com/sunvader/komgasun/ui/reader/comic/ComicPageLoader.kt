package com.sunvader.komgasun.ui.reader.comic

import com.sunvader.komgasun.ui.reader.readerPageWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Main-thread confined session scheduler. At most two loads and five retained results.
 * Display consumes the exact decoded result, not another URL request. */
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

    fun moveTo(current: Int) {
        if (closed) return
        window = readerPageWindow(current, total)
        jobs.keys.toList().filter { it !in window }.forEach { jobs.remove(it)?.cancel() }
        mutablePages.value = mutablePages.value.filterKeys { it in window }
        // Backward navigation can turn both occupied slots into speculative work.
        // Keep a promoted current request intact; otherwise preempt the lowest-priority
        // nearby request rather than making the new current wait for its download.
        if (current in window && current !in jobs && current !in mutablePages.value && jobs.size >= 2) {
            window.lastOrNull { it in jobs && it != current }?.let { page ->
                jobs.remove(page)?.cancel()
            }
        }
        pump()
    }

    fun retry(page: Int) {
        if (closed || page !in window || jobs.containsKey(page)) return
        mutablePages.value = mutablePages.value - page
        moveTo(window.first())
    }

    private fun pump() {
        if (closed) return
        for (page in window) {
            if (jobs.size >= 2) break
            if (page in jobs || page in mutablePages.value) continue
            // One speculative load leaves capacity for a newly selected current page.
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
                    // A removed/replaced job must not remove the replacement's slot.
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

    fun close() {
        closed = true
        jobs.values.toList().forEach { it.cancel() }
        jobs.clear()
        mutablePages.value = emptyMap()
    }
}
