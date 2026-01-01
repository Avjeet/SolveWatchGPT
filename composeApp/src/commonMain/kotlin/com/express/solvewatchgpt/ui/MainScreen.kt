package com.express.solvewatchgpt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.express.solvewatchgpt.speech.ChatMessage
import com.express.solvewatchgpt.speech.SpeechViewModel
import com.express.solvewatchgpt.ui.permissions.RequestMicrophonePermission
import org.koin.compose.viewmodel.koinViewModel

@Composable


fun MainScreen() {
    val viewModel = koinViewModel<SpeechViewModel>()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val config by viewModel.config.collectAsState()

    var hasPermission by remember { mutableStateOf(false) }
    var requestPermissionTrigger by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    androidx.compose.runtime.LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppTheme.Background,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.speech.isModelReady) {
                // Minimized Audio Player Interface
                AudioBottomBar(
                    state = state.speech,
                    isSocketConnected = state.isSocketConnected,
                    onStartListening = {
                        if (hasPermission) {
                            viewModel.startListening()
                        } else {
                            requestPermissionTrigger = true
                        }
                    },
                    onStopListening = viewModel::stopListening,
                    onProcess = viewModel::triggerManualProcessing,
                    onClear = viewModel::clearTranscription
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 10.dp)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SolveWatch",
                        color = AppTheme.OnBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Settings Button
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { viewModel.openSettings() }
                            .background(AppTheme.OnSurface.copy(alpha = 0.05f))
                            .padding(8.dp)
                    ) {
                        // Simple Gear Icon Drawing
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
                            drawCircle(color = AppTheme.OnSurface, style = Stroke(width = 2f))
                            drawCircle(color = AppTheme.OnSurface, radius = 2f)
                        }
                    }
                }

                // Connection Toggle
                Box(
                    modifier = Modifier
                        .background(
                            color = if (state.isSocketConnected) AppTheme.Primary.copy(alpha = 0.2f) else AppTheme.OnSurface.copy(
                                alpha = 0.1f
                            ),
                            shape = RoundedCornerShape(50)
                        )
                        .border(
                            width = 1.dp,
                            color = if (state.isSocketConnected) AppTheme.Primary else AppTheme.OnSurface.copy(
                                alpha = 0.2f
                            ),
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { viewModel.toggleSocketConnection() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (state.isSocketConnected) Color.Green else Color.Red,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = if (state.isSocketConnected) "Live" else "Offline",
                            color = if (state.isSocketConnected) AppTheme.Primary else AppTheme.OnSurface.copy(
                                alpha = 0.6f
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Error Display
            if (state.speech.error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(AppTheme.Error.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = state.speech.error ?: "Unknown Error",
                        color = AppTheme.Error,
                        fontSize = 12.sp
                    )
                }
            }

            // Removed Transcription Preview from top
            // Removed Error Display from top to avoid clutter, errors will be in bottom bar or toast ideally
            // Keeping critical connection error if needed but standardizing on bottom bar status.

            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                reverseLayout = false // Normal top-down layout
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        EmptyStateMessage()
                    }
                }

                // Show messages in reverse chronological order if we want latest at bottom, 
                // but usually the list is stored old->new. 
                // Let's assume list is Old -> New. We want New at bottom.
                // We should use keys.

                items(state.messages) { message ->
                    ChatBubble(message = message, onClick = {
                        if (!message.isUser) { // Only allow clicking AI messages
                            viewModel.openMessageOptions(message.id)
                        }
                    })
                }
            }
        }
    }



    if (isSettingsOpen && config != null) {
        SettingsBottomSheet(
            config = config!!,
            onDismiss = viewModel::closeSettings,
            onSave = viewModel::saveSettings
        )
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

    if (state.speech.isDownloading) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { /* Prevent dismiss */ },
            title = { Text(text = "Initializing AI") },
            text = { Text(text = "Downloading high-accuracy speech model (40MB). This happens only once.") },
            confirmButton = {},
            dismissButton = {}
        )
    }

    if (state.isOptionsDialogOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::closeMessageOptions,
            title = { Text("Select Option") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.sendPromptOption("debug") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Primary)
                    ) {
                        Text("Debug (with Snapshot)", color = Color.White)
                    }
                    Button(
                        onClick = { viewModel.sendPromptOption("theory") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Surface)
                    ) {
                        Text("Theory", color = AppTheme.OnSurface)
                    }
                    Button(
                        onClick = { viewModel.sendPromptOption("coding") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Surface)
                    ) {
                        Text("Coding")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = viewModel::closeMessageOptions) {
                    Text("Cancel", color = AppTheme.OnSurface)
                }
            }
        )
    }
}

