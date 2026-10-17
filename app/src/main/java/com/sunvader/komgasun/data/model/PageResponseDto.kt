package com.sunvader.komgasun.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PageResponseDto<T>(
    val content: List<T> = emptyList(),
    val empty: Boolean = true,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val number: Int = 0,
    val size: Int = 0
)
