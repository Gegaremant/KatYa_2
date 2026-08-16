package com.katya.app.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

actual class ImageCaptureLauncher(
    private val launchAction: () -> Unit
) {
    actual fun launch() {
        launchAction()
    }
}

@Composable
actual fun rememberImageCaptureLauncher(onResult: (ByteArray?) -> Unit): ImageCaptureLauncher {
    val context = LocalContext.current
    var tempFileUri by remember { mutableStateOf<Uri?>(null) }
    var tempFile by remember { mutableStateOf<File?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempFile?.let { file ->
                try {
                    val bytes = file.readBytes()
                    onResult(bytes)
                } catch (e: Exception) {
                    e.printStackTrace()
                    onResult(null)
                } finally {
                    file.delete() // Clean up
                }
            } ?: onResult(null)
        } else {
            tempFile?.delete()
            onResult(null)
        }
    }

    return remember {
        ImageCaptureLauncher {
            val file = File(context.cacheDir, "camera_capture_${UUID.randomUUID()}.jpg")
            tempFile = file
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            tempFileUri = uri
            launcher.launch(uri)
        }
    }
}
