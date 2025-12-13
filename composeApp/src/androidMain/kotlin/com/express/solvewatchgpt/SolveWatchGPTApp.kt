package com.express.solvewatchgpt

import android.app.Application
import com.express.solvewatchgpt.di.initKoin
import org.koin.android.ext.koin.androidContext

class SolveWatchGPTApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@SolveWatchGPTApp)
        }
    }
}
