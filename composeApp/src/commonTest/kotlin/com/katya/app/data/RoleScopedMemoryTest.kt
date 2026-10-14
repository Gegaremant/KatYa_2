package com.katya.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Разделение памяти на личности (вариант 3: общее ядро плюс своё у каждой роли).
 *
 * Проверяется то, на чём держится вся затея: чужое знание не должно просачиваться
 * в чужой промпт, а общее — обязано быть у всех. Ошибка здесь выглядит как «Катя
 * забыла то, что только что знала», и без теста обнаружится через месяц разговоров.
 */
class RoleScopedMemoryKeyTest {

    @Test
    fun `общее знание не меняет ключ`() {
        // Записи, набранные до разделения, лежат с обычным ключом. Если начать
        // добавлять к ним префикс при чтении, вся старая память станет нечитаемой.
        assertEquals("любимый_цвет", RoleScopedMemoryKey.encode("любимый_цвет", null))
        assertEquals("любимый_цвет", RoleScopedMemoryKey.encode("любимый_цвет", ""))
    }

    @Test
    fun `личное знание разводится по ролям`() {
        val stored = RoleScopedMemoryKey.encode("курит", AgentMode.FRIEND.name)
        assertTrue(stored.startsWith(RoleScopedMemoryKey.PREFIX))
        assertEquals(AgentMode.FRIEND.name to "курит", RoleScopedMemoryKey.decode(stored))
    }

    @Test
    fun `разбор не трогает ключ с двоеточием`() {
        // Модель пишет человекочитаемые ключи и может вставить двоеточие. Разбор идёт
        // по первому, поэтому «с work: понедельник» остаётся целым.
        val stored = RoleScopedMemoryKey.encode("с work: понедельник", AgentMode.SECRETARY.name)
        val (role, key) = RoleScopedMemoryKey.decode(stored)
        assertEquals(AgentMode.SECRETARY.name, role)
        assertEquals("с work: понедельник", key)
    }

    @Test
    fun `обычный ключ читается как общее знание`() {
        val (role, key) = RoleScopedMemoryKey.decode("имя")
        assertNull(role)
        assertEquals("имя", key)
    }

    @Test
    fun `чужая роль не видит чужое знание`() {
        val stored = RoleScopedMemoryKey.encode("курит", AgentMode.FRIEND.name)
        assertTrue(RoleScopedMemoryKey.isVisibleTo(stored, AgentMode.FRIEND.name))
        assertFalse(RoleScopedMemoryKey.isVisibleTo(stored, AgentMode.COLLEAGUE.name))
        assertFalse(RoleScopedMemoryKey.isVisibleTo(stored, AgentMode.SECRETARY.name))
    }

    @Test
    fun `общее знание видно всем`() {
        val stored = RoleScopedMemoryKey.encode("работает в Яндексе", null)
        for (mode in AgentMode.entries) {
            assertTrue(RoleScopedMemoryKey.isVisibleTo(stored, mode.name), "общее не видно роли ${mode.name}")
        }
    }

    @Test
    fun `разбор мусора не теряет ключ`() {
        // Обрезанный или подделанный ключ не должен приводить к потере содержимого:
        // хуже вернуть его как «общее» с исходным текстом, чем выкинуть запись.
        val (role, key) = RoleScopedMemoryKey.decode(RoleScopedMemoryKey.PREFIX + "мусор")
        assertEquals("мусор", key)
        // Роль без двоеточия — не роль.
        assertNull(role)
    }
}

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
