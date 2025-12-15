package com.express.solvewatchgpt.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.express.solvewatchgpt.model.ApiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    config: ApiConfig,
    onDismiss: () -> Unit,
    onSave: (ApiConfig) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Local state for editing
    // We strictly use the "order" from the config to display items.
    // If order is empty, we default to the known keys.
    val initialOrder = if (config.order.isNotEmpty()) config.order else listOf("openai", "grok", "gemini")
    val providers = remember { 
        mutableStateListOf<String>().apply { addAll(initialOrder) } 
    }
    
    // Map to hold current input values
    val keysInput = remember { 
        mutableStateMapOf<String, String>().apply { 
             config.keys.forEach { (k, v) -> put(k, v) }
        }
    }
    
    val enabledState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            config.enabled.forEach { put(it, true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "API Configuration",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            providers.forEachIndexed { index, providerId ->
                ProviderConfigItem(
                    providerId = providerId,
                    currentKey = keysInput[providerId] ?: "",
                    isEnabled = enabledState[providerId] == true,
                    onKeyChange = { keysInput[providerId] = it },
                    onEnabledChange = { enabledState[providerId] = it },
                    onMoveUp = if (index > 0) { { 
                        val item = providers.removeAt(index)
                        providers.add(index - 1, item)
                    } } else null,
                    onMoveDown = if (index < providers.size - 1) { {
                        val item = providers.removeAt(index)
                        providers.add(index + 1, item)
                    } } else null
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    // Reconstruct ApiConfig
                    val finalKeys = keysInput.toMap()
                    val finalEnabled = providers.filter { enabledState[it] == true }
                    // Order is now the reordered list
                    val newConfig = ApiConfig(
                        keys = finalKeys,
                        order = providers.toList(),
                        enabled = finalEnabled
                    )
                    onSave(newConfig)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Configuration")
            }
        }
    }
}

@Composable
fun ProviderConfigItem(
    providerId: String,
    currentKey: String,
    isEnabled: Boolean,
    onKeyChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = isEnabled,
                onCheckedChange = onEnabledChange
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = providerId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            
            // Reordering controls
            if (onMoveUp != null) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(24.dp)) {
                    UpIcon(modifier = Modifier.size(24.dp))
                }
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            if (onMoveDown != null) {
                IconButton(onClick = onMoveDown, modifier = Modifier.size(24.dp)) {
                    DownIcon(modifier = Modifier.size(24.dp))
                }
            } else {
                 Spacer(modifier = Modifier.size(24.dp))
            }
        }

        if (isEnabled) {
            OutlinedTextField(
                value = currentKey,
                onValueChange = onKeyChange,
                label = { Text("$providerId API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text(if (currentKey == "***") "Key Configured" else "Enter API Key") }
            )
        }
    }
}

@Composable
fun UpIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path()
        val w = size.width
        val h = size.height
        
        path.moveTo(w * 0.5f, h * 0.3f)
        path.lineTo(w * 0.2f, h * 0.6f)
        path.lineTo(w * 0.8f, h * 0.6f)
        path.close()
        
        drawPath(path, Color.Gray)
        drawPath(path, Color.Black, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun DownIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path()
        val w = size.width
        val h = size.height
        
        path.moveTo(w * 0.5f, h * 0.7f)
        path.lineTo(w * 0.2f, h * 0.4f)
        path.lineTo(w * 0.8f, h * 0.4f)
        path.close()
        
        drawPath(path, Color.Gray)
        drawPath(path, Color.Black, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

