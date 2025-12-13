package com.express.solvewatchgpt.speech.sherpa

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.express.solvewatchgpt.speech.SpeechRecognizerManager
import com.express.solvewatchgpt.speech.SpeechState
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

import com.express.solvewatchgpt.network.SpeechSocketClient

class SherpaSpeechManager(
    private val context: Context,
    private val socketClient: SpeechSocketClient
) : SpeechRecognizerManager {

    private val _state = MutableStateFlow(SpeechState())
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var lastFinalText = ""

    init {
        initModel()
    }

    private fun initModel() {
        scope.launch {
            try {
                val modelDir = File(context.filesDir, "sherpa-model")
                if (!modelDir.exists()) {
                    _state.update { it.copy(isDownloading = true, error = null) }
                    // GitHub Release Asset Link
                    val modelUrl = "https://github.com/Avjeet/SolveWatchGPT/releases/download/sherpa-model-release/sherpa-model.zip"
                    
                    val zipFile = File(context.filesDir, "sherpa-model.zip")
                    
                    try {
                        downloadFile(modelUrl, zipFile)
                        unzip(zipFile, context.filesDir) // Unzips to filesDir
                        zipFile.delete() 
                        _state.update { it.copy(isDownloading = false) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _state.update { it.copy(error = "Model download failed. Check internet.", isDownloading = false) }
                        return@launch
                    }
                }



                val config = OnlineRecognizerConfig(
                    featConfig = com.k2fsa.sherpa.onnx.FeatureConfig(
                        sampleRate = 16000, featureDim = 80
                    ), modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = File(
                                modelDir, "encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
                            ).absolutePath, decoder = File(
                                modelDir, "decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
                            ).absolutePath, joiner = File(
                                modelDir, "joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
                            ).absolutePath
                        ),
                        tokens = File(modelDir, "tokens.txt").absolutePath,
                        numThreads = 1,
                        debug = true
                    ),
                    endpointConfig = EndpointConfig()
                )

                recognizer = OnlineRecognizer(
                    assetManager = null, config = config
                )

                println("Sherpa-ONNX Initialized Successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update { it.copy(error = "Sherpa Init Error: ${e.message}") }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startListening() {
        if (recognizer == null) {
            _state.update { it.copy(error = "Sherpa model not loaded yet.") }
            return
        }
        if (isRecording) return

        try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufferSize, 4096)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize
            )

            audioRecord?.startRecording()
            isRecording = true
            _state.update { it.copy(isListening = true, error = null) }

            // Create New Stream
            if (stream != null) {
                stream?.release()
                stream = null
            }
            stream = recognizer?.createStream()

            // Connect Socket (Fire and Forget) and listen for responses
            scope.launch {
                try {
                    // Use local network IP for physical device (from logs)
                    socketClient.connect("192.168.0.106", 4000)
                } catch (e: Exception) {
                   e.printStackTrace()
                }
            }
            
            // Listen for AI responses
            scope.launch {
                 socketClient.transcriptions.collect { aiText ->
                     println("SOCKET_RECEIVED: $aiText")
                 }
            }

            recordingJob = scope.launch(Dispatchers.IO) {
                try {
                    val shortBuffer = ShortArray(bufferSize / 2)

                    while (isActive && isRecording) {
                        val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                        if (read > 0) {
                            val floatSamples = FloatArray(read)
                            for (i in 0 until read) {
                                floatSamples[i] = shortBuffer[i] / 32768.0f
                            }

                            // Calculate Audio Level (RMS) for Visualizer
                            var sumSq = 0.0
                            for (sample in floatSamples) {
                                sumSq += sample * sample
                            }
                            val rms = kotlin.math.sqrt(sumSq / floatSamples.size)
                            // Boost it a bit for visibility
                            val visualLevel = (rms * 10).coerceIn(0.0, 1.0).toFloat()

                            val s = stream ?: break
                            s.acceptWaveform(floatSamples, 16000)

                            while (recognizer?.isReady(s) == true) {
                                recognizer?.decode(s)
                            }

                            val result = recognizer?.getResult(s)
                            var currentText = ""
                            if (result != null) {
                                val text = result.text
                                if (text.isNotEmpty()) {
                                    val isEndpoint = recognizer?.isEndpoint(s) ?: false
                                    if (isEndpoint) {
                                        val textToSend = text
                                        // SEND FINAL TEXT CHUNK TO BACKEND
                                        socketClient.sendTextChunk(textToSend)
                                        
                                        recognizer?.reset(s)
                                    }
                                    currentText = lastFinalText + text
                                }
                            }
                            
                            // Update State with level and text
                            _state.update { 
                                it.copy(
                                    transcription = if (currentText.isNotEmpty()) currentText else it.transcription,
                                    audioLevel = visualLevel
                                ) 
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        stream?.release()
                        stream = null
                        audioRecord?.stop()
                        audioRecord?.release()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    audioRecord = null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _state.update { it.copy(error = "Mic Error: ${e.message}") }
            isRecording = false
        }
    }

    override fun stopListening() {
        isRecording = false
        recordingJob?.cancel()
        // Resources are cleaned up in the job's finally block
        _state.update { it.copy(isListening = false) }
    }



    private fun downloadFile(url: String, destFile: File) {
        try {
            val connection = java.net.URL(url).openConnection()
            connection.connect()
            connection.getInputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
    
    // Simple Zip extraction (assumes .zip, not .tar.bz2)
    private fun unzip(zipFile: File, targetDirectory: File) {
        java.util.zip.ZipInputStream(java.io.FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDirectory, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                entry = zis.nextEntry
            }
        }
    }
}
