package com.express.solvewatchgpt.speech

import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import platform.AVFAudio.*
import platform.Foundation.*
import platform.Speech.*
import platform.darwin.NSObject

class IosSpeechRecognizer : SpeechRecognizerManager {

    private val _state = MutableStateFlow(SpeechState())
    override val state: StateFlow<SpeechState> = _state.asStateFlow()



    private val speechRecognizer = SFSpeechRecognizer(NSLocale(localeIdentifier = "en-US"))
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest? = null
    private var recognitionTask: SFSpeechRecognitionTask? = null
    private val audioEngine = AVAudioEngine()
    
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun initializeModel() {
        SFSpeechRecognizer.requestAuthorization { status ->
             val isAuthorized = status == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized
             if (isAuthorized) {
                 _state.update { it.copy(isModelReady = true, error = null) }
             } else {
                 _state.update { it.copy(isModelReady = false, error = "Speech permission denied") }
             }
        }
    }

    override fun startListening() {
        if (!_state.value.isModelReady) {
             _state.update { it.copy(error = "Model (Permission) not ready") }
             return
        }
        
        // Assume already authorized if isModelReady is true, but double check in callback normally
        // For simplicity, we just flow into recording logic which is mostly separate in previous code
        // But the previous startListening had the auth check inside.
        // Let's call startRecording directly if ready, or fallback to the old block.
        // Actually, let's keep the previous valid logic but just add the check.
        
        startRecordingSafely()
    }
    
    private fun startRecordingSafely() {
         // Re-use logic from previous startListening but without the auth request wrapper as that is now in init
         // OR just call the old logic. The old logic requests auth every time which returns immediately if already granted.
         // Let's just delegate to the auth-wrapped logical block.
         SFSpeechRecognizer.requestAuthorization { status -> 
             scope.launch {
                 if (status == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) {
                      try { startRecording() } catch(e: Exception) { _state.update{ it.copy(error = e.message) } }
                 } else {
                      _state.update { it.copy(error = "Not authorized") }
                 }
             }
         }
    }

    // Track the committed text to "subtract" it from the continuous stream
    private var committedTextPrefix = ""

    @OptIn(ExperimentalForeignApi::class)
    private fun startRecording() {
        if (recognitionTask != null) {
            recognitionTask?.cancel()
            recognitionTask = null
        }
        
        // Reset prefix on new recording session
        committedTextPrefix = ""

        val audioSession = AVAudioSession.sharedInstance()
        try {
            audioSession.setCategory(AVAudioSessionCategoryRecord, mode = AVAudioSessionModeMeasurement, options = AVAudioSessionCategoryOptionDuckOthers, error = null)
            audioSession.setActive(true, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
        } catch (e: Exception) {
             _state.update { it.copy(error = "Audio session setup failed") }
             return
        }

        recognitionRequest = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
        }

        val inputNode = audioEngine.inputNode
        val request = recognitionRequest ?: return

        recognitionTask = speechRecognizer?.recognitionTaskWithRequest(request) { result, error ->
            var isFinal = false

            if (result != null) {
                val fullTranscription = result.bestTranscription.formattedString // Continuous string
                isFinal = result.isFinal()
                
                // Calculate display text by removing the committed prefix.
                // Using startsWith logic to be safe, though indices are usually stable in append-only.
                // SFSpeech might stabilize/change earlier text, so simple substring is risky but works for visual "clearing".
                // Better heuristic: if full string starts with committed prefix, strip it. 
                // If not (re-correction happened), try to find best overlap or just show full.
                
                var verificationText = fullTranscription
                if (committedTextPrefix.isNotEmpty() && verificationText.startsWith(committedTextPrefix)) {
                    verificationText = verificationText.removePrefix(committedTextPrefix).trim()
                } else if (committedTextPrefix.isNotEmpty()) {
                     // Fallback: if earlier words changed, just try to strip what we can or reset
                     // For simplicity in this "hack", we won't try complex diffing. 
                     // Usually SFSpeech stabilizes fast.
                }

                _state.update { it.copy(transcription = verificationText, isListening = !isFinal) }
            }

            if (error != null || isFinal) {
                audioEngine.stop()
                inputNode.removeTapOnBus(0u)
                recognitionRequest = null
                recognitionTask = null
                
                if (error != null) {
                     _state.update { it.copy(isListening = false, error = error.localizedDescription) }
                } else {
                     _state.update { it.copy(isListening = false) }
                }
            }
        }

        val recordingFormat = inputNode.outputFormatForBus(0u)
        
        inputNode.installTapOnBus(0u, bufferSize = 1024u, format = recordingFormat) { buffer, _ ->
            buffer?.let { request.appendAudioPCMBuffer(it) }
        }

        audioEngine.prepare()

        try {
            audioEngine.startAndReturnError(null)
            _state.update { it.copy(isListening = true, error = null) }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Audio engine failed to start") }
        }
    }

    override fun stopListening() {
        if (audioEngine.isRunning()) {
            audioEngine.stop()
            recognitionRequest?.endAudio()
            _state.update { it.copy(isListening = false, transcription = "") }
        }
    }
    
    override fun clearTranscription() {
        // Instead of restarting, we just commit the current full text as a "prefix" to hide.
        // We need to capture the *current full transcription* from the running task somehow.
        // Since we don't store it in a var, we can deduce it: 
        // displayedText = full - committed
        // => full = committed + displayedText
        // newCommitted = full
        
        val currentDisplayed = _state.value.transcription
        if (currentDisplayed.isNotEmpty()) {
            val space = if (committedTextPrefix.isNotEmpty()) " " else ""
            committedTextPrefix += space + currentDisplayed
            // Remove double spaces if any
             committedTextPrefix = committedTextPrefix.trim()
        }
        
        _state.update { it.copy(transcription = "") }
    }
}
