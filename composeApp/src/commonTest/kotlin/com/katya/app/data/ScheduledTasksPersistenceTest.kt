package com.katya.app.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Регрессии на тихую потерю данных (отчёты 06.10 п.4 и 07.10 п.7).
 *
 * Сценарий, который здесь воспроизводится дословно: список задач стирается при
 * обновлении, новая задача после этого создаётся успешно, а старая пропадает.
 */
class ScheduledTasksPersistenceTest {

    private val json = SharedJson

    private fun settingsWith(value: String): AppSettings = AppSettings(MapSettings(AppSettingsKeys.KEY_SCHEDULED_TASKS to value))

    private fun taskJson(id: String, description: String): String {
        val task = ScheduledTask(
            id = id,
            description = description,
            prompt = "сделай $description",
            scheduledAtEpochMs = 1_700_000_000_000L,
            createdAtEpochMs = 1_700_000_000_000L,
        )
        return json.encodeToString(task)
    }

    @Test
    fun `пустая строка не пишется в настройки`() {
        // Раньше именно "" доезжал до хранилища. Дальше TaskStore отдавал его в
        // разбор, разбор падал, и список становился пустым — а следующая запись
        // стирала всё, что было.
        val settings = AppSettings(MapSettings())
        settings.setScheduledTasksJson("")
        assertEquals("[]", settings.getScheduledTasksJson())
    }

    @Test
    fun `строка из пробелов тоже считается пустой`() {
        val settings = AppSettings(MapSettings())
        settings.setScheduledTasksJson("   \n ")
        assertEquals("[]", settings.getScheduledTasksJson())
    }

    @Test
    fun `настоящий список пишется без изменений`() {
        val payload = "[${taskJson("a", "полить цветы")}]"
        val settings = AppSettings(MapSettings())
        settings.setScheduledTasksJson(payload)
        assertEquals(payload, settings.getScheduledTasksJson())
    }

    @Test
    fun `битое значение не затирается добавлением задачи`() {
        // Главная проверка: значение, которое не читается, нельзя переписать.
        // Раньше addTask писал поверх него список из одного элемента, и старая
        // задача исчезала навсегда.
        //
        // Читается тот же экземпляр AppSettings, а не новый: иначе проверка
        // смотрела бы на заведомо нетронутое значение и всегда проходила.
        val settings = settingsWith("не json вовсе")
        val store = TaskStore(settings)
        assertTrue(store.getAllTasks().isEmpty())

        runTest { store.addTask("полить цветы", "полить цветы", 1_700_000_000_000L) }

        assertEquals("не json вовсе", settings.getScheduledTasksJson())
    }

    @Test
    fun `быстрые действия не пишутся пустой строкой`() {
        val settings = AppSettings(MapSettings())
        settings.setQuickActionsJson("")
        assertEquals("[]", settings.getQuickActionsJson())
        settings.setQuickActionsJson("  ")
        assertEquals("[]", settings.getQuickActionsJson())
    }

    @Test
    fun `быстрые действия переживают цикл записи`() {
        val payload = """[{"id":"qa1","label":"Сводка дня","command":"сделай сводку"}]"""
        val settings = AppSettings(MapSettings())
        settings.setQuickActionsJson(payload)
        assertEquals(payload, settings.getQuickActionsJson())
    }

    @Test
    fun `список читается после нормализации пустой строки`() {
        val store = TaskStore(settingsWith(""))
        assertTrue(store.getAllTasks().isEmpty())

        runTest { store.addTask("полить цветы", "полить цветы", 1_700_000_000_000L) }
        // Запись прошла — значит значение перестало быть «нечитаемым».
        assertEquals(1, store.getAllTasks().size)
    }

    @Test
    fun `одна нечитаемая строка не роняет весь список`() {
        // Противоположная сторона того же случая: битая строка внутри списка
        // не должна уничтожать остальные задачи.
        val good = taskJson("good", "полить цветы")
        val store = TaskStore(settingsWith("""[$good, {"id":"broken","extra":42}]"""))
        val tasks = store.getAllTasks()
        assertEquals(1, tasks.size)
        assertEquals("good", tasks.first().id)
    }

    @Test
    fun `прочитанный список можно дополнять без потерь`() {
        val first = taskJson("first", "полить цветы")
        val settings = settingsWith("[$first]")
        val store = TaskStore(settings)
        assertEquals(1, store.getAllTasks().size)

        runTest { store.addTask("выключить свет", "выключи свет", 1_700_000_100_000L) }
        val after = TaskStore(settings).getAllTasks()
        assertEquals(2, after.size)
        assertTrue(after.any { it.id == "first" })
        assertFalse(after.any { it.description.isBlank() })
    }
}
