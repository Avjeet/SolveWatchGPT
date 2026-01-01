package com.express.solvewatchgpt.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.express.solvewatchgpt.speech.AudioViewModel
import com.express.solvewatchgpt.ui.permissions.RequestMicrophonePermission
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SpeechScreen(
    onBack: () -> Unit
) {
    val viewModel = koinViewModel<AudioViewModel>()
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
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppTheme.Surface)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "<",
                        color = AppTheme.OnBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Audio Test (Local)",
                    color = AppTheme.OnBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Error Message Area
            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppTheme.Error.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = state.error ?: "Unknown Error",
                        color = AppTheme.Error,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Content Area based on State
            if (state.statusMessage != null && !state.isModelReady) {
                // LOADING / INITIALIZING
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        color = AppTheme.Primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.statusMessage ?: "Loading...",
                        color = AppTheme.OnBackground,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Do not close the screen",
                        color = AppTheme.OnSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else if (!state.isModelReady) {
                // NOT READY -> SETUP BUTTON
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Speech Model Required using Offline Whisper Tiny (40MB)",
                        color = AppTheme.OnSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Button(
                        onClick = { viewModel.initializeModel() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.error != null) AppTheme.Error else AppTheme.Primary
                        ),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (state.error != null) "Retry Setup" else "Download & Setup Model",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // READY -> RECORDING UI
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Transcription
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(AppTheme.Surface, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.transcription.isEmpty()) {
                            Text(
                                text = if (state.isListening) "Listening..." else "Tap REC to speak",
                                color = AppTheme.OnSurface.copy(alpha = 0.4f),
                                fontSize = 18.sp
                            )
                        } else {
                            Text(
                                text = state.transcription,
                                color = AppTheme.OnBackground,
                                fontSize = 20.sp,
                                lineHeight = 28.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                }

                Spacer(modifier = Modifier.height(16.dp))

                // Manual Process Button (Only visible when listening and has text)
                AnimatedVisibility(
                    visible = state.isListening && state.transcription.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Button(
                        onClick = { viewModel.triggerManualProcessing() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.Secondary,
                            contentColor = AppTheme.OnBackground
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Process & Clear",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Visualizer
                AudioVisualizer(
                    isListening = state.isListening,
                    audioLevel = state.audioLevel,
                    modifier = Modifier.height(60.dp).fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Record Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(
                            elevation = if (state.isListening) 16.dp else 4.dp,
                            shape = CircleShape,
                            spotColor = AppTheme.Primary
                        )
                        .clip(CircleShape)
                        .background(
                            if (state.isListening) AppTheme.Error else AppTheme.Primary
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
                    Text(
                        text = if (state.isListening) "STOP" else "REC",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Permission Handling
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
}
