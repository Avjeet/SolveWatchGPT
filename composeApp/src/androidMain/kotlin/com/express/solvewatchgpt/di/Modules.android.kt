package com.express.solvewatchgpt.di


import com.express.solvewatchgpt.speech.SpeechRecognizerManager
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import com.express.solvewatchgpt.speech.sherpa.SherpaSpeechManager

actual val platformModule = module {
    single<SpeechRecognizerManager> { SherpaSpeechManager(androidContext(), get()) }
}
