package com.example.komgasun.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SeriesDto(
    val id: String,
    val libraryId: String,
    val name: String,
    val booksCount: Int = 0,
    val metadata: SeriesMetadataDto = SeriesMetadataDto()
)

@Serializable
data class SeriesMetadataDto(
    val status: String = "",
    val title: String = "",
    val summary: String = "",
    val totalBookCount: Int? = null
)
