package com.katya.app.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

expect class BatteryOptimizationPermissionController() {
    val permissionRequested: StateFlow<Boolean>
    fun hasPermission(): Boolean
    suspend fun requestPermission(): Boolean
    fun onPermissionResult(granted: Boolean)
}

@Composable
expect fun SetupBatteryOptimizationPermissionHandler(controller: BatteryOptimizationPermissionController)
