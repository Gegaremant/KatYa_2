package com.katya.app.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * «Снять фото» на десктопе.
 *
 * На Android это камера. На десктопе веб-камеры есть, но единого способа их
 * открыть нет, и тянуть отдельную зависимость ради одной кнопки — лишнее.
 *
 * `launch()` ничего не делает, и это осознанно: он зовётся из обработчика
 * результата камеры, который на десктопе не запускается. Заглушка нужна, чтобы
 * общий код собирался, а не чтобы кнопка делала вид, что работает.
 */
actual class ImageCaptureLauncher {
    actual fun launch() {
        // Камеры нет — запускать нечего.
    }
}

@Composable
actual fun rememberImageCaptureLauncher(onResult: (ByteArray?) -> Unit): ImageCaptureLauncher = remember { ImageCaptureLauncher() }
