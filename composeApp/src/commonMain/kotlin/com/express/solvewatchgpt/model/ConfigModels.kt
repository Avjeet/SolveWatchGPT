package com.express.solvewatchgpt.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiConfig(
    val keys: Map<String, String>,
    val order: List<String>,
    val enabled: List<String> = emptyList()
)

@Serializable
data class ConfigResponse(
    val success: Boolean,
    val config: ApiConfig? = null,
    val message: String? = null,
    val error: String? = null
)
