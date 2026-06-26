package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ProxyResponse(
    val id: String,
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val data: String = "", // Base64 encoded response body
    val error: String = ""
)
