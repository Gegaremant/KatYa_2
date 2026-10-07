package com.katya.app.tools

/**
 * Убирает секреты из текста, который идёт в журнал.
 *
 * Полевая проверка 07.10 показала, зачем это нужно: приложение пишет в лог команду,
 * которой кладёт авторизацию DeepSeek внутрь песочницы, и туда попадала вся сессия —
 * `ds_session_id`, `aws_session_token`, подпись. Лог потом уходит на разбор в общий
 * каталог, то есть секрет покидает устройство целиком.
 *
 * Логика намеренно грубая: секрет не надо узнавать, надо не дать ему уехать. Поэтому
 * вычищаются три вещи — длинные base64-блоки (именно в таком виде лежит JSON с
 * cookie), значения после типичных имён параметров и заголовки авторизации.
 *
 * Ложные срабатывания допустимы: в журнале важнее отсутствие токена, чем читаемость
 * длинной команды.
 */
object SecretRedactor {

    private const val MASK = "***"

    /** base64-блок, достаточный для того, чтобы быть сессией, а не словом. */
    private val BASE64_BLOB = Regex("""[A-Za-z0-9+/]{60,}={0,2}""")

    /** Пары «имя параметра = значение», где значение — секрет. */
    private val NAMED_VALUE = Regex(
        """(?i)\b(ds_session_id|aws_session_token|session_id|access_token|refresh_token|api_key|apikey|password|passwd|secret|token|authorization|cookie|x-csrf-token|signature|sid)\s*[=:]\s*("?)[^\s"'',;&)]+""",
    )

    private val BEARER = Regex("""(?i)\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=-]{16,}""")

    fun redact(text: String): String = text
        .replace(BEARER, "$1 $MASK")
        .replace(NAMED_VALUE, "$1=$MASK")
        .replace(BASE64_BLOB, MASK)
}
