package com.katya.app.components

/** Fallback для платформ без Android-загрузчика (desktop/wasm) — ничего не делает. */
class NoopComponentDownloadLauncher : ComponentDownloadLauncher {
    override fun startDownload(id: String) {
        // Non-Android targets have no downloadable components.
    }
}