package com.express.solvewatchgpt.model

import kotlinx.serialization.Serializable

@Serializable
data class Answer(
    val id: String = "",
    val question: String,
    val answer: String,
    val timestamp: Long,
    val type: String = "question" // "question" or "image"
)
