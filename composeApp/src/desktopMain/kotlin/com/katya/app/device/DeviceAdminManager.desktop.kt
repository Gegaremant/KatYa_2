package com.katya.app.device

/**
 * Администратор устройства и агент доверия — понятия Android.
 *
 * На десктопе нет ни устройства, которым можно управлять, ни механизма, который
 * нужно подтверждать, поэтому «включено» здесь всегда означает «включено».
 * Считать иначе нельзя: UI спрашивает это, чтобы показать карточку функций, и
 * «не поддерживается» честнее, чем «включено» на пустом месте.
 */
actual object DeviceAdminManager {
    actual fun isDeviceAdminActive(): Boolean = false

    actual fun requestDeviceAdmin() {
        // Настройки администратора устройства у десктопа нет.
    }

    actual fun openDeviceAdminSettings() {
        // Экрана с такими настройками нет.
    }

    actual fun isTrustAgentEnabled(): Boolean = false

    actual fun openTrustAgentSettings() {
        // Экрана с такими настройками нет.
    }
}
