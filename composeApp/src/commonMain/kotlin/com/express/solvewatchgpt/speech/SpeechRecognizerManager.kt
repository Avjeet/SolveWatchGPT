package com.express.solvewatchgpt.speech

import kotlinx.coroutines.flow.StateFlow

interface SpeechRecognizerManager {
    val state: StateFlow<SpeechState>
    fun clearTranscription()
    fun initializeModel()
    fun startListening()
    fun stopListening()
}

data class SpeechState(
    val transcription: String = "",
    val isListening: Boolean = false,
    val error: String? = null,
    val isDownloading: Boolean = false,
    val isModelReady: Boolean = false,
    val statusMessage: String? = null,
    val audioLevel: Float = 0f // Normalized 0..1
)
