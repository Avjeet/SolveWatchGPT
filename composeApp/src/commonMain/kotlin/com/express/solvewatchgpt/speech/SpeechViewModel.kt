package com.express.solvewatchgpt.speech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SpeechViewModel : ViewModel(), KoinComponent {

    private val speechManager: SpeechRecognizerManager by inject()
    
    val state: StateFlow<SpeechState> = speechManager.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SpeechState()
        )

    init {
        viewModelScope.launch {
            speechManager.state
                .map { it.transcription }
                .distinctUntilChanged()
                .collect { text ->
                    if (text.isNotBlank()) {
                        println("🎙️ TRANSCRIPTION: $text")
                    }
                }
        }
    }

    fun startListening() {
        speechManager.startListening()
    }

    fun stopListening() {
        speechManager.stopListening()
    }
}
