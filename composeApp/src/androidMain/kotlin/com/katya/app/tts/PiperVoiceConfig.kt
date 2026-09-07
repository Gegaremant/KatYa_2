package com.katya.app.tts

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Parsed `*.onnx.json` voice descriptor that ships next to a Piper voice model.
 * Only the fields required for tflite synthesis are kept: sample rate, speaker
 * count and the phoneme-id map (phoneme symbol -> list of model ids).
 */
class PiperVoiceConfig(
    val sampleRate: Int,
    val numSpeakers: Int,
    val phonemeIdMap: Map<String, IntArray>,
    val espeakVoice: String,
) {
    fun phonemeId(phoneme: String): Int? = phonemeIdMap[phoneme]?.firstOrNull()

    companion object {
        private const val TAG = "PiperVoiceConfig"

        fun load(jsonFile: File): PiperVoiceConfig? = try {
            val root = JSONObject(jsonFile.readText())
            val audio = root.optJSONObject("audio")
            val sampleRate = audio?.optInt("sample_rate") ?: 22_050
            val numSpeakers = root.optInt("num_speakers", 1)
            val espeakVoice = root.optJSONObject("espeak")?.optString("voice") ?: "ru"
            val idsRaw = root.optJSONObject("phoneme_id_map")
            val map = LinkedHashMap<String, IntArray>()
            if (idsRaw != null) {
                val it = idsRaw.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    val arr = idsRaw.optJSONArray(key) ?: continue
                    val ids = IntArray(arr.length()) { i -> arr.optInt(i) }
                    map[key] = ids
                }
            }
            if (map.isEmpty()) {
                com.katya.app.tools.AppLogger.w(TAG, "No phoneme_id_map found in ${jsonFile.name}")
                return null
            }
            PiperVoiceConfig(sampleRate, numSpeakers, map, espeakVoice)
        } catch (e: Exception) {
            com.katya.app.tools.AppLogger.w(TAG, "Failed to parse ${jsonFile.name}: $e")
            null
        }
    }
}

/**
 * Minimal rule-based Russian text-to-phonemes converter. Piper voices expect one
 * phoneme per espeak-ng symbol; without espeak-ng we approximate each Cyrillic
 * letter with a close phoneme (это звучит разборчиво, хоть и «роботизированно»).
 * Latin letters and CJK are mapped through a small fallback table.
 */
object PiperPhonemizer {

    private val RU = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "ɡ", 'д' to "d",
        'е' to "e", 'ё' to "jo", 'ж' to "ʒ", 'з' to "z", 'и' to "i",
        'й' to "j", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "x", 'ц' to "ts", 'ч' to "tʃ",
        'ш' to "ʃ", 'щ' to "ʃ", 'ъ' to "", 'ы' to "ɨ", 'ь' to "",
        'э' to "e", 'ю' to "ju", 'я' to "ja",
    )

    private val EN_FALLBACK = mapOf(
        'a' to "a", 'b' to "b", 'c' to "k", 'd' to "d", 'e' to "e",
        'f' to "f", 'g' to "ɡ", 'h' to "x", 'i' to "i", 'j' to "dʒ",
        'k' to "k", 'l' to "l", 'm' to "m", 'n' to "n", 'o' to "o",
        'p' to "p", 'q' to "k", 'r' to "r", 's' to "s", 't' to "t",
        'u' to "u", 'v' to "v", 'w' to "w", 'x' to "ks", 'y' to "j",
        'z' to "z",
    )

    private val digits = mapOf(
        '0' to "ноль", '1' to "один", '2' to "два", '3' to "три", '4' to "четыре",
        '5' to "пять", '6' to "шесть", '7' to "семь", '8' to "восемь", '9' to "девять",
    )

    /**
     * Returns the list of Piper phoneme symbols for [text]. Whitespace becomes a
     * pause symbol (""), which the voice config maps to its own id if present.
     */
    fun phonemes(text: String): List<String> {
        val result = ArrayList<String>(text.length)
        for (raw in text.lowercase()) {
            val ch = raw.lowercaseChar()
            when {
                ch in digits -> digits[ch]?.forEach { c -> consume(c, RU, EN_FALLBACK, result) }
                ch == ' ' -> result.add(" ")
                ch.isDigit() -> continue
                else -> consume(ch, RU, EN_FALLBACK, result)
            }
        }
        return result
    }

    private fun consume(ch: Char, ru: Map<Char, String>, en: Map<Char, String>, out: MutableList<String>) {
        val mapped = ru[ch] ?: en[ch] ?: return
        mapped.forEach { symbol -> out.add(symbol.toString()) }
    }
}
