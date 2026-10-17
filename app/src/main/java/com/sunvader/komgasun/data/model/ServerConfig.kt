package com.sunvader.komgasun.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val serverUrl: String,
    val username: String,
    val password: String
)
