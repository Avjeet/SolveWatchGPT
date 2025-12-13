package com.express.solvewatchgpt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun ArrowIcon(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.OnSurface.copy(alpha = 0.5f)
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val path = Path()
        val w = size.width
        val h = size.height
        
        // Simple arrow shape
        if (isExpanded) {
            // Up arrow "^"
            path.moveTo(w * 0.2f, h * 0.6f)
            path.lineTo(w * 0.5f, h * 0.3f)
            path.lineTo(w * 0.8f, h * 0.6f)
        } else {
            // Down arrow "v"
            path.moveTo(w * 0.2f, h * 0.4f)
            path.lineTo(w * 0.5f, h * 0.7f)
            path.lineTo(w * 0.8f, h * 0.4f)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
