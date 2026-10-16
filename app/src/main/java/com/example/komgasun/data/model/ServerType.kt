package com.example.komgasun.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ServerType(val label: String) {
    LOCAL("Local (WiFi)"),
    REMOTE("Tailscale (Remoto)");

    companion object {
        fun fromName(name: String?): ServerType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: LOCAL
        }
    }
}
