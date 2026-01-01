package com.express.solvewatchgpt.speech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

import com.express.solvewatchgpt.network.DataSocketClient
import com.express.solvewatchgpt.model.ApiConfig
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AudioViewModel : ViewModel(), KoinComponent {

    private val speechManager: SpeechRecognizerManager by inject()
    private val dataSocketClient: DataSocketClient by inject()

    val state: StateFlow<SpeechState> = speechManager.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SpeechState()
    )

    init {
        // No automatic collection of chunks or silence trigger anymore.
        // User manually triggers processing.
    }

    fun initializeModel() {
        speechManager.initializeModel()
    }

    fun startListening() {
        speechManager.startListening()
    }

    fun stopListening() {
        speechManager.stopListening()
        // Stop listening no longer triggers processing automatically
    }

    fun triggerManualProcessing() {
        viewModelScope.launch {
            if (dataSocketClient.isConnected.value && state.value.transcription.isNotBlank()) {
                val textToProcess = state.value.transcription
                println("AudioViewModel: Manually triggering processing for text: '$textToProcess'")
                
                // 1. Send the full accumulated text as one chunk
                dataSocketClient.sendTranscriptionChunk(textToProcess)
                
                // 2. Clear local buffer immediately so user can continue speaking
                speechManager.clearTranscription()
                
                // 3. Trigger processing on server
                dataSocketClient.processTranscription()
            }
        }
    }
}
