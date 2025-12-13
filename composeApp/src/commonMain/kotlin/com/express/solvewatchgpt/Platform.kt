package com.express.solvewatchgpt

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform