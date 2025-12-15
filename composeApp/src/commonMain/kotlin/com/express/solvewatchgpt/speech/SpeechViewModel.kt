package com.express.solvewatchgpt.speech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.express.solvewatchgpt.model.Answer
import com.express.solvewatchgpt.network.DataSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class MainScreenState(
    val speech: SpeechState = SpeechState(),
    val answers: List<Answer> = emptyList(),
    val expandedAnswerId: String? = null,
    val isSocketConnected: Boolean = false,
    val snackbarMessage: String? = null
)

class SpeechViewModel : ViewModel(), KoinComponent {

    private val speechManager: SpeechRecognizerManager by inject()
    private val dataSocketClient: DataSocketClient by inject()

    private val _answers = MutableStateFlow<List<Answer>>(emptyList())
    private val _expandedAnswerId = MutableStateFlow<String?>(null)
    private val _snackbarMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<MainScreenState> = combine(
        speechManager.state,
        _answers,
        _expandedAnswerId,
        dataSocketClient.isConnected,
        _snackbarMessage
    ) { speech, answers, expandedId, isConnected, snackbarMsg ->
        MainScreenState(speech, answers, expandedId, isConnected, snackbarMsg)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainScreenState()
    )

    init {
        // Collect new answers
        viewModelScope.launch {
            dataSocketClient.answers.collect { answer ->
                _answers.update { currentList ->
                    listOf(answer) + currentList
                }
                // Auto expand the new answer
                _expandedAnswerId.value = answer.id
            }
        }

        // Collect status messages
        viewModelScope.launch {
            dataSocketClient.statusMessages.collect { msg ->
                _snackbarMessage.value = msg
            }
        }
    }
    
    fun toggleSocketConnection() {
        if (state.value.isSocketConnected) {
            dataSocketClient.disconnect()
        } else {
            viewModelScope.launch {
                try {
                    dataSocketClient.connect("192.168.0.106", 4000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun initializeModel() {
        speechManager.initializeModel()
    }

    fun startListening() {
        speechManager.startListening()
    }

    fun stopListening() {
        speechManager.stopListening()
    }

    fun toggleAnswer(id: String) {
        _expandedAnswerId.update { current ->
            if (current == id) null else id
        }
    }

    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }
}

