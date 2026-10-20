package com.sunvader.komgasun.data.model

import kotlinx.serialization.Serializable

/** Posiciones definidas por el servidor, no páginas físicas dependientes del área de lectura. */
@Serializable
data class EpubPositionsDto(val total: Int, val positions: List<EpubLocatorDto>)

/** Progreso de la API R2 de Komga, distinto del DTO de páginas de Divina /read-progress. */
@Serializable
data class EpubProgressionDto(val modified: String, val device: EpubDeviceDto, val locator: EpubLocatorDto)

@Serializable
data class EpubDeviceDto(val id: String, val name: String)

@Serializable
data class EpubLocatorDto(val href: String, val type: String, val locations: EpubLocationsDto? = null)

@Serializable
data class EpubLocationsDto(val progression: Double? = null, val totalProgression: Double? = null, val position: Int? = null)
