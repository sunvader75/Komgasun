package com.sunvader.komgasun.ui.reader.epub

/** Only app-generated categories and numeric measurements cross into the shareable report. */
data class EpubImageDiagnostics(
    val image: Int,
    val complete: Boolean,
    val width: Int,
    val height: Int,
    val rectWidth: Int,
    val rectHeight: Int,
    val transport: String,
    val recovery: String,
    val sourceAttribute: String = "UNKNOWN",
    val sourceSelection: String = "UNKNOWN",
    val allowedCanonicalCount: Int = 0,
    val trustedBase: String = "UNKNOWN"
) {
    private fun safe(value: String, allowed: Set<String>) = value.takeIf { it in allowed } ?: "UNKNOWN"
    fun report() = "Imagen $image: complete=$complete natural=${width}x$height rect=${rectWidth}x$rectHeight\n" +
        "transporte=$transport recuperación=$recovery categoría=${if (complete && width == 0) "DECODE_OR_LOAD" else if (width > 0) "NONE" else "PENDING"}\n" +
        "fuente=${safe(sourceAttribute, setOf("MISSING_ATTR", "EMPTY_ATTR", "ATTR"))} selección=${safe(sourceSelection, setOf("CURRENT_SRC", "ATTR"))} permitidas=${allowedCanonicalCount.coerceIn(0, 10000)} base=${safe(trustedBase, setOf("APP_CHAPTER", "DOCUMENT_FALLBACK"))}\n"
}
