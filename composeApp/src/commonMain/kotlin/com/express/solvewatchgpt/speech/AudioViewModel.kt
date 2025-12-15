package com.express.solvewatchgpt.speech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AudioViewModel : ViewModel(), KoinComponent {

    private val speechManager: SpeechRecognizerManager by inject()

    val state: StateFlow<SpeechState> = speechManager.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SpeechState()
    )

    fun initializeModel() {
        speechManager.initializeModel()
    }

    fun startListening() {
        speechManager.startListening()
    }

    fun stopListening() {
        speechManager.stopListening()
    }
}
