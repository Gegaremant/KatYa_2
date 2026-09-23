package com.katya.app.components

import android.content.Context
import android.content.Intent

class AndroidComponentDownloadLauncher(
    private val context: Context,
) : ComponentDownloadLauncher {
    override fun startDownload(id: String) {
        val intent = Intent(context, ComponentDownloaderService::class.java)
            .setAction(ComponentDownloaderService.ACTION_DOWNLOAD)
            .putExtra(ComponentDownloaderService.EXTRA_COMPONENT_ID, id)
        context.startForegroundService(intent)
    }
}