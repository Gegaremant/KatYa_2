package com.katya.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.katya.app.decodeToImageBitmap
import katya.composeapp.generated.resources.Res

/**
 * Loads a raster image stored under `composeResources/files/` and renders it.
 * Raster assets (PNG/JPG) can't be referenced through the generated
 * `Res.drawable.*` accessors in this resource pipeline, but the generic
 * [Res.readBytes] API still reads them, and [decodeToImageBitmap] decodes them.
 */
@Composable
fun ResourceImage(
    filePath: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, filePath) {
        value = runCatching { Res.readBytes(filePath) }
            .getOrNull()
            ?.let { decodeToImageBitmap(it) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}
