package com.katya.app.tools

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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

    val takePictureLauncher = rememberLauncherForActivityResult(
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

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = tempFileUri
            if (uri != null) {
                try {
                    takePictureLauncher.launch(uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                    tempFile?.delete()
                    onResult(null)
                }
            }
        } else {
            tempFile?.delete()
            onResult(null)
        }
    }

    return remember {
        ImageCaptureLauncher {
            try {
                val file = File(context.cacheDir, "camera_capture_${UUID.randomUUID()}.jpg")
                tempFile = file
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                tempFileUri = uri

                val hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (hasCameraPermission) {
                    takePictureLauncher.launch(uri)
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tempFile?.delete()
                onResult(null)
            }
        }
    }
}
