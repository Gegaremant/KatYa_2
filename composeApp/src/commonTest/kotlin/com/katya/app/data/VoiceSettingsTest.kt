package com.katya.app.data

import com.katya.app.tts.VoiceTuning
import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Новая шкала голоса и задержки.
 *
 * Здесь три вещи, которые легко сломать молча: перенос старых настроек в новую
 * шкалу, честность ручного режима и пересчёт процентов в множитель.
 */
class VoiceSettingsTest {

    private fun settingsWith(vararg entries: Pair<String, Any>): AppSettings = AppSettings(MapSettings(*entries))

    // --- Перенос старых настроек ----------------------------------------------

    @Test
    fun `old rate and pitch are converted into percents`() {
        val store = settingsWith(
            "sys_tts_rate" to 1.5f,
            "sys_tts_pitch" to 1.0f,
        )

        assertEquals(0, store.getSysTtsRatePercent(), "1.5 — это и есть норма Кати, значит ноль процентов")
        assertEquals(0, store.getSysTtsPitchPercent(), "1.0 — тоже норма, значит ноль процентов")
    }

    @Test
    fun `a doubled voice becomes a positive percent`() {
        val store = settingsWith("sys_tts_rate" to 3.0f)

        assertEquals(30, store.getSysTtsRatePercent(), "3.0 втрое быстрее нормы — это 10^0.3, то есть +30%")
    }

    @Test
    fun `a halved voice becomes a negative percent`() {
        val store = settingsWith("sys_tts_pitch" to 0.5f)

        assertEquals(-30, store.getSysTtsPitchPercent(), "0.5 втрое ниже нормы — это 10^-0.3, то есть −30%")
    }

    @Test
    fun `the migration runs once and does not overwrite a new setting`() {
        val store = settingsWith(
            "sys_tts_rate" to 1.5f,
            AppSettingsKeys.KEY_SYS_TTS_RATE_PERCENT to 25,
        )

        assertEquals(25, store.getSysTtsRatePercent(), "Новое значение должно остаться новым")
    }

    @Test
    fun `old delay in milliseconds becomes seconds`() {
        val store = settingsWith("send_delay_ms" to 2500L)

        assertEquals(2.5f, store.getSendDelaySeconds(), "2.5 секунды — это 2500 миллисекунд")
    }

    @Test
    fun `the old manual end of the slider becomes the manual switch`() {
        val store = settingsWith("send_delay_ms" to 5000L)

        assertTrue(store.isSendManualMode(), "Прежний ручной конец ползунка — это теперь тумблер")
        assertTrue(store.isManualSendMode(), "И ручной режим по факту должен включиться")
    }

    @Test
    fun `the old immediate send becomes the default half second`() {
        val store = settingsWith("send_delay_ms" to 0L)

        assertEquals(AppSettings.SEND_DELAY_DEFAULT_SECONDS, store.getSendDelaySeconds())
        assertFalse(store.isManualSendMode(), "Ноль в новой шкале — это ручной режим, а не «отправить сразу»")
    }

    // --- Ручной режим ---------------------------------------------------------

    @Test
    fun `zero delay means manual send`() {
        val store = settingsWith(AppSettingsKeys.KEY_SEND_DELAY_SECONDS to 0f)

        assertTrue(store.isManualSendMode(), "Ноль секунд — ручной режим")
        assertFalse(store.isAutoSendDelay())
        assertEquals(0L, store.getSendDelayMs(), "В ручном режиме ждать нечего")
    }

    @Test
    fun `the manual switch wins over the slider`() {
        val store = settingsWith(
            AppSettingsKeys.KEY_SEND_DELAY_SECONDS to 1.0f,
            AppSettingsKeys.KEY_SEND_MANUAL_MODE to true,
        )

        assertTrue(store.isManualSendMode())
        assertEquals(0L, store.getSendDelayMs())
    }

    @Test
    fun `a positive delay sends by itself`() {
        val store = settingsWith(AppSettingsKeys.KEY_SEND_DELAY_SECONDS to 0.5f)

        assertFalse(store.isManualSendMode())
        assertTrue(store.isAutoSendDelay())
        assertEquals(500L, store.getSendDelayMs())
    }

    @Test
    fun `the delay is clamped to its range`() {
        val store = settingsWith()

        store.setSendDelaySeconds(99f)
        assertEquals(AppSettings.SEND_DELAY_MAX_SECONDS, store.getSendDelaySeconds())

        store.setSendDelaySeconds(-5f)
        assertEquals(AppSettings.SEND_DELAY_MIN_SECONDS, store.getSendDelaySeconds())
    }

    // --- Проценты в множитель -------------------------------------------------

    @Test
    fun `zero percent is the normal voice`() {
        assertEquals(1.0f, VoiceTuning.percentToFactor(0), "Ноль процентов — это как есть")
    }

    @Test
    fun `a hundred percent is ten times faster`() {
        assertEquals(10.0f, VoiceTuning.percentToFactor(100), 0.0001f)
    }

    @Test
    fun `minus a hundred percent is ten times slower`() {
        assertEquals(0.1f, VoiceTuning.percentToFactor(-100), 0.0001f)
    }

    @Test
    fun `the percent scale is symmetric around zero`() {
        val slow = VoiceTuning.percentToFactor(-40)
        val fast = VoiceTuning.percentToFactor(40)

        assertEquals(1f / (slow * fast), 1f, 0.0001f, "Плюс и минус должны быть обратны друг другу")
    }

    // --- Восстановление из резервной копии -----------------------------------

    @Test
    fun `a backup of the new scale restores the voice as it was`() {
        val target = settingsWith()
        val backup = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"send_delay_seconds":1.5,"send_manual_mode":false,
                "sys_tts_rate_percent":25,"sys_tts_pitch_percent":-10}""",
        ).jsonObject

        val errors = target.importFromJson(backup, emptyList(), ImportSection.entries.toSet(), replace = true)

        assertEquals(0, errors)
        assertEquals(1.5f, target.getSendDelaySeconds())
        assertEquals(25, target.getSysTtsRatePercent())
        assertEquals(-10, target.getSysTtsPitchPercent())
        assertFalse(target.isManualSendMode())
    }

    @Test
    fun `a backup of the old scale restores the voice as it was`() {
        val target = settingsWith()
        val backup = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"send_delay_ms":2500,"sys_tts_rate":3.0,"sys_tts_pitch":0.5}""",
        ).jsonObject

        val errors = target.importFromJson(backup, emptyList(), ImportSection.entries.toSet(), replace = true)

        assertEquals(0, errors)
        assertEquals(2.5f, target.getSendDelaySeconds(), "2500 миллисекунд — это 2.5 секунды")
        assertEquals(30, target.getSysTtsRatePercent())
        assertEquals(-30, target.getSysTtsPitchPercent())
    }

    @Test
    fun `a backup with a zero delay restores the manual mode as well`() {
        val target = settingsWith()
        val backup = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"send_delay_seconds":0.0}""",
        ).jsonObject

        target.importFromJson(backup, emptyList(), ImportSection.entries.toSet(), replace = true)

        assertTrue(target.isManualSendMode(), "Нулевая задержка в копии — это ручной режим")
    }
}
