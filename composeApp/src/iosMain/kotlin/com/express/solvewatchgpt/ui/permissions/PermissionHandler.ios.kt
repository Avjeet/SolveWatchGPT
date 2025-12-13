package com.express.solvewatchgpt.ui.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionRecordPermissionUndetermined
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied

@Composable
actual fun RequestMicrophonePermission(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    LaunchedEffect(Unit) {
        val session = AVAudioSession.sharedInstance()
        when (session.recordPermission()) {
            AVAudioSessionRecordPermissionGranted -> {
                onPermissionGranted()
            }
            AVAudioSessionRecordPermissionDenied -> {
                onPermissionDenied()
            }
            AVAudioSessionRecordPermissionUndetermined -> {
                session.requestRecordPermission { granted ->
                    if (granted) onPermissionGranted() else onPermissionDenied()
                }
            }
            else -> onPermissionDenied()
        }
    }
}
