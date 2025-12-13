package com.express.solvewatchgpt.di

import com.express.solvewatchgpt.speech.SpeechViewModel
import org.koin.core.module.Module
import org.koin.dsl.module
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import com.express.solvewatchgpt.network.SpeechSocketClient
import org.koin.core.module.dsl.viewModelOf

expect val platformModule: Module

val sharedModule = module {
    single {
        HttpClient {
            install(WebSockets) {
                // pingInterval = 20_000 
            }
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true 
                    ignoreUnknownKeys = true 
                })
            }
        }
    }
    single { SpeechSocketClient(get()) }
    viewModelOf(::SpeechViewModel)
}
