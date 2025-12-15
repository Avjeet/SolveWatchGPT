package com.express.solvewatchgpt

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.express.solvewatchgpt.ui.MainScreen
import com.express.solvewatchgpt.ui.SpeechScreen
import org.jetbrains.compose.ui.tooling.preview.Preview

enum class Screen {
    Main,
    Audio
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        var currentScreen by remember { mutableStateOf(Screen.Main) }

        when (currentScreen) {
            Screen.Main -> MainScreen(
                onNavigateToAudio = { currentScreen = Screen.Audio }
            )

            Screen.Audio -> SpeechScreen(
                onBack = { currentScreen = Screen.Main }
            )
        }
    }
}