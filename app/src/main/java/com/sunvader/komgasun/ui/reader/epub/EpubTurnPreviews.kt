package com.sunvader.komgasun.ui.reader.epub

/** UI-thread confined, two-neighbour cache with one auxiliary renderer in flight. */
internal class EpubTurnPreviews<T>(private val load: (Int, (T) -> Unit) -> Unit) {
    private var key: Any? = null
    private var directions = emptyList<Int>()
    private val values = mutableMapOf<Int, T>()
    private var serial = 0L
    private var active: Int? = null

    fun update(key: Any?, allowedDirections: List<Int>) {
        val allowed = allowedDirections.filter { it == -1 || it == 1 }.distinct()
        if (this.key == key && directions.toSet() == allowed.toSet()) return
        this.key = key
        directions = if (key == null) emptyList() else allowed
        values.clear()
        active = null
        ++serial
        pump()
    }

    operator fun get(direction: Int): T? = values[direction]

    fun prefer(direction: Int) {
        if (direction !in directions) return
        directions = listOf(direction) + directions.filter { it != direction }
        pump()
    }

    /** Invalidate the timed-out callback; a subsequent request can retry. */
    fun failed() { ++serial; active = null }

    private fun pump() {
        if (active != null) return
        val direction = directions.firstOrNull { it !in values } ?: return
        active = direction
        val ticket = ++serial
        load(direction) { value ->
            if (serial == ticket && active == direction) {
                values[direction] = value
                active = null
                pump()
            }
        }
    }
}
