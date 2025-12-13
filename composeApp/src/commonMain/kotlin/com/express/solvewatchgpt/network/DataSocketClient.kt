package com.express.solvewatchgpt.network

import com.express.solvewatchgpt.model.Answer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
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
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

class DataSocketClient(private val client: HttpClient) {

    private var session: DefaultClientWebSocketSession? = null
    private val _answers = MutableSharedFlow<Answer>()
    val answers: SharedFlow<Answer> = _answers.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Namespace for Data Updates (Frontend -> Mobile)
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
                // Parse Logic
                val jsonPart = text.substringAfter(",")
                val array = Json.parseToJsonElement(jsonPart).jsonArray

                if (array.size >= 1) {
                    val eventName = array[0].jsonPrimitive.content
                    
                    if (eventName == "data_update" && array.size >= 2) {
                        val payload = array[1].jsonObject
                        val type = payload["type"]?.jsonPrimitive?.content
                        
                        if (type == "update") {
                            val newItem = payload["newItem"]?.jsonObject
                            if (newItem != null) {
                                val question = newItem["extractedText"]?.jsonPrimitive?.content ?: ""
                                val answerText = newItem["gptResponse"]?.jsonPrimitive?.content ?: newItem["response"]?.jsonPrimitive?.content ?: ""
                                val timestamp = newItem["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L
                                val id = newItem["filename"]?.jsonPrimitive?.content ?: "item-${timestamp}"
                                val itemType = newItem["type"]?.jsonPrimitive?.content ?: "question"

                                if (question.isNotBlank()) {
                                    val answer = Answer(
                                        id = id,
                                        question = question,
                                        answer = answerText,
                                        timestamp = timestamp,
                                        type = itemType
                                    )
                                    _answers.emit(answer)
                                }
                            }
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
