package com.katya.app.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Разрешения, которых на десктопе нет.
 *
 * Все они — андроидные runtime-разрешения: микрофон, календарь, SMS,
 * уведомления, доступ к локальной сети, точные будильники, обход оптимизации
 * батареи, Accessibility. Объединять их в один файл можно по одной причине —
 * здесь у всех одинаковый смысл: «разрешения нет, просить нечего, дать его
 * нечем».
 *
 * `hasPermission()` возвращает true, а не false, и это не ошибка: десктоп не
 * ограничивает приложение в доступе к микрофону или сети, спрашивать нечего, и
 * «нет» заставило бы UI прятать функции, которые на самом деле доступны.
 * `requestPermission()` при этом честно говорит, что подтверждения не было.
 */

/**
 * Состояние «идёт запрос разрешения»: на десктопе оно никогда не поднимается,
 * поэтому обработчики запуска диалога и не вызываются.
 */
private fun neverRequested(): StateFlow<Boolean> = MutableStateFlow(false)

actual class AccessibilityPermissionController actual constructor() {
    actual val permissionRequested: StateFlow<Boolean> = neverRequested()

    actual fun hasPermission(): Boolean = true

    actual suspend fun requestPermission(): Boolean = true

    actual fun onPermissionResult(granted: Boolean) {
        // Диалога нет — результат неоткуда ждать.
    }
}

@Composable
actual fun SetupAccessibilityPermissionHandler(controller: AccessibilityPermissionController) {
    // Обработчика запускать нечего.
}

actual class AudioPermissionController actual constructor() {
    actual val permissionRequested: StateFlow<Boolean> = neverRequested()

    actual fun hasPermission(): Boolean = true

    actual suspend fun requestPermission(): Boolean = true

    actual fun onPermissionResult(granted: Boolean) {
        // Диалога нет.
    }
}

@Composable
actual fun SetupAudioPermissionHandler(controller: AudioPermissionController) {
    // Обработчика запускать нечего.
}

actual class BatteryOptimizationPermissionController actual constructor() {
    actual val permissionRequested: StateFlow<Boolean> = neverRequested()

    actual fun hasPermission(): Boolean = true

    actual suspend fun requestPermission(): Boolean = true

    actual fun onPermissionResult(granted: Boolean) {
        // Диалога нет.
    }
}

@Composable
actual fun SetupBatteryOptimizationPermissionHandler(controller: BatteryOptimizationPermissionController) {
    // Обработчика запускать нечего.
}

actual class CalendarPermissionController actual constructor() {
    actual val permissionRequested: StateFlow<Boolean> = neverRequested()

    actual fun hasPermission(): Boolean = true

    actual suspend fun requestPermission(): Boolean = true

    actual fun onPermissionResult(granted: Boolean) {
        // Диалога нет.
    }
}

@Composable
actual fun SetupCalendarPermissionHandler(controller: CalendarPermissionController) {
    // Обработчика запускать нечего.
}

actual class LocalNetworkPermissionController actual constructor() {
    actual val permissionRequested: StateFlow<Boolean> = neverRequested()

    actual fun hasPermission(): Boolean = true

    actual suspend fun requestPermission(): Boolean = true

    actual fun onPermissionResult(granted: Boolean) {
        // Диалога нет.
    }

    actual fun openAppSettings() {
        // Экрана настроек приложения на десктопе нет.
    }
}

@Composable
actual fun SetupLocalNetworkPermissionHandler(controller: LocalNetworkPermissionController) {
    // Обработчика запускать нечего.
}

actual class NotificationPermissionController actual constructor() {
    actual val permissionRequested: StateFlow<Boolean> = neverRequested()

    actual fun hasPermission(): Boolean = true

    actual suspend fun requestPermission(): Boolean = true

    actual fun onPermissionResult(granted: Boolean) {
        // Диалога нет.
    }
}

@Composable
actual fun SetupNotificationPermissionHandler(controller: NotificationPermissionController) {
    // Обработчика запускать нечего.
}

actual class SmsPermissionController actual constructor() {
    actual val permissionRequested: StateFlow<Boolean> = neverRequested()

    actual fun hasPermission(): Boolean = false

    actual suspend fun requestPermission(): Boolean = false

    actual fun onPermissionResult(granted: Boolean) {
        // SMS на десктопе не поддерживается — выдавать разрешение не на что.
    }
}

@Composable
actual fun SetupSmsPermissionHandler(controller: SmsPermissionController) {
    // Обработчика запускать нечего.
}

actual class SmsSendPermissionController actual constructor() {
    actual val permissionRequested: StateFlow<Boolean> = neverRequested()

    actual fun hasPermission(): Boolean = false

    actual suspend fun requestPermission(): Boolean = false

    actual fun onPermissionResult(granted: Boolean) {
        // Отправка SMS на десктопе не поддерживается.
    }
}

@Composable
actual fun SetupSmsSendPermissionHandler(controller: SmsSendPermissionController) {
    // Обработчика запускать нечего.
}

actual class ExactAlarmPermissionController actual constructor() {
    actual val permissionRequested: StateFlow<Boolean> = neverRequested()

    actual fun hasPermission(): Boolean = true

    actual suspend fun requestPermission(): Boolean = true

    actual fun onPermissionResult(granted: Boolean) {
        // Диалога нет.
    }
}

@Composable
actual fun SetupExactAlarmPermissionHandler(controller: ExactAlarmPermissionController) {
    // Обработчика запускать нечего.
}

/**
 * Специальный доступ к уведомлениям.
 *
 * В отличие от остальных здесь `isSupported()` — false, а не true: чтение чужих
 * уведомлений на десктопе невозможно в принципе, и показывать пользователю
 * переключатель, который ничего не включает, незачем.
 */
actual class NotificationListenerController actual constructor() {
    actual fun isSupported(): Boolean = false

    actual fun isAccessGranted(): Boolean = false

    actual fun openAccessSettings() {
        // Такого экрана в системе нет.
    }
}
