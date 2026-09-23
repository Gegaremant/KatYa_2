package com.katya.app.components

import android.os.Build

actual fun currentAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: Build.CPU_ABI
