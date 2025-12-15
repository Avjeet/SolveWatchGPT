package com.express.solvewatchgpt.di

import com.express.solvewatchgpt.speech.IosSpeechRecognizer
import com.express.solvewatchgpt.speech.SpeechRecognizerManager
import org.koin.dsl.module

actual val platformModule = module {
    factory<SpeechRecognizerManager> { IosSpeechRecognizer() }
}
