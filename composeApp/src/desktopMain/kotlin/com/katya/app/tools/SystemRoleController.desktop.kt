package com.katya.app.tools

/**
 * Системные роли Android на десктопе не существуют.
 *
 * Администратор устройства, ассистент по умолчанию и агент доверия — это три
 * отдельных экрана настроек Android. Общего аналога нет, а «открыть настройки»
 * здесь означало бы просто открыть настройки ОС, что сделало бы кнопку
 * бессмысленной.
 */
actual class SystemRoleController actual constructor() {
    actual fun openDeviceAdminSettings() {
        // Экрана нет.
    }

    actual fun openDefaultAssistantSettings() {
        // Экрана нет.
    }

    actual fun openTrustAgentSettings() {
        // Экрана нет.
    }

    actual fun isDeviceAdmin(): Boolean = false

    actual fun isDefaultAssistant(): Boolean = false
}
