package com.example.komgasun.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val email: String = "",
    val roles: List<String> = emptyList(),
    val sharedAllLibraries: Boolean = false
)
