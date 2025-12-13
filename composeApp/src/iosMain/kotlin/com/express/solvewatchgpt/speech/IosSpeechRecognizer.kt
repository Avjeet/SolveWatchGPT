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

class IosSpeechRecognizer(
    private val socketClient: com.express.solvewatchgpt.network.SpeechSocketClient
) : SpeechRecognizerManager {

    private val _state = MutableStateFlow(SpeechState())
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val speechRecognizer = SFSpeechRecognizer(NSLocale(localeIdentifier = "en-US"))
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest? = null
    private var recognitionTask: SFSpeechRecognitionTask? = null
    private val audioEngine = AVAudioEngine()
    
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun startListening() {
        SFSpeechRecognizer.requestAuthorization { status ->
             scope.launch {
                 when (status) {
                     SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized -> {
                         try {
                             // Connect socket on start just in case (optional, but good for connection check)
                             launch(Dispatchers.Default) {
                                 try {
                                     socketClient.connect("192.168.0.106", 4000)
                                 } catch (e: Exception) {
                                     e.printStackTrace()
                                 }
                             }
                             startRecording()
                         } catch (e: Exception) {
                             _state.update { it.copy(error = "Recording failed: ${e.message}") }
                         }
                     }
                     else -> {
                         _state.update { it.copy(error = "Speech recognition not authorized") }
                     }
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
                
                // If it is final, send to socket
                if (isFinal && transcription.isNotBlank()) {
                     scope.launch(Dispatchers.Default) {
                         socketClient.sendTextChunk(transcription)
                     }
                }
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
