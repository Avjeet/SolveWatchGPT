package com.express.solvewatchgpt.config

object AppConfig {
    const val HOST = "192.168.1.109"
    const val PORT = 4000
    
    val BASE_URL = "http://$HOST:$PORT"
    val SOCKET_URL = "ws://$HOST:$PORT"
}
