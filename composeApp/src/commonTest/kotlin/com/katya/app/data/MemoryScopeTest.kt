package com.katya.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Разделение памяти на личности (вариант 3: общее ядро плюс своё у каждой роли).
 *
 * Знание адресуется парой «ключ + роль» в одноимённых столбцах таблицы
 * `memoryEntry` (составной первичный ключ `(key, role)`). Роль в ключе больше
 * не кодируется — для этого есть настоящий столбец, введённый миграцией `3.sqm`.
 */
class MemoryScopeTest {

    @Test
    fun `пустая область означает общее знание`() {
        assertNull(parseMemoryScope(null))
        assertNull(parseMemoryScope(""))
        assertNull(parseMemoryScope("   "))
    }

    @Test
    fun `русские и английские имена ролей равнозначны`() {
        val expected = AgentMode.FRIEND.name
        for (raw in listOf("FRIEND", "friend", "Подруга", "подруга", " ПОДРУГА ")) {
            assertEquals(expected, parseMemoryScope(raw), "не разобрано: $raw")
        }
    }

    @Test
    fun `старое имя сыскуна ведёт в секретаря`() {
        // «Сыскун» — прежнее название этого режима. Если модель пришлёт его из
        // привычки, знание должно уехать к нужной личности, а не в неизвестность.
        for (raw in listOf("DETECTIVE", "Сыскун", "детектив", "секретарь", "Личный секретарь")) {
            assertEquals(AgentMode.SECRETARY.name, parseMemoryScope(raw), "не разобрано: $raw")
        }
    }

    @Test
    fun `слова про общность дают общее знание`() {
        for (raw in listOf("shared", "общее", "все", "всем", "all", "everyone")) {
            assertNull(parseMemoryScope(raw), "разобрано не как общее: $raw")
        }
    }

    @Test
    fun `неизвестное имя не молча становится общим`() {
        // Плохая строка не должна уехать в общее ядро: иначе пользователь, попросивший
        // «это только Подруге», получил бы знание у всех, и молча.
        assertEquals("НЕПОНЯТНО", parseMemoryScope("НЕПОНЯТНО"))
    }

    @Test
    fun `подпись роли читаема`() {
        assertEquals("общее", roleTitle(null))
        assertEquals("Коллега", roleTitle(AgentMode.COLLEAGUE.name))
        assertEquals("Подруга", roleTitle(AgentMode.FRIEND.name))
        assertEquals("Личный секретарь", roleTitle(AgentMode.SECRETARY.name))
    }
}

class AgentModeMigrationTest {

    @Test
    fun `старые значения читаются как новые роли`() {
        // Переименование без этого разбора обнулило бы режим у всех, кто им
        // пользовался, без единого сообщения.
        assertEquals(AgentMode.COLLEAGUE, AgentMode.fromStored("SHORT"))
        assertEquals(AgentMode.FRIEND, AgentMode.fromStored("CONVERSATIONAL"))
        assertEquals(AgentMode.SECRETARY, AgentMode.fromStored("DETECTIVE"))
    }

    @Test
    fun `новые значения читаются как есть`() {
        for (mode in AgentMode.entries) {
            assertEquals(mode, AgentMode.fromStored(mode.name))
        }
    }

    @Test
    fun `мусор и пусто дают Подругу`() {
        // Дефолт совпадает со старым дефолтом, поэтому переименование не должно быть
        // заметно тем, кто режим не выбирал.
        assertEquals(AgentMode.FRIEND, AgentMode.fromStored(null))
        assertEquals(AgentMode.FRIEND, AgentMode.fromStored(""))
        assertEquals(AgentMode.FRIEND, AgentMode.fromStored("чё-нибудь"))
    }
}
