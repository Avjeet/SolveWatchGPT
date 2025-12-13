package com.express.solvewatchgpt.speech

import kotlinx.coroutines.flow.StateFlow

interface SpeechRecognizerManager {
    val state: StateFlow<SpeechState>
    fun startListening()
    fun stopListening()
}

data class SpeechState(
    val transcription: String = "",
    val isListening: Boolean = false,
    val error: String? = null
)
