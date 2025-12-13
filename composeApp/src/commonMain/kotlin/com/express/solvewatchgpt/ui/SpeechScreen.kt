package com.express.solvewatchgpt.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.express.solvewatchgpt.speech.SpeechViewModel
import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.express.solvewatchgpt.ui.permissions.RequestMicrophonePermission

@Composable
fun SpeechScreen() {
    val viewModel = koinViewModel<SpeechViewModel>()
    val state by viewModel.state.collectAsState()
    
    var hasPermission by remember { mutableStateOf(false) }
    var requestPermissionTrigger by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.Background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "SolveWatch GPT",
                color = AppTheme.OnBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 40.dp)
            )

            // Transcription Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.transcription.isEmpty() && !state.isListening) {
                    Text(
                        text = "Tap the microphone to start...",
                        color = AppTheme.OnSurface.copy(alpha = 0.5f),
                        fontSize = 18.sp
                    )
                } else {
                    Text(
                        text = state.transcription.ifEmpty { "Listening..." },
                        color = if (state.transcription.isEmpty()) AppTheme.Primary else AppTheme.OnBackground,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp
                    )
                }
            }
            
            // Error Message
             AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = state.error ?: "",
                    color = AppTheme.Error,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 40.dp)
            ) {
                // Visualizer
                AudioVisualizer(
                    isListening = state.isListening,
                    audioLevel = state.audioLevel,
                    modifier = Modifier.height(60.dp).fillMaxWidth(0.8f)
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                // Mic Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(
                            elevation = if (state.isListening) 20.dp else 4.dp,
                            shape = CircleShape,
                            spotColor = AppTheme.Primary
                        )
                        .clip(CircleShape)
                        .background(
                            if (state.isListening) AppTheme.Primary else AppTheme.Surface
                        )
                        .border(
                            width = 2.dp,
                            color = if (state.isListening) AppTheme.OnBackground else AppTheme.Primary.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .clickable {
                            if (state.isListening) {
                                viewModel.stopListening()
                            } else {
                                if (hasPermission) {
                                    viewModel.startListening()
                                } else {
                                    requestPermissionTrigger = true
                                }
                            }
                        }
                ) {
                    // Create a simple Mic icon using Canvas or Icon if available
                    // For now, a simple ring or text
                    Text(
                        text = if (state.isListening) "STOP" else "REC",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    
    if (requestPermissionTrigger) {
        RequestMicrophonePermission(
            onPermissionGranted = {
                hasPermission = true
                requestPermissionTrigger = false
                viewModel.startListening()
            },
            onPermissionDenied = {
                hasPermission = false
                requestPermissionTrigger = false
            }
        )
    }

    if (state.isDownloading) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss */ },
            title = { Text(text = "Initializing AI") },
            text = { Text(text = "Downloading high-accuracy speech model (40MB). This happens only once.") },
            confirmButton = {},
            dismissButton = {}
        )
    }
}
