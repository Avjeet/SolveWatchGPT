package com.express.solvewatchgpt.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun AudioVisualizer(
    isListening: Boolean,
    audioLevel: Float, // 0..1
    modifier: Modifier = Modifier,
    color: Color = AppTheme.Primary
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Animate phase shift for movement
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Animate amplitude based on listening state AND audio level
    // Base amplitude creates a "breathing" effect, plus dynamic level
    val targetAmp = if (isListening) (10f + (audioLevel * 50f)) else 2f
    
    val amplitude by animateFloatAsState(
        targetValue = targetAmp,
        animationSpec = tween(50) // Fast response
    )

    Canvas(modifier = modifier.fillMaxWidth().height(100.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        val path = Path()
        path.moveTo(0f, centerY)

        for (x in 0..width.toInt() step 5) {
            // Simplified sine wave simulation
            // In a real app with audio buffer, we'd map FFT data here
            val normalizedX = x / width
            val waveHeight = sin((x * 0.05f) + phase) * amplitude * sin(normalizedX * 3.14f) 
            path.lineTo(x.toFloat(), centerY + waveHeight)
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    color.copy(alpha = 0f),
                    color,
                    color.copy(alpha = 0f)
                )
            ),
            style = Stroke(width = 4.dp.toPx())
        )
    }
}
