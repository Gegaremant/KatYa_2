package com.katya.app.device

import android.content.Context

private var appContext: Context? = null

fun initDeviceProviders(context: Context) {
    appContext = context.applicationContext
}

actual fun createDeviceInfoProvider(): DeviceInfoProvider? {
    val context = appContext ?: return null
    return AndroidDeviceInfoProvider(context)
}

actual fun createNetworkStatusProvider(): NetworkStatusProvider? {
    val context = appContext ?: return null
    return AndroidNetworkStatusProvider(context)
}
