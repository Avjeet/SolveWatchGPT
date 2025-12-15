package com.express.solvewatchgpt.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.express.solvewatchgpt.model.Answer
import com.express.solvewatchgpt.speech.SpeechViewModel
import com.express.solvewatchgpt.ui.permissions.RequestMicrophonePermission
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainScreen(
    onNavigateToAudio: () -> Unit
) {
    val viewModel = koinViewModel<SpeechViewModel>()
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppTheme.Background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    onNavigateToAudio()
                },
                containerColor = AppTheme.Primary,
                contentColor = Color.White
            ) {
                // Mic Icon (Simple shape drawing)
                MicIcon(color = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 10.dp)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SolveWatch Chat",
                    color = AppTheme.OnBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

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

            // Transcription Preview (Debugging/Feedback)
            if (state.speech.transcription.isNotEmpty()) {
                Text(
                    text = state.speech.transcription,
                    color = AppTheme.OnSurface.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }

            // Answers List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.answers.isEmpty()) {
                    item {
                        EmptyStateMessage()
                    }
                }

                items(state.answers, key = { it.id }) { answer ->
                    // For a chat-like feel, maybe different card style?
                    // Keeping AnswerCard for now but it can be enhanced.
                    AnswerCard(
                        answer = answer,
                        isExpanded = state.expandedAnswerId == answer.id,
                        onClick = { viewModel.toggleAnswer(answer.id) }
                    )
                }
            }
        }
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
fun AnswerCard(
    answer: Answer,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.Surface)
            .border(
                width = 1.dp,
                color = if (isExpanded) AppTheme.Primary else AppTheme.OnSurface.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = answer.question,
                color = AppTheme.OnBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            ArrowIcon(isExpanded = isExpanded)
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppTheme.OnSurface.copy(alpha = 0.1f))
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = answer.answer,
                color = AppTheme.OnSurface.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        }
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
