package com.sunvader.komgasun.data.model

import kotlinx.serialization.Serializable

/** Último progreso global conocido por el lector; totalPages corresponde a la última maquetación medida. */
@Serializable
data class EpubCoverProgress(
    val fraction: Double,
    val totalPages: Int? = null
) {
    val isValid: Boolean get() = fraction.isFinite() && fraction in 0.0..1.0 &&
        (totalPages == null || totalPages in 1..1_000_000_000)

    companion object {
        /** Extrae solo totalProgression, sin inventar un total de páginas; devuelve `null` si falta o no es válido. */
        fun fromRemote(progression: EpubProgressionDto?): EpubCoverProgress? =
            progression?.locator?.locations?.totalProgression?.let { EpubCoverProgress(it) }?.takeIf { it.isValid }
    }
}
