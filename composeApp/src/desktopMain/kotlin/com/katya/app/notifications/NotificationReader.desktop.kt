package com.katya.app.notifications

import com.katya.app.data.NotificationRecord

/**
 * Чтение чужих уведомлений — андроидная возможность.
 *
 * На Android она опирается на `BIND_NOTIFICATION_LISTENER_SERVICE`. На десктопе
 * такого сервиса нет: уведомления живут в трее, и единственный честный способ
 * их увидеть — Accessibility API, то есть чтение всего, что пользователь видит
 * на экране. Делать это по умолчанию нельзя, поэтому поддержка выключена.
 */
actual class NotificationReader actual constructor() {
    actual fun isSupported(): Boolean = false

    actual fun hasAccess(): Boolean = false

    actual suspend fun getById(id: String): NotificationRecord? = null

    actual suspend fun search(query: String, limit: Int, packageName: String?): List<NotificationRecord> = emptyList()
}
