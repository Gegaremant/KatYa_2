package com.katya.app.components

/**
 * Запуск фоновой загрузки компонента. Common-интерфейс, чтобы UI
 * (вкладка «Сервер» → «Альтернативные ссылки») не зависел от Android.
 */
interface ComponentDownloadLauncher {
    fun startDownload(id: String)
}