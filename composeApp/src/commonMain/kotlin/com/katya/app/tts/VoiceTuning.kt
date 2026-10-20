package com.katya.app.tts

import kotlin.math.pow

/**
 * Настройка голоса: скорость и тембр, оба — в процентах от −100 до +100.
 *
 * Ноль — это «как есть», то есть голос движка по умолчанию. ±100 — в десять раз
 * медленнее или быстрее.
 *
 * Проценты, а не множители, потому что ползунок должен означать одно и то же в
 * любой своей точке: «+10 %» к быстрому голосу и «+10 %» к медленному — это
 * разное ускорение, и множитель такие различия прячет. Процент переводится в
 * множитель через десятичный логарифм: `10^(процент/100)`, то есть −100 → 0.1,
 * 0 → 1, +100 → 10.
 *
 * Пределы намеренно не прижимаются к «человеческим» значениям: системный TTS
 * Android сам ограничивает скорость диапазоном 0.5…2, а speech-dispatcher и
 * SAPI5 — своими шкалами. Прижимать здесь означало бы обламывать шкалу под
 * худший движок и запрещать то, что другие движки умеют.
 */
data class VoiceTuning(
    val ratePercent: Int = 0,
    val pitchPercent: Int = 0,
) {
    /** Множитель скорости относительно нормы Кати: 0.1…10. */
    val rateFactor: Float get() = percentToFactor(ratePercent)

    /** Множитель тембра относительно нормы Кати: 0.1…10. */
    val pitchFactor: Float get() = percentToFactor(pitchPercent)

    companion object {
        const val MIN_PERCENT = -100
        const val MAX_PERCENT = 100

        fun clampPercent(value: Int): Int = value.coerceIn(MIN_PERCENT, MAX_PERCENT)

        /** Процент → множитель: −100 → 0.1, 0 → 1, +100 → 10. */
        fun percentToFactor(percent: Int): Float = 10f.pow(percent / 100f)
    }
}

/**
 * Что движок синтеза действительно умеет менять.
 *
 * Движок, который не умеет менять тембр, обязан сказать об этом честно, а не
 * делать вид, что ползунок работает: в macOS у `say` нет высоты тона, и
 * «тембр» там просто не о чём.
 */
data class VoiceTuningSupport(
    val rate: Boolean,
    val pitch: Boolean,
) {
    companion object {
        val NONE = VoiceTuningSupport(rate = false, pitch = false)
        val RATE_ONLY = VoiceTuningSupport(rate = true, pitch = false)
        val FULL = VoiceTuningSupport(rate = true, pitch = true)
    }
}
