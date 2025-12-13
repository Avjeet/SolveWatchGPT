package com.express.solvewatchgpt.ui.permissions

import androidx.compose.runtime.Composable

@Composable
expect fun RequestMicrophonePermission(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit
)
