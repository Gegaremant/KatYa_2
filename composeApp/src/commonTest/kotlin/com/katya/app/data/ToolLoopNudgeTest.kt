package com.katya.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The nudge detector decides whether a tool-free answer earns one extra round
 * trip. A false positive costs latency; a false negative is the bug from #15 —
 * "Хорошо" with nothing scheduled.
 */
class ToolLoopNudgeTest {

    @Test
    fun `a non-committal ack earns a nudge`() {
        assertTrue(shouldNudgeToolUse("Хорошо"))
        assertTrue(shouldNudgeToolUse("Ок"))
        assertTrue(shouldNudgeToolUse("Ок, поняла."))
        assertTrue(shouldNudgeToolUse("Сделаю!"))
        assertTrue(shouldNudgeToolUse("Секунду"))
    }

    @Test
    fun `claiming a finished action earns a nudge`() {
        assertTrue(shouldNudgeToolUse("Напоминание поставлено на 10:00"))
        assertTrue(shouldNudgeToolUse("Я создала задачу"))
        assertTrue(shouldNudgeToolUse("Отправила сообщение"))
        assertTrue(shouldNudgeToolUse("Готово, файл скачан"))
    }

    @Test
    fun `silence earns a nudge`() {
        assertTrue(shouldNudgeToolUse(""))
        assertTrue(shouldNudgeToolUse("   \n "))
    }

    @Test
    fun `a real answer does not earn one`() {
        assertFalse(shouldNudgeToolUse("В файле 12 строк, на 4-й есть опечатка в расписании."))
        assertFalse(shouldNudgeToolUse("Конечно, это интересный вопрос — давай разберём."))
        assertFalse(shouldNudgeToolUse("Ты права: напоминание на 10:00 я поставить не могу, это надо сделать в настройках."))
    }

    @Test
    fun `a long reply that merely opens with an ack is left alone`() {
        // The opener only counts for short replies; a real answer can start with
        // "Хорошо," and go on to explain something.
        assertFalse(shouldNudgeToolUse("Хорошо, тогда разложим по шагам, что именно нужно сделать тебе на телефоне."))
    }
}
