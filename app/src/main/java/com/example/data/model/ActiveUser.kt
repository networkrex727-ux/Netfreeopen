package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ActiveUser(
    val uid: String = "",
    val email: String = "",
    val status: String = "offline", // "online", "offline", "logged_out"
    val lastActive: Long = 0L,
    val deviceInfo: String = "",
    val role: String = "idle" // "idle", "hosting", "using"
)
