package com.express.solvewatchgpt.speech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.express.solvewatchgpt.model.Answer
import com.express.solvewatchgpt.model.ApiConfig
import com.express.solvewatchgpt.network.ConfigRepository
import com.express.solvewatchgpt.network.DataSocketClient
import com.express.solvewatchgpt.config.AppConfig
import kotlinx.datetime.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long
)

data class MainScreenState(
    val speech: SpeechState = SpeechState(),
    val messages: List<ChatMessage> = emptyList(),
    val isSocketConnected: Boolean = false,
    val snackbarMessage: String? = null,
    val selectedMessageId: String? = null,
    val isOptionsDialogOpen: Boolean = false
)

class SpeechViewModel : ViewModel(), KoinComponent {

    private val speechManager: SpeechRecognizerManager by inject()
    private val dataSocketClient: DataSocketClient by inject()
    private val configRepository: ConfigRepository by inject()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _selectedMessageId = MutableStateFlow<String?>(null)
    private val _isOptionsDialogOpen = MutableStateFlow(false)
    
    // Separate state for settings
    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()
    
    private val _config = MutableStateFlow<ApiConfig?>(null)
    val config: StateFlow<ApiConfig?> = _config.asStateFlow()

    val state: StateFlow<MainScreenState> = combine(
        speechManager.state,
        _messages,
        dataSocketClient.isConnected,
        _snackbarMessage,
        _selectedMessageId,
        _isOptionsDialogOpen
    ) { args ->
        val speech = args[0] as SpeechState
        val messages = args[1] as List<ChatMessage>
        val isConnected = args[2] as Boolean
        val snackbarMsg = args[3] as String?
        val selectedId = args[4] as String?
        val isDialogOpen = args[5] as Boolean

        MainScreenState(speech, messages, isConnected, snackbarMsg, selectedId, isDialogOpen)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainScreenState()
    )

    init {
        // Initialize Speech Model automatically on startup
        speechManager.initializeModel()

        // Collect new answers
        viewModelScope.launch {
            dataSocketClient.answers.collect { answer ->
                val aiMessage = ChatMessage(
                    id = answer.id,
                    text = answer.answer,
                    isUser = false,
                    timestamp = answer.timestamp
                )
                _messages.update { currentList ->
                    currentList + aiMessage
                }
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
                    dataSocketClient.connect(AppConfig.HOST, AppConfig.PORT) {
                        _snackbarMessage.value = it
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _snackbarMessage.value = "Socket connection refused"
                }
            }
        }
    }

    fun openSettings() {
        // Fetch updates in background
        viewModelScope.launch {
            _snackbarMessage.value = "Fetching configuration..."
            try {
                val response = configRepository.fetchConfig()
                if (response != null && response.success) {
                    if (response.config != null) {
                        _config.value = response.config
                    }
                    _snackbarMessage.value = null
                    _isSettingsOpen.value = true
                } else {
                     _snackbarMessage.value = response?.error ?: "Failed to update config from server."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _snackbarMessage.value = "Network error fetching config."
            }
        }
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun saveSettings(newConfig: ApiConfig) {
        viewModelScope.launch {
            _snackbarMessage.value = "Saving..."
            val response = configRepository.saveConfig(newConfig)
            if (response != null && response.success) {
                _config.value = response.config
                _isSettingsOpen.value = false
                _snackbarMessage.value = "Configuration saved!"
            } else {
                _snackbarMessage.value = response?.error ?: "Failed to save"
            }
        }
    }





    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }
    
    // --- Audio Control Methods (Moved from AudioViewModel) ---

    fun startListening() {
        speechManager.startListening()
    }

    fun stopListening() {
        speechManager.stopListening()
    }
    
    fun triggerManualProcessing() {
        viewModelScope.launch {
            val currentTranscription = speechManager.state.value.transcription
            if (dataSocketClient.isConnected.value && currentTranscription.isNotBlank()) {
                val textToProcess = currentTranscription
                println("SpeechViewModel: Manually triggering processing for text: '$textToProcess'")
                
                // Add user message locally immediately
                val userMessage = ChatMessage(
                    id = "msg_" + (0..100000).random().toString(), // Simple random ID
                    text = textToProcess,
                    isUser = true,
                    timestamp = 0L // Placeholder
                )
                _messages.update { it + userMessage }

                // 1. Send the full accumulated text as one chunk
                dataSocketClient.sendTranscriptionChunk(textToProcess)
                
                // 2. Clear local buffer immediately so user can continue speaking
                speechManager.clearTranscription()
                
                // 3. Trigger processing on server
                dataSocketClient.processTranscription()
            }
        }
    }
    
    fun clearTranscription() {
        speechManager.clearTranscription()
    }

    fun openMessageOptions(messageId: String) {
        _selectedMessageId.value = messageId
        _isOptionsDialogOpen.value = true
    }

    fun closeMessageOptions() {
        _isOptionsDialogOpen.value = false
        _selectedMessageId.value = null
    }

    fun sendPromptOption(promptType: String) {
        val messageId = _selectedMessageId.value
        if (messageId != null && dataSocketClient.isConnected.value) {
            viewModelScope.launch {
                // Determine if screenshot is required based on prompt type (as per requirements)
                // debug - ss required
                // theory - ss not required
                // coding - ss not required
                val screenshotRequired = promptType == "debug"
                
                // Add Chat Message for UI feedback
                val commandText = "${promptType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} ${if (screenshotRequired) "(Snapshot)" else ""}"
                
                val userMessage = ChatMessage(
                    id = "cmd_" + (0..100000).random().toString(),
                    text = commandText,
                    isUser = true,
                    timestamp = 0L
                )
                _messages.update { it + userMessage }

                // Emit Socket Event
                dataSocketClient.emitUsePrompt(promptType, messageId, screenshotRequired)
                
                closeMessageOptions()
            }
        } else {
            closeMessageOptions()
            _snackbarMessage.value = "Cannot send command (Disconnected or No Message ID)"
        }
    }
}

