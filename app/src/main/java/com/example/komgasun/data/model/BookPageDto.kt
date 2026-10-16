package com.example.komgasun.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BookPageDto(
    val number: Int,
    val fileName: String = "",
    val mediaType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0
)
