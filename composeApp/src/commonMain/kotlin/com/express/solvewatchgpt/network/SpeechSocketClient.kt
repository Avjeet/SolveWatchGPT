package com.express.solvewatchgpt.network

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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SpeechSocketClient(private val client: HttpClient) {

    private var session: DefaultClientWebSocketSession? = null
    private val _transcriptions = MutableSharedFlow<String>()
    val transcriptions: SharedFlow<String> = _transcriptions.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Namespace for Text Stream (Mobile -> Backend)
    private val NAMESPACE = "/text-stream"

    suspend fun connect(host: String, port: Int) {
        val urlString = "ws://$host:$port/socket.io/?EIO=4&transport=websocket"
        println("Connecting to $urlString")

        try {
            client.webSocket(urlString) {
                session = this
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
                    println("Socket Error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun handleMessage(text: String) {
        // Socket.IO Protocol v4
        // 0: Open
        // 40: Connect Namespace
        // 42: Event
        // 2: Ping -> 3: Pong

        if (text.startsWith("0")) {
            // Open Handshake
            // Send namespace connect if not sent? we sent it above.
        } else if (text.startsWith("2")) {
            session?.send(Frame.Text("3")) // Pong
        } else if (text.startsWith("42$NAMESPACE")) {
            // Event in our namespace
            // format: 42/text-stream,["event_name", data]
            try {
                // Parse Logic (Simplified)
                val jsonPart = text.substringAfter(",")
                val array = Json.parseToJsonElement(jsonPart).jsonArray

                if (array.size >= 1) {
                    val eventName = array[0].jsonPrimitive.content
                    // Handle events from docs: session_started, questions_extracted, error

                    if (eventName == "questions_extracted" && array.size >= 2) {
                        val data = array[1].jsonObject
                        val questions = data["questions"]?.jsonArray
                        questions?.forEach { q ->
                            val qText = q.jsonObject["question"]?.jsonPrimitive?.content ?: ""
                            _transcriptions.emit(qText)
                        }
                    } else if (eventName == "session_started") {
                        println("Session Started: ${array.getOrNull(1)}")
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

    suspend fun sendTextChunk(text: String) {
        // Emit text_chunk event
        // 42/text-stream,["text_chunk", {"text": "...", "timestamp": ...}]
        val jsonPayload = """{"text": "$text"}"""
        val packet = "42$NAMESPACE,[\"text_chunk\",$jsonPayload]"
        sendPacket(packet)
    }

    fun disconnect() {
        scope.launch {
            session?.close()
            session = null
        }
    }
}
