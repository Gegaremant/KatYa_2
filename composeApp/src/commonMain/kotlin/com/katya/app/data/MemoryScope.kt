package com.katya.app.data

/**
 * Разбирает значение параметра `scope` инструментов памяти в имя роли.
 *
 * Модель пишет по-разному: с пробелами, в другом регистре, по-русски, иногда
 * присылает пустую строку, иногда — «все». Всё это должно означать одно и то же,
 * иначе персональная запись молча уедет в общее ядро, и разделение перестанет
 * работать именно там, где пользователь его просил.
 *
 * Пустая строка — осмысленный ответ «это знание общее», а не ошибка ввода, и она
 * никогда не превращается в имя роли по умолчанию: иначе «забыть, что я курю»
 * тихо сохранилось бы только Подруге.
 *
 * Неизвестное значение тоже считается общим, но не молча — [parseMemoryScope]
 * возвращает только роль, а вызывающий инструкт сообщает модели, что имя не
 * распознано, чтобы она поправилась, а не записала наугад.
 */
internal fun parseMemoryScope(raw: Any?): String? = when (val text = raw?.toString()?.trim().orEmpty()) {
    "" -> null
    else -> when (text.lowercase()) {
        "shared", "общее", "все", "всем", "all", "everyone" -> null
        "kollega", "коллега" -> AgentMode.COLLEAGUE.name
        "podruga", "подруга", "друг", "подружка" -> AgentMode.FRIEND.name
        "secretary", "секретарь", "личный секретарь", "сыскун", "детектив", "detective" -> AgentMode.SECRETARY.name
        else -> text.uppercase()
    }
}

/** Понятное имя роли для показа и для промпта. */
internal fun roleTitle(role: String?): String {
    if (role.isNullOrBlank()) return "общее"
    return AgentMode.entries.firstOrNull { it.name == role }?.title ?: role
}
