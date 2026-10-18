package com.sunvader.komgasun.data.model

import kotlinx.serialization.Serializable

/** Last reader-known whole-book progress. totalPages belongs to the last measured layout. */
@Serializable
data class EpubCoverProgress(
    val fraction: Double,
    val totalPages: Int? = null
) {
    val isValid: Boolean get() = fraction.isFinite() && fraction in 0.0..1.0 &&
        (totalPages == null || totalPages in 1..1_000_000_000)

    companion object {
        fun fromRemote(progression: EpubProgressionDto?): EpubCoverProgress? =
            progression?.locator?.locations?.totalProgression?.let { EpubCoverProgress(it) }?.takeIf { it.isValid }
    }
}
