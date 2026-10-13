package com.katya.app.sandbox

/**
 * Состояние установки инструментов разведки, выраженное одним файлом на диске.
 *
 * Отдельный объект, а не строки в [LinuxSandboxManager], потому что решение
 * «пробовать ли сейчас» — это состояние с окном повтора и разбором значения,
 * то есть ровно то, что тихо ломается: при выключенной сети каждая команда в
 * песочнице жгла бы до пяти минут на `apt-get update`. Логика вынесена в
 * `commonMain`, чтобы её можно было покрыть тестом — `commonTest` не видит
 * `androidMain`.
 */
internal object IntrospectionMarker {
    const val OK = "installed"

    /** Префикс значения при неудаче; следом — время попытки в миллисекундах. */
    const val FAILED_PREFIX = "failed:"

    /** Пауза между попытками после неудачи — шесть часов. */
    const val RETRY_AFTER_MS = 6 * 60 * 60 * 1000L

    /**
     * Пробовать ли ставить инструменты.
     *
     * - уже установлены — нет, никогда;
     * - последняя попытка неудачна и прошло меньше [RETRY_AFTER_MS] — нет;
     * - всё остальное, включая пустое и нечитаемое значение файла, — да.
     *
     * Последний пункт осознанный: испорченный маркер должен приводить к попытке
     * поставить, а не к вечному молчанию. Маркер — ускоритель, а не источник
     * истины; наличие бинарей всё равно проверяется на диске.
     */
    fun shouldAttempt(content: String?, nowMillis: Long): Boolean {
        val text = content?.trim().orEmpty()
        if (text == OK) return false
        if (!text.startsWith(FAILED_PREFIX)) return true
        val attemptedAt = text.removePrefix(FAILED_PREFIX).toLongOrNull() ?: return true
        val elapsed = nowMillis - attemptedAt
        // Метка из будущего — часы телефона перевели, либо файлы перенесли на другое
        // устройство. Сравнение на `elapsed >= RETRY_AFTER_MS` молча блокировало бы
        // попытки до тех пор, пока реальное время не догонит выдуманное, то есть
        // при неудачных часах — до скончания века.
        if (elapsed < 0) return true
        return elapsed >= RETRY_AFTER_MS
    }

    /** Значение маркера для записи после неудачи. */
    fun failedAt(nowMillis: Long): String = FAILED_PREFIX + nowMillis
}
