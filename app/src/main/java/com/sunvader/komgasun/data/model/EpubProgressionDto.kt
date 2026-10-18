package com.sunvader.komgasun.data.model

import kotlinx.serialization.Serializable

/** Komga's R2 progression API, not the Divina /read-progress page DTO. */
@Serializable
data class EpubProgressionDto(val modified: String, val device: EpubDeviceDto, val locator: EpubLocatorDto)

@Serializable
data class EpubDeviceDto(val id: String, val name: String)

@Serializable
data class EpubLocatorDto(val href: String, val type: String, val locations: EpubLocationsDto? = null)

@Serializable
data class EpubLocationsDto(val progression: Double? = null, val totalProgression: Double? = null, val position: Int? = null)
