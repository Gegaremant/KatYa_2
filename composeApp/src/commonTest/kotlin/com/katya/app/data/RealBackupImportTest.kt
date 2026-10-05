package com.katya.app.data

import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Проверка на настоящем бэкапе пользователя (26-10-03_Katya_backup.zip).
 *
 * Файл положили рядом как `legacy_backup_config.json`; тест пропускает себя, если его
 * нет, чтобы сборка не зависела от внешних данных.
 */
class RealBackupImportTest {

    private fun load(name: String): JsonObject? {
        val text = javaClass.classLoader?.getResourceAsStream(name)?.bufferedReader()?.use { it.readText() }
            ?: return null
        return Json.parseToJsonElement(text) as? JsonObject
    }

    @Test
    fun `a real backup previews and imports without throwing`() {
        val json = load("legacy_backup_config.json")
        if (json == null) {
            println("real backup fixture not present — skipped")
            return
        }
        val target = AppSettings(MapSettings())
        val sections = ImportSection.entries.toSet()

        // Preview must not throw: this is where a legacy-shaped value used to blow up.
        val preview = target.previewSnapshot(json, sections, ImportMode.Merge)
        println("rows=${preview.diff.size} changed=${preview.changedCount}")
        assertTrue(preview.diff.isNotEmpty(), "A real backup must produce a real diff")

        val errors = target.importFromJson(json, emptyList(), sections, replace = true)
        println("errors=$errors")
        assertEquals(0, errors, "A real backup must import cleanly")

        // The essentials from that actual file.
        assertTrue(target.getSoulText().isNotBlank(), "Душа не восстановилась")
        assertTrue(target.getVlessUri().isNotBlank(), "VLESS-адрес не восстановился")
        assertTrue(target.getServerIp().isNotBlank(), "SSH-сервер не восстановился")
        assertTrue(target.getConfiguredServiceInstances().isNotEmpty(), "Сервисы не восстановились")
        assertTrue(target.getMcpServersJson().isNotBlank(), "MCP-серверы не восстановились")
        assertTrue(target.isToolEnabled("web_search"), "Инструменты не восстановились")
    }

    @Test
    fun `importing the real backup twice changes nothing the second time`() {
        val json = load("legacy_backup_config.json")
        if (json == null) {
            println("real backup fixture not present — skipped")
            return
        }
        val target = AppSettings(MapSettings())
        val sections = ImportSection.entries.toSet()

        target.importFromJson(json, emptyList(), sections, replace = true)

        // Re-import the very same file: this is the user's own export coming back, and
        // it must report nothing to do (feedback #2).
        val second = AppSettings(MapSettings())
        second.importFromJson(json, emptyList(), sections, replace = true)
        val afterFirst = target.exportSnapshot()
        val afterSecond = second.exportSnapshot()
        assertEquals(afterFirst.toMap(), afterSecond.toMap(), "Импорт не должен быть нестабильным")

        val preview = second.previewSnapshot(json, sections, ImportMode.Merge)
        preview.diff.filter { it.changed }.forEach { println("CHANGED ${it.key}: '${it.current}' -> '${it.incoming}'") }
        println("round trip changed=${preview.changedCount} of ${preview.diff.size}")
        assertEquals(0, preview.changedCount, "Свой же бэкап не должен предлагать изменения")
    }
}
