package com.katya.app.sms

import com.katya.app.data.SmsMessage

/**
 * SMS на десктопе не существует.
 *
 * Не «заглушка, которую допишем потом», а осознанный отказ: на Windows есть
 * приложения для чтения SMS, но публичного API, на который можно опереться, у
 * них нет, а выдавать за поддержку чтение чужих сообщений нельзя. Пустые списки и
 * `false` — правильный ответ на «можно ли читать SMS здесь».
 */
actual class SmsReader actual constructor() {
    actual fun isSupported(): Boolean = false

    actual fun hasPermission(): Boolean = false

    actual suspend fun readInboxSince(lastSeenId: Long, limit: Int): List<SmsMessage> = emptyList()

    actual suspend fun readById(id: Long): SmsMessage? = null

    actual suspend fun search(query: String, limit: Int): List<SmsMessage> = emptyList()

    actual suspend fun currentMaxInboxId(): Long = 0L
}

/**
 * Отправка SMS на десктопе не поддерживается, и причина называется прямо:
 * модель вернёт пользователю текст ошибки вместо «успешно отправлено».
 */
actual class SmsSender actual constructor() {
    actual fun hasPermission(): Boolean = false

    actual suspend fun send(address: String, body: String): SmsSendResult = SmsSendResult.Failure("Отправка SMS поддерживается только на Android")
}
