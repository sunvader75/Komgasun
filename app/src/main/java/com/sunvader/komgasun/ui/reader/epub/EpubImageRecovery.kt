package com.sunvader.komgasun.ui.reader.epub

/** One generation, at most 256 image attempts and 16 MiB retained raster source bytes. */
class EpubImageRecovery(private val generation: Long) {
    private val attempted = mutableSetOf<Int>()
    private var bytes = 0
    fun claim(token: Long, image: Int): Boolean =
        token == generation && image in 0..255 && attempted.add(image)

    fun reserve(token: Long, size: Int): Boolean {
        if (token != generation || size !in 1..8 * 1024 * 1024 || size > 16 * 1024 * 1024 - bytes) return false
        bytes += size
        return true
    }

    companion object {
        fun validRaster(mime: String, data: ByteArray): Boolean {
            fun prefix(vararg values: Int) = data.size >= values.size && values.indices.all { (data[it].toInt() and 255) == values[it] }
            fun text(offset: Int, value: String) = data.size >= offset + value.length && value.indices.all { data[offset + it].toInt() == value[it].code }
            return when (mime) {
                "image/png" -> prefix(137, 80, 78, 71, 13, 10, 26, 10)
                "image/jpeg" -> prefix(255, 216, 255)
                "image/gif" -> text(0, "GIF87a") || text(0, "GIF89a")
                "image/webp" -> text(0, "RIFF") && text(8, "WEBP")
                else -> false
            }
        }
    }
}
