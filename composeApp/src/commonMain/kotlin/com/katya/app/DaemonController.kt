package com.katya.app

interface DaemonController {
    fun start()
    fun stop()

    /**
     * Re-point the on-device DeepSeek proxy at [instanceId] and restart it so the
     * freshly saved session is the one actually served. Default no-op: only Android
     * runs the sandboxed proxy.
     */
    fun switchFreeDeepSeekInstance(instanceId: String) {}
}

expect fun createDaemonController(): DaemonController
