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

class SherpaSpeechManager(
    private val context: Context
) : SpeechRecognizerManager {

    private val _state = MutableStateFlow(SpeechState())
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var accumulatedText = ""
    private var lastFinalText = ""

    init {
        initModel()
    }

    private fun initModel() {
        scope.launch {
            try {
                val modelDir = File(context.filesDir, "sherpa-model")
                // Force copy to ensure latest assets are used (dev mode)
                // In production, check version or existence.
                copyAssets("sherpa-model", modelDir.absolutePath)

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
                    ), endpointConfig = EndpointConfig() // Use default
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

                            val s = stream ?: break
                            s.acceptWaveform(floatSamples, 16000)

                            while (recognizer?.isReady(s) == true) {
                                recognizer?.decode(s)
                            }

                            val result = recognizer?.getResult(s)
                            if (result != null) {
                                val text = result.text
                                if (text.isNotEmpty()) {
                                    val isEndpoint = recognizer?.isEndpoint(s) ?: false

                                    if (isEndpoint) {
                                        lastFinalText += text
                                        recognizer?.reset(s)
                                    }
                                    _state.update { it.copy(transcription = lastFinalText + text) }
                                }
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

    // Recursive asset copy
    private fun copyAssets(path: String, outPath: String) {
        val assets = context.assets.list(path) ?: return
        if (assets.isEmpty()) {
            copyFile(path, outPath)
        } else {
            val dir = File(outPath)
            if (!dir.exists()) dir.mkdirs()
            for (asset in assets) {
                copyAssets("$path/$asset", "$outPath/$asset")
            }
        }
    }

    private fun copyFile(inFilename: String, outFilename: String) {
        try {
            context.assets.open(inFilename).use { inputStream ->
                FileOutputStream(outFilename).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
