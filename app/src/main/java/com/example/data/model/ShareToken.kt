package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ShareToken(
    val token: String = "",
    val hostUid: String = "",
    val hostEmail: String = "",
    val guestUid: String? = null,
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val bandwidthLimit: Long = 0L, // in MB
    val bandwidthUsed: Long = 0L, // in MB
    val status: String = "waiting" // "waiting", "connected", "expired", "completed"
)
