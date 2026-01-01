package com.express.solvewatchgpt.speech.sherpa

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.express.solvewatchgpt.speech.SpeechRecognizerManager
import com.express.solvewatchgpt.speech.SpeechState
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


class SherpaSpeechManager(
    private val context: Context
) : SpeechRecognizerManager {

    private val _state = MutableStateFlow(SpeechState())
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var committedText = ""
    private var resetRequested = false


    private var recognizer: OfflineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null

    // Buffer to hold audio for the entire utterance for Whisper
    private val audioBuffer = ArrayList<Float>()

    override fun initializeModel() {
        if (recognizer != null) {
            _state.update { it.copy(isModelReady = true, statusMessage = "Model Ready") }
            return
        }

        scope.launch {
            try {
                _state.update { it.copy(statusMessage = "Checking local files...") }

                val modelDir = File(context.filesDir, "sherpa-onnx-whisper-base.en")
                val encoderFile = File(modelDir, "base.en-encoder.int8.onnx")
                val tokensFile = File(modelDir, "base.en-tokens.txt")
                
                // If the folder exists but key files are missing, force re-download
                if (!modelDir.exists() || !encoderFile.exists() || !tokensFile.exists()) {
                    if (modelDir.exists()) modelDir.deleteRecursively()

                    _state.update {
                        it.copy(
                            isDownloading = true,
                            error = null,
                            statusMessage = "Downloading Whisper Base (~75MB)..."
                        )
                    }
                    delay(500)

                    // Official K2-FSA Whisper Base Release
                    val modelUrl =
                        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.en.tar.bz2"
                    val tarFile = File(context.filesDir, "whisper-base.tar.bz2")

                    try {
                        downloadFile(modelUrl, tarFile)

                        _state.update { it.copy(statusMessage = "Extracting files...") }
                        delay(500)

                        unTarBz2(tarFile, context.filesDir)
                        tarFile.delete()
                        _state.update { it.copy(isDownloading = false) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _state.update {
                            it.copy(
                                error = "Model download failed: ${e.message}",
                                isDownloading = false,
                                statusMessage = null
                            )
                        }
                        return@launch
                    }
                }

                _state.update { it.copy(statusMessage = "Initializing Whisper Engine...") }
                delay(500)

                val config = OfflineRecognizerConfig(
                    featConfig = com.k2fsa.sherpa.onnx.FeatureConfig(
                        sampleRate = 16000, featureDim = 80
                    ),
                    modelConfig = OfflineModelConfig(
                        tokens = File(modelDir, "base.en-tokens.txt").absolutePath,
                        whisper = OfflineWhisperModelConfig(
                            encoder = File(modelDir, "base.en-encoder.int8.onnx").absolutePath,
                            decoder = File(modelDir, "base.en-decoder.int8.onnx").absolutePath
                        ),
                        numThreads = 1,
                        debug = true
                    )
                )

                recognizer = OfflineRecognizer(
                    assetManager = null, config = config
                )

                println("Sherpa-ONNX Whisper Initialized Successfully")
                _state.update {
                    it.copy(
                        isModelReady = true,
                        error = null,
                        statusMessage = "Ready to Listen"
                    )
                }
                delay(500)
                _state.update { it.copy(statusMessage = null) } // Clear message after success
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update {
                    it.copy(
                        error = "Sherpa Init Error: ${e.message}",
                        statusMessage = null
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startListening() {
        if (recognizer == null) {
            _state.update { it.copy(error = "Whisper model not loaded yet.") }
            return
        }
        if (isRecording) return

        // Reset state (keep transcription for append)
        audioBuffer.clear()
        _state.update { it.copy(isListening = true, error = null) }

        _state.update { it.copy(isListening = true, error = null) }

        // Initialize committedText with whatever is currently displayed (or start fresh if that's desired behavior)
        committedText = _state.value.transcription

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

            // Create Stream for this session
            var stream = recognizer?.createStream()

            recordingJob = scope.launch(Dispatchers.IO) {
                try {
                    val shortBuffer = ShortArray(bufferSize / 2)
                    var lastEmittedText = ""
                    var lastProcessedIndex = 0

                    // Smart Decoding State
                    var timeSinceLastDecodeMs = 0L
                    var silenceDurationMs = 0L
                    val silenceThresholdRms = 0.02 // Sensitivity
                    val maxLatencyMs = 3000L      // Force decode every 3s
                    val minSilenceDurationMs = 500L // Wait for 0.5s pause
                    val minIntervalMs = 1000L     // Don't decode more often than 1s

                    while (isActive && isRecording) {
                        val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                        if (read > 0) {
                            val floatSamples = FloatArray(read)
                            for (i in 0 until read) {
                                floatSamples[i] = shortBuffer[i] / 32768.0f
                            }

                            val chunkDurationMs = (read.toDouble() / sampleRate * 1000).toLong()
                            timeSinceLastDecodeMs += chunkDurationMs

                            // 1. Calculate Visuals & Silence
                            var sumSq = 0.0
                            for (sample in floatSamples) {
                                sumSq += sample * sample
                            }
                            val rms = kotlin.math.sqrt(sumSq / floatSamples.size)
                            val visualLevel = (rms * 10).coerceIn(0.0, 1.0).toFloat()

                            _state.update { it.copy(audioLevel = visualLevel) }

                            if (rms < silenceThresholdRms) {
                                silenceDurationMs += chunkDurationMs
                            } else {
                                silenceDurationMs = 0 // Reset on speech
                            }

                            // 2. Feed to Whisper Stream
                            if (stream != null) {
                                stream?.acceptWaveform(floatSamples, sampleRate)

                                // Smart Trigger Logic
                                val isPauseDetected =
                                    silenceDurationMs >= minSilenceDurationMs && timeSinceLastDecodeMs >= minIntervalMs
                                val isBufferFull = timeSinceLastDecodeMs >= maxLatencyMs

                                if ((isPauseDetected || isBufferFull) && !resetRequested) {
                                    recognizer?.decode(stream!!)
                                    val result = recognizer?.getResult(stream!!)
                                    val text = result?.text ?: ""

                                    // Filter out noise tokens like [Buzzing], [static], (Video)
                                    val sanitizedText = text.replace(Regex("\\[.*?\\]"), "")
                                                            .replace(Regex("\\(.*?\\)"), "")
                                                            .trim()

                                    if (sanitizedText.isNotBlank()) {
                                        val fullText = if (committedText.isBlank()) sanitizedText else "$committedText $sanitizedText"
                                        _state.update { it.copy(transcription = fullText) }
                                    }


                                    timeSinceLastDecodeMs = 0 // Reset timer
                                }

                                if (resetRequested) {
                                    stream?.release()
                                    stream = recognizer?.createStream()
                                    // committedText is already reset by the function
                                    resetRequested = false
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _state.update { it.copy(error = "Streaming Error: ${e.message}") }
                } finally {
                    try {
                        // Final decode to capture last bit
                        if (stream != null) {
                            recognizer?.decode(stream!!)
                            val result = recognizer?.getResult(stream!!)
                            val text = result?.text ?: ""
                            
                            val sanitizedText = text.replace(Regex("\\[.*?\\]"), "")
                                                    .replace(Regex("\\(.*?\\)"), "")
                                                    .trim()

                            if (sanitizedText.isNotBlank()) {
                                val fullText = if (committedText.isBlank()) sanitizedText else "$committedText $sanitizedText"
                                _state.update { it.copy(transcription = fullText) }
                            }
                        }

                        stream?.release()
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
        if (!isRecording) return
        isRecording = false
        recordingJob?.cancel()
        committedText = ""
        _state.update { it.copy(isListening = false, audioLevel = 0f, transcription = "") }
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

    private fun unTarBz2(tarFile: File, targetDirectory: File) {
        val fileInputStream = FileInputStream(tarFile)
        val bufferedInputStream = BufferedInputStream(fileInputStream)
        val bzip2InputStream = BZip2CompressorInputStream(bufferedInputStream)
        val tarInputStream = TarArchiveInputStream(bzip2InputStream)

        var entry = tarInputStream.nextEntry
        while (entry != null) {
            val outputFile = File(targetDirectory, entry.name)
            if (entry.isDirectory) {
                outputFile.mkdirs()
            } else {
                outputFile.parentFile?.mkdirs()
                val outputStream = FileOutputStream(outputFile)
                tarInputStream.copyTo(outputStream)
                outputStream.close()
            }
            entry = tarInputStream.nextEntry
        }
        tarInputStream.close()
    }
    override fun clearTranscription() {
        committedText = ""
        _state.update { it.copy(transcription = "") }
        resetRequested = true
    }
}
