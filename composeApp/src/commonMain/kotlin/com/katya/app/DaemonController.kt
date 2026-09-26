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

    /**
     * Make the VLESS runtime agree with the settings that were just imported.
     *
     * Feedback #10: "connected" is an in-memory flag, so it survived an import that
     * brought no VLESS configuration at all. The card kept claiming a live tunnel
     * while the container still ran whatever config it had before. The imported
     * values are the truth now: clear the stale flag and let the proxy re-evaluate
     * (it already refuses to start with a blank URI or when VLESS is off).
     * Default no-op: only Android runs the tunnel.
     */
    fun reconcileVlessAfterImport() {}

    /**
     * A configured Free DeepSeek Proxy instance was deleted.
     *
     * Feedback #5: the proot process kept running on the deleted instance's auth file
     * and the manager still pointed at that instance, so creating a new proxy afterwards
     * looked broken — the old coroutine was never stopped. Default no-op: only Android
     * runs the sandboxed proxy.
     */
    fun onFreeDeepSeekInstanceRemoved(instanceId: String) {}
}

expect fun createDaemonController(): DaemonController
