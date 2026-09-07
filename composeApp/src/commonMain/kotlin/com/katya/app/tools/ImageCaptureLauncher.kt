package com.katya.app.tools

import androidx.compose.runtime.Composable

expect class ImageCaptureLauncher {
    fun launch()
}

@Composable
expect fun rememberImageCaptureLauncher(onResult: (ByteArray?) -> Unit): ImageCaptureLauncher
