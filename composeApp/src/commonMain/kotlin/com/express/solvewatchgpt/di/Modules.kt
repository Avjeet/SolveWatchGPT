package com.express.solvewatchgpt.di

import com.express.solvewatchgpt.speech.SpeechViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

expect val platformModule: Module

val sharedModule = module {
    viewModel { SpeechViewModel() }
}
