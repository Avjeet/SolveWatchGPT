package com.express.solvewatchgpt.speech

import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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

    @OptIn(ExperimentalForeignApi::class)
    private fun startRecording() {
        if (recognitionTask != null) {
            recognitionTask?.cancel()
            recognitionTask = null
        }

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
                val transcription = result.bestTranscription.formattedString
                isFinal = result.isFinal()
                _state.update { it.copy(transcription = transcription, isListening = !isFinal) }
                

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
            _state.update { it.copy(isListening = false) }
        }
    }
}
