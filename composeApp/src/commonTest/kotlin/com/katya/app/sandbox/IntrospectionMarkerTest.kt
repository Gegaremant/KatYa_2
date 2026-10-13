package com.katya.app.sandbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntrospectionMarkerTest {

    private val hour = 60L * 60L * 1000L

    @Test
    fun `установленные инструменты не ставятся заново`() {
        assertFalse(IntrospectionMarker.shouldAttempt("installed", nowMillis = 10L * hour))
    }

    @Test
    fun `пустой маркер означает попытку`() {
        // Маркер — ускоритель, а не источник истины: его отсутствие обязано
        // приводить к попытке, иначе испорченный или удалённый файл навсегда
        // оставил бы песочницу без `ss`.
        assertTrue(IntrospectionMarker.shouldAttempt(null, nowMillis = 0L))
        assertTrue(IntrospectionMarker.shouldAttempt("", nowMillis = 0L))
        assertTrue(IntrospectionMarker.shouldAttempt("   \n", nowMillis = 0L))
    }

    @Test
    fun `после неудачи ждём окно перед следующей попыткой`() {
        val failed = IntrospectionMarker.failedAt(100L * hour)
        assertFalse(IntrospectionMarker.shouldAttempt(failed, nowMillis = 100L * hour))
        assertFalse(IntrospectionMarker.shouldAttempt(failed, nowMillis = 100L * hour + 1))
        assertFalse(IntrospectionMarker.shouldAttempt(failed, nowMillis = 101L * hour))
        assertTrue(IntrospectionMarker.shouldAttempt(failed, nowMillis = 100L * hour + IntrospectionMarker.RETRY_AFTER_MS))
        assertTrue(IntrospectionMarker.shouldAttempt(failed, nowMillis = 106L * hour))
    }

    @Test
    fun `окно повтора равно шести часам`() {
        assertEquals(6L * hour, IntrospectionMarker.RETRY_AFTER_MS)
    }

    @Test
    fun `метка из будущего не блокирует попытки навсегда`() {
        // Если часы телефона перевели или переехали на другое устройство, метка
        // неудачи может оказаться позже текущего времени. Считать это «только что
        // пробовали» нельзя — иначе песочница молчала бы до конца часовых.
        val future = IntrospectionMarker.failedAt(50L * hour)
        assertTrue(IntrospectionMarker.shouldAttempt(future, nowMillis = 10L * hour))
    }

    @Test
    fun `испорченное значение маркера не блокирует попытку`() {
        assertTrue(IntrospectionMarker.shouldAttempt("failed:не-число", nowMillis = 0L))
        assertTrue(IntrospectionMarker.shouldAttempt("installed ", nowMillis = 0L).not())
        assertTrue(IntrospectionMarker.shouldAttempt("что-то другое", nowMillis = 0L))
    }

    @Test
    fun `значение неудачи разбирается обратно`() {
        assertEquals("failed:12345", IntrospectionMarker.failedAt(12345L))
    }
}
