package com.express.solvewatchgpt.network

import com.express.solvewatchgpt.model.ApiConfig
import com.express.solvewatchgpt.model.ConfigResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

import com.express.solvewatchgpt.config.AppConfig

class ConfigRepository(private val client: HttpClient) {

    private val BOOT_URL = AppConfig.BASE_URL

    suspend fun fetchConfig(): ConfigResponse? {
        return try {
            client.get("$BOOT_URL/api/config/keys").body()
        } catch (e: Exception) {
            e.printStackTrace()
            ConfigResponse(success = false, error = e.message)
        }
    }

    suspend fun saveConfig(config: ApiConfig): ConfigResponse? {
        return try {
            client.post("$BOOT_URL/api/config/keys") {
                contentType(ContentType.Application.Json)
                setBody(config)
            }.body()
        } catch (e: Exception) {
            e.printStackTrace()
            ConfigResponse(success = false, error = e.message)
        }
    }
}
