package com.express.solvewatchgpt.network

import com.express.solvewatchgpt.model.Answer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.util.date.getTimeMillis
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class DataSocketClient(private val client: HttpClient) {

    private var session: DefaultClientWebSocketSession? = null
    private val _answers = MutableSharedFlow<Answer>()
    val answers: SharedFlow<Answer> = _answers.asSharedFlow()

    private val _statusMessages = MutableSharedFlow<String>()
    val statusMessages: SharedFlow<String> = _statusMessages.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Namespace for Data Updates
    private val NAMESPACE = "/data-updates"

    private val _isConnected = MutableSharedFlow<Boolean>(replay = 1)
    val isConnected: SharedFlow<Boolean> = _isConnected.asSharedFlow()

    suspend fun connect(host: String, port: Int) {
        val urlString = "ws://$host:$port/socket.io/?EIO=4&transport=websocket"
        println("DataSocketClient: Connecting to $urlString")

        try {
            client.webSocket(urlString) {
                session = this
                _isConnected.emit(true)
                // 1. Handshake/Open is handled by Socket.IO on connection.
                // 2. Connect to Namespace
                sendPacket("40$NAMESPACE,")

                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            handleMessage(text)
                        }
                    }
                } catch (e: Exception) {
                    println("DataSocketClient Error: ${e.message}")
                } finally {
                    _isConnected.emit(false)
                    session = null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _isConnected.emit(false)
        }
    }

    private suspend fun handleMessage(text: String) {
        if (text.startsWith("0")) {
            // Open Handshake
        } else if (text.startsWith("2")) {
            session?.send(Frame.Text("3")) // Pong
        } else if (text.startsWith("42$NAMESPACE")) {
            // Event in our namespace
            try {
                // Parse Logic: 42/data-updates,["event_name",{...}]
                val jsonPart = text.substringAfter(",")
                val array = Json.parseToJsonElement(jsonPart).jsonArray

                if (array.size >= 1) {
                    val eventName = array[0].jsonPrimitive.content
                    val payload = if (array.size >= 2) array[1].jsonObject else null

                    when (eventName) {
                        "connected" -> {
                            // Payload: socketId, connectedAt, timestamp
                        }
                        "screenshot_captured" -> {
                            payload?.get("message")?.jsonPrimitive?.content?.let {
                                _statusMessages.emit(it)
                            }
                        }
                        "ocr_started" -> {
                            payload?.get("message")?.jsonPrimitive?.content?.let {
                                _statusMessages.emit(it)
                            }
                        }
                        "ocr_complete" -> {
                            payload?.get("message")?.jsonPrimitive?.content?.let {
                                _statusMessages.emit(it)
                            }
                        }
                        "ai_processing_started" -> {
                            payload?.get("message")?.jsonPrimitive?.content?.let {
                                _statusMessages.emit(it)
                            }
                        }
                        "ai_processing_complete" -> {
                            val message = payload?.get("message")?.jsonPrimitive?.content
                            val response = payload?.get("response")?.jsonPrimitive?.content ?: ""

                            if (message != null) _statusMessages.emit(message)

                            if (response.isNotEmpty()) {
                                val timestamp = getTimeMillis()
                                val id = "ans-$timestamp"

                                var question = "AI Response"
                                var answerText = response

                                if (response.startsWith("Problem: ")) {
                                    val firstLineEnd = response.indexOf('\n')
                                    if (firstLineEnd != -1) {
                                        question = response.substring(9, firstLineEnd).trim()
                                        answerText = response.substring(firstLineEnd + 1).trim()
                                    }
                                }

                                val answer = Answer(
                                    id = id,
                                    question = question,
                                    answer = answerText,
                                    timestamp = timestamp,
                                    type = "ai_response"
                                )
                                _answers.emit(answer)
                            }
                        }
                        "aiprocessing_error" -> {
                            val error = payload?.get("error")?.jsonPrimitive?.content ?: ""
                            val message = payload?.get("message")?.jsonPrimitive?.content ?: ""
                            _statusMessages.emit("$message: $error")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun sendPacket(packet: String) {
        session?.send(Frame.Text(packet))
    }

    fun disconnect() {
        scope.launch {
            session?.close()
            session = null
            _isConnected.emit(false)
        }
    }
}