@Composable

fun AudioBottomBar(
    state: com.express.solvewatchgpt.speech.SpeechState,
    isSocketConnected: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onProcess: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.Surface)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom, // Align bottom for multi-line text
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Cancel Button (X) - Only if text exists
            if (state.transcription.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppTheme.Error.copy(alpha = 0.1f))
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = AppTheme.Error, fontWeight = FontWeight.Bold)
                }
            }

            // Input Text Display
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppTheme.Background)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(IntrinsicSize.Min)
            ) {
                if (state.transcription.isEmpty()) {
                    Text(
                        text = if (state.isListening) "Listening..." else "Tap Mic to speak",
                        color = AppTheme.OnSurface.copy(alpha = 0.4f),
                        fontSize = 16.sp
                    )
                } else {
                    Text(
                        text = state.transcription,
                        color = AppTheme.OnBackground,
                        fontSize = 16.sp
                    )
                }
            }

            // Mic Button (Toggle)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.isListening) AppTheme.Error.copy(alpha = 0.1f) else AppTheme.Primary.copy(
                            alpha = 0.1f
                        )
                    )
                    .clickable {
                        if (state.isListening) onStopListening() else onStartListening()
                    },
                contentAlignment = Alignment.Center
            ) {
                // Mic Icon needs to be drawn or text
                if (state.isListening) {
                    // Stop Square
                    Box(
                        modifier = Modifier.size(16.dp)
                            .background(AppTheme.Error, RoundedCornerShape(4.dp))
                    )
                } else {
                    MicIcon(color = AppTheme.Primary, modifier = Modifier.size(24.dp))
                }
            }

            // Send Button - Only if text exists
            if (state.transcription.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AppTheme.Primary)
                        .clickable(onClick = onProcess),
                    contentAlignment = Alignment.Center
                ) {
                    Text("➜", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, onClick: () -> Unit) {
    val isUser = message.isUser
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(if (isUser) AppTheme.Primary else AppTheme.Surface)
                .clickable { onClick() }
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = if (isUser) Color.White else AppTheme.OnSurface.copy(alpha = 0.9f),
                fontSize = 16.sp,
                lineHeight = 22.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isUser) "You" else "PC",
            fontSize = 10.sp,
            color = AppTheme.OnSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun MicIcon(modifier: Modifier = Modifier, color: Color) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val path = Path()
        val w = size.width
        val h = size.height

        // Mic body
        path.moveTo(w * 0.35f, h * 0.2f)
        path.lineTo(w * 0.65f, h * 0.2f)
        path.lineTo(w * 0.65f, h * 0.5f)
        path.lineTo(w * 0.35f, h * 0.5f)
        path.close()

        drawPath(
            path,
            color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Mic stand
        val standPath = Path()
        standPath.moveTo(w * 0.5f, h * 0.5f)
        standPath.lineTo(w * 0.5f, h * 0.7f)

        standPath.moveTo(w * 0.3f, h * 0.7f)
        standPath.lineTo(w * 0.7f, h * 0.7f)

        drawPath(
            standPath,
            color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}


@Composable
fun EmptyStateMessage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Waiting for server updates...",
            color = AppTheme.OnSurface.copy(alpha = 0.5f),
            fontSize = 16.sp
        )
    }
}
