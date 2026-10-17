package com.sunvader.komgasun.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LibraryDto(
    val id: String,
    val name: String,
    val root: String = "",
    val unavailable: Boolean = false
)
