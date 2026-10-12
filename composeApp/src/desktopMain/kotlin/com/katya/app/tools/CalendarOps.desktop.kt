package com.katya.app.tools

import java.io.File

/**
 * Календарь на десктопе.
 *
 * На Android это был ContentResolver поверх системного календаря. На дескотпе
 * единого календаря нет: у Windows свой Outlook, у Linux — файл `.ics` в
 * `~/.local/share/evolution/calendar`, у пользователя может быть ничего из
 * перечисленного.
 *
 * Поэтому здесь не пишется выдуманный успех: инструмент возвращает честный отказ
 * с объяснением, и модель скажет пользователю правду вместо «событие создано».
 */
actual class CalendarOps actual constructor() {

    actual fun getEvents(daysAhead: Int): String = "Error: чтение системного календаря поддерживается только на Android"

    actual fun createEvent(
        title: String,
        description: String,
        startTimeMs: Long,
        endTimeMs: Long,
    ): String {
        // Пишем `.ics` — это единственный формат, который понимают почти все
        // календари на десктопе, и он не требует установленного приложения.
        // Пользователь получает настоящий файл вместо сообщения об ошибке.
        val file = writeIcs(title, description, startTimeMs, endTimeMs)
            ?: return "Error: не удалось создать файл события"
        return "Файл события создан: ${file.absolutePath}. Открой его в своём календаре."
    }

    private fun writeIcs(title: String, description: String, startMs: Long, endMs: Long): File? = runCatching {
        val dir = File(com.katya.app.getAppFilesDirectory(), "calendar")
        dir.mkdirs()
        val file = File(dir, "katya-${System.currentTimeMillis()}.ics")
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(java.time.ZoneOffset.UTC)
        val body = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//KatYa//RU")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:${System.currentTimeMillis()}@katya")
            appendLine("DTSTAMP:${fmt.format(java.time.Instant.now())}")
            appendLine("DTSTART:${fmt.format(java.time.Instant.ofEpochMilli(startMs))}")
            appendLine("DTEND:${fmt.format(java.time.Instant.ofEpochMilli(endMs))}")
            appendLine("SUMMARY:${title.replace("\n", " ")}")
            if (description.isNotBlank()) {
                appendLine("DESCRIPTION:${description.replace("\n", "\\n")}")
            }
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }
        file.writeText(body)
        file
    }.getOrNull()
}
