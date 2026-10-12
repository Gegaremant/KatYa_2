package com.katya.app.components

import com.katya.app.Platform
import com.katya.app.currentPlatform

/**
 * ABI на десктопе — это имя процессора, а не Android-код.
 *
 * Значение уходит в подстановки имён компонентов, но самих компонентов на
 * десктопе нет: proot, rootfs и xray не нужны, потому что команда идёт прямо в
 * оболочку ОС. Здесь важен не «правильный» ответ, а такой, чтобы UI не пытался
 * качать Android-пакеты на компьютер.
 */
actual fun currentAbi(): String = when (currentPlatform) {
    is Platform.Desktop.Linux -> "desktop-linux"
    is Platform.Desktop.Windows -> "desktop-windows"
    else -> "desktop"
}

/** Компонентов внутри сборки нет: их место на десктопе занимает сама ОС. */
actual fun bundledComponentIds(): List<String> = emptyList()
