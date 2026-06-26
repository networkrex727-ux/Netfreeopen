package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ProxyRequest(
    val id: String,
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null // Base64 encoded body for POST/PUT etc.
)
