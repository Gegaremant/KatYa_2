package com.katya.app.data

import kotlinx.serialization.Serializable

@Serializable
data class LocalServerProfile(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int = 22,
    val user: String = "",
    val password: String = "",
    val localTunnelPort: Int = 11434,
    val remoteTunnelPort: Int = 11434
)

@Serializable
data class VlessProxyProfile(
    val id: String,
    val name: String,
    val uri: String
)
