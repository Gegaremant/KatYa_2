package com.katya.app.notifications

import com.katya.app.data.NotificationRecord

/**
 * Multiplatform notification reader. Only the Android FOSS build returns real data —
 * the feature is gated by the `KatyaNotificationListenerService` being declared in the
 * merged manifest, which is only the case for the `foss` product flavor. iOS, desktop,
 * and wasm return no-op stubs (notification access is either restricted or doesn't
 * exist on those platforms).
 *
 * Unlike [com.katya.app.sms.SmsReader] which queries the system content
 * provider, this reads from the in-process [com.katya.app.data.NotificationStore]
 * — the listener service writes records there as they arrive.
 */
expect class NotificationReader() {
    /** True when this build can ever capture notifications — i.e. Android + listener registered. */
    fun isSupported(): Boolean

    /** True when [isSupported] and the user has granted notification listener access. */
    fun hasAccess(): Boolean

    /** Fetch a single record by `StatusBarNotification.key`. Null if not found. */
    suspend fun getById(id: String): NotificationRecord?

    /**
     * Full-text search across `appLabel`, `title`, and `text`. Newest-first, capped at [limit].
     * Optional [packageName] filter restricts to a single app.
     */
    suspend fun search(query: String, limit: Int, packageName: String?): List<NotificationRecord>
}
