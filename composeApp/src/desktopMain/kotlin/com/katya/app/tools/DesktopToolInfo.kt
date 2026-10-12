package com.katya.app.tools

import com.katya.app.network.tools.ToolInfo

/**
 * Описания десктопных инструментов для настроек.
 *
 * На Android `ToolInfo` ссылается на `nameRes`/`descriptionRes` — строковые
 * ресурсы `strings.xml`. На десктопе этого файла нет: ресурсы Compose
 * Multiplatform лежат в другом месте и подставляются иначе, а тащить `strings.xml`
 * ради двух подписей незачем. Поэтому здесь обычные строки — они идут в UI
 * списка инструментов как есть.
 */
internal val desktopToolInfo = ToolInfo(
    id = "execute_shell_command",
    name = "Выполнение команд",
    description = "Выполнить команду в оболочке этой операционной системы",
)

internal val manageProcessToolInfo = ToolInfo(
    id = "manage_process",
    name = "Фоновые процессы",
    description = "Проверить, остановить или убрать запущенный в фоне процесс",
)
