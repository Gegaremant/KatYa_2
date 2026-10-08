package com.katya.app.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Что уходит в голосовой движок для одной ассистентской записи.
 *
 * Feedback 08.10 п.2: озвучка размышлений не работала никогда. Две независимые
 * причины, и обе молчаливые: `ChatUiState.voiceThoughtsEnabled` был объявлен как
 * `false` и нигде не заполнялся, а условие в экране смотрело на `isThinking`
 * последней записи — а размышление приходит как `reasoningContent` раньше ответа,
 * поэтому к моменту срабатывания последней уже был ответ.
 *
 * Порядок «сначала размышление, потом ответ» закреплён тестом: объяснение хода
 * решения после вывода не имеет смысла.
 */
class SpeechTextTest {

    @Test
    fun `размышление говорится вместе с ответом и идёт первым`() {
        val text = buildSpeechText(
            reasoning = "Проверю, что прокси поднялся.",
            answer = "Прокси работает.",
            speakReasoning = true,
            speakAnswer = true,
        )
        assertEquals("Проверю, что прокси поднялся.\n\nПрокси работает.", text)
    }

    @Test
    fun `выключенная озвучка размышлений оставляет только ответ`() {
        // Переключатель «Показ и озвучка размышлений» выключен — размышление видно в
        // чате, но вслух не идёт.
        val text = buildSpeechText(
            reasoning = "Проверю, что прокси поднялся.",
            answer = "Прокси работает.",
            speakReasoning = false,
            speakAnswer = true,
        )
        assertEquals("Прокси работает.", text)
    }

    @Test
    fun `выключенный голос оставляет только размышление`() {
        val text = buildSpeechText(
            reasoning = "Думаю о ответе.",
            answer = "Вот ответ.",
            speakReasoning = true,
            speakAnswer = false,
        )
        assertEquals("Думаю о ответе.", text)
    }

    @Test
    fun `без размышления говорится только ответ`() {
        val text = buildSpeechText(
            reasoning = null,
            answer = "Просто ответ.",
            speakReasoning = true,
            speakAnswer = true,
        )
        assertEquals("Просто ответ.", text)
    }

    @Test
    fun `пустые части не превращаются в пробелы`() {
        // Пустое размышление или пустой ответ не должны давать «\n\n» в начале или в
        // середине речи — движок такое читает как паузу и спотыкается.
        val blankReasoning = buildSpeechText(
            reasoning = "   ",
            answer = "Ответ.",
            speakReasoning = true,
            speakAnswer = true,
        )
        assertEquals("Ответ.", blankReasoning)

        val blankAnswer = buildSpeechText(
            reasoning = "Мысль.",
            answer = "",
            speakReasoning = true,
            speakAnswer = true,
        )
        assertEquals("Мысль.", blankAnswer)
    }

    @Test
    fun `без размышлений и без ответа голос молчит`() {
        assertNull(
            buildSpeechText(reasoning = null, answer = "", speakReasoning = true, speakAnswer = true),
        )
        assertNull(
            buildSpeechText(reasoning = "", answer = "  ", speakReasoning = false, speakAnswer = false),
        )
    }

    @Test
    fun `разметка не попадает в речь`() {
        // Голос должен читать «жизнь», а не «звёздочка жизн…». Markdown разбирается до
        // отправки в движок.
        val text = buildSpeechText(
            reasoning = "Проверю **список** дел.",
            answer = "Готово: `apt install` прошёл.",
            speakReasoning = true,
            speakAnswer = true,
        )
        assertTrue(text != null)
        assertTrue("**" !in text!!, "жирная разметка не должна звучать: $text")
        assertTrue("`" !in text, "моноширинная разметка не должна звучать: $text")
    }
}
