package com.katya.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «Видимость работы» показывает пользователю, что Катя вообще делает.
 *
 * Feedback 08.10 п.3: в чате была только английская подпись инструмента
 * (`Execute Shell Command`), и по ней нельзя было понять, какая команда уходит в
 * песочницу. Подпись с командой — то, что читается как «что она делает и зачем», и
 * её поведение закреплено здесь, потому что ошибка здесь тихая: строка просто
 * выглядит неправильно, и никто не падает.
 */
class ToolCommandLabelTest {

    @Test
    fun `команда дописывается к подписи инструмента`() {
        val label = "Выполняю команду".withCommandFrom(
            "execute_shell_command",
            """{"command":"apt-get install -y nodejs npm git","timeout":30}""",
        )
        assertEquals("Выполняю команду · apt-get install -y nodejs npm git", label)
    }

    @Test
    fun `команда на телефоне тоже показывается`() {
        val label = "Выполняю команду на телефоне".withCommandFrom(
            "host_shell_command",
            """{"command":"id","use_root":true}""",
        )
        assertEquals("Выполняю команду на телефоне · id", label)
    }

    @Test
    fun `путь к файлу показывается для чтения`() {
        val label = "Читаю файл".withCommandFrom("read_file", """{"path":"/sdcard/KATYA_BRAIN.md"}""")
        assertEquals("Читаю файл · /sdcard/KATYA_BRAIN.md", label)
    }

    @Test
    fun `несколько строк команды схлопываются в одну`() {
        val label = "Выполняю команду".withCommandFrom(
            "execute_shell_command",
            """{"command":"cd /root\n  && ls -la\n  && echo done"}""",
        )
        // Многострочная команда в однострочной подписи разъехалась бы по ширине.
        assertEquals("Выполняю команду · cd /root && ls -la && echo done", label)
    }

    @Test
    fun `длинная команда обрезается с многоточием`() {
        // Слова через пробел, а не один длинный токен: длинный токен маскируется как
        // base64-секрет, и проверялась бы не обрезка, а маскировка.
        val long = (1..40).joinToString(" ") { "apt-get-опция-$it" }
        val label = "Выполняю команду".withCommandFrom("execute_shell_command", """{"command":"$long"}""")
        val detail = label.removePrefix("Выполняю команду · ")
        assertTrue(detail.length <= 120, "подпись не должна быть длиннее 120 символов, получилось ${detail.length}")
        assertTrue(detail.endsWith("…"), "обрезка должна быть видна")
    }

    @Test
    fun `инструмент без команды остаётся без неё`() {
        // Почта, SMS, уведомления: показывать «Compose Email · …» нечего, и выдуманная
        // деталь в подписи хуже, чем её отсутствие.
        val label = "Пишу письмо".withCommandFrom("compose_email", """{"to":"a@b.c","subject":"Привет"}""")
        assertEquals("Пишу письмо", label)
    }

    @Test
    fun `битые аргументы не ломают подпись`() {
        // Модель может прислать не-JSON. Раньше это уронило бы разбор — теперь подпись
        // просто остаётся прежней.
        assertEquals("Выполняю команду", "Выполняю команду".withCommandFrom("execute_shell_command", "{не json"))
        assertEquals("Выполняю команду", "Выполняю команду".withCommandFrom("execute_shell_command", ""))
        assertEquals("Выполняю команду", "Выполняю команду".withCommandFrom("execute_shell_command", """{"command":"   "}"""))
    }

    @Test
    fun `команда без разбора строк берётся из первого подходящего ключа`() {
        val label = "Ищу в сети".withCommandFrom("search_web", """{"query":"proot android selinux","limit":5}""")
        assertEquals("Ищу в сети · proot android selinux", label)
    }

    @Test
    fun `деталь команды маскирует секреты`() {
        // Feedback 08.10: команда уходит в интерфейс и в журнал, а в команде может быть
        // токен. Маскирование здесь не «хороший тон», а причина не публиковать ключи.
        val label = "Выполняю команду".withCommandFrom(
            "execute_shell_command",
            """{"command":"curl -H 'Authorization: Bearer sk-secret1234567890' https://api.deepseek.com"}""",
        )
        assertTrue("sk-secret1234567890" !in label, "токен не должен попадать в подпись команды: $label")
    }

    @Test
    fun `пустая деталь не добавляет разделитель`() {
        // Разделитель «·» без ничего после него выглядит как обрыв фразы.
        assertEquals("Выполняю команду", "Выполняю команду".withCommandFrom("execute_shell_command", """{"timeout":30}"""))
    }

    @Test
    fun `деталь недоступна вне команды`() {
        assertNull(commandDetailFor("compose_email", """{"to":"a@b.c"}"""))
    }
}
