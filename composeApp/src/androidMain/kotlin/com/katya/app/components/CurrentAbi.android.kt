package com.katya.app.components

import android.content.Context
import android.os.Build

actual fun currentAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: Build.CPU_ABI

actual fun bundledComponentIds(): List<String> = bundledComponentIdsInternal(null)

/**
 * Внутренняя часть — чтобы можно было позвать с готовым [Context], не поднимая Koin.
 *
 * Пересечение с известными сидами обязательно: assets и код могут разойтись (другая
 * версия сборки), и тогда «встроенный» компонент установщик просто не узнает по типу.
 */
internal fun bundledComponentIdsInternal(context: Context?): List<String> {
    val ctx = context ?: BundledComponents.contextOrNull() ?: return emptyList()
    val abi = currentAbi()
    return ComponentDefaults.seeds()
        .filter { it.abi == abi }
        .filter { BundledComponents.has(ctx, it.id, it.componentType) }
        .map { it.id }
}
