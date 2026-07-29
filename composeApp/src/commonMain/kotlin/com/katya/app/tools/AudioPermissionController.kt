package com.katya.app.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/**
 * Multiplatform controller for Audio record permission requests.
 */
expect class AudioPermissionController() {
    val permissionRequested: StateFlow<Boolean>

    fun hasPermission(): Boolean

    suspend fun requestPermission(): Boolean

    fun onPermissionResult(granted: Boolean)
}

@Composable
expect fun SetupAudioPermissionHandler(controller: AudioPermissionController)
