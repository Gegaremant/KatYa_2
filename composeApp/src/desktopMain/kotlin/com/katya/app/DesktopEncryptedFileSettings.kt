package com.katya.app

import com.katya.app.tools.AppLogger
import com.russhwolf.settings.Settings
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрованные настройки для десктопа.
 *
 * На Android это `EncryptedSharedPreferences` поверх KeyStore: ключ живёт в
 * железе, из приложения его достать нельзя. На десктопе Keystore нет, поэтому ключ
 * хранится рядом, в отдельном файле с правами 600 — по сути тот же уровень
 * защиты, что даёт `0600` у SSH-ключа. Зашифровано AES-256-GCM: подмена файла
 * ловится проверкой целостности, в отличие от CBC.
 *
 * Объём секретов тут небольшой (ключи API, пароли почты), поэтому весь набор
 * держится в памяти и пишется целиком при каждом изменении. Это проще и надёжнее
 * частичных записей в файл, который может остаться в промежуточном состоянии.
 */
internal class DesktopEncryptedFileSettings(
    private val file: File,
) : Settings {

    private val cipher = Cipher.getInstance(TRANSFORMATION)

    private val values: MutableMap<String, String> = load()

    @Volatile
    private var inWrite = false

    override val keys: Set<String> get() = values.keys.toSet()

    override val size: Int get() = values.size

    override fun clear() {
        values.clear()
        persist()
    }

    override fun remove(key: String) {
        if (values.remove(key) != null) persist()
    }

    override fun hasKey(key: String): Boolean = values.containsKey(key)

    override fun putInt(key: String, value: Int) = put(key, value.toString())

    override fun getInt(key: String, defaultValue: Int): Int = get(key)?.toIntOrNull() ?: defaultValue

    override fun putLong(key: String, value: Long) = put(key, value.toString())

    override fun getLong(key: String, defaultValue: Long): Long = get(key)?.toLongOrNull() ?: defaultValue

    override fun putString(key: String, value: String) = put(key, value)

    override fun getString(key: String, defaultValue: String): String = get(key) ?: defaultValue

    override fun getStringOrNull(key: String): String? = get(key)

    override fun putDouble(key: String, value: Double) = put(key, value.toString())

    override fun getDouble(key: String, defaultValue: Double): Double = get(key)?.toDoubleOrNull() ?: defaultValue

    override fun getDoubleOrNull(key: String): Double? = get(key)?.toDoubleOrNull()

    override fun getIntOrNull(key: String): Int? = get(key)?.toIntOrNull()

    override fun getLongOrNull(key: String): Long? = get(key)?.toLongOrNull()

    override fun getFloatOrNull(key: String): Float? = get(key)?.toFloatOrNull()

    override fun getBooleanOrNull(key: String): Boolean? = when (get(key)?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    override fun putBoolean(key: String, value: Boolean) = put(key, value.toString())

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = when (get(key)?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> defaultValue
    }

    override fun putFloat(key: String, value: Float) = put(key, value.toString())

    override fun getFloat(key: String, defaultValue: Float): Float = get(key)?.toFloatOrNull() ?: defaultValue

    private fun get(key: String): String? = values[key]

    private fun put(key: String, value: String) {
        values[key] = value
        persist()
    }

    /**
     * Расшифровка целиком, с понятной причиной отказа.
     *
     * Раньше здесь был try-catch вокруг вызова, который сам мог бросить, и пустой
     * результат читался как «настроек нет» — то есть битый файл тихо выглядел как
     * свежая установка, и пользователь не понимал, куда делись ключи. Теперь
     * неудача удаляет файл и пишется в журнал.
     */
    private fun load(): MutableMap<String, String> {
        if (!file.isFile) return mutableMapOf()
        return runCatching {
            val bytes = file.readBytes()
            if (bytes.size <= IV_LENGTH) error("файл короче вектора инициализации")
            val iv = bytes.copyOfRange(0, IV_LENGTH)
            val body = bytes.copyOfRange(IV_LENGTH, bytes.size)
            cipher.init(Cipher.DECRYPT_MODE, loadKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(body).decodeToString()
                .lineSequence()
                .mapNotNull { line ->
                    val tab = line.indexOf('\t')
                    if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
                }.toMap()
                .toMutableMap()
        }.getOrElse { e ->
            // Ключ сменили, данные побиты или файл не наш. Пользователь теряет
            // сохранённые ключи, но не получает падение на старте: приложение
            // обязано запуститься.
            AppLogger.w("Настройки", "файл секретов не читается (${e.message}) — начинаю с чистых")
            file.delete()
            mutableMapOf()
        }
    }

    private fun persist() {
        if (inWrite) return
        inWrite = true
        try {
            file.parentFile?.mkdirs()
            val plain = values.entries.joinToString("\n") { "${it.key}\t${it.value}" }
            val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, loadKey(), GCMParameterSpec(TAG_BITS, iv))
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            // Пишем во временный файл и переименовываем: падение посреди записи
            // иначе оставило бы усечённый файл, который больше не расшифруется.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(iv + body)
            if (!tmp.renameTo(file)) {
                file.writeBytes(tmp.readBytes())
                tmp.delete()
            }
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        } catch (e: Exception) {
            AppLogger.e("Настройки", "не удалось записать секреты: ${e.message}")
        } finally {
            inWrite = false
        }
    }

    private fun loadKey(): SecretKey {
        if (!KEY_FILE.isFile) {
            val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
            KEY_FILE.parentFile?.mkdirs()
            KEY_FILE.writeBytes(key)
            KEY_FILE.setReadable(false, false)
            KEY_FILE.setReadable(true, true)
        }
        return SecretKeySpec(KEY_FILE.readBytes(), "AES")
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128

        /**
         * Ключ лежит рядом с данными — это честное ограничение десктопа, а не
         * недоработка: без системного хранилища ключ некуда спрятать. Права 600
         * закрывают его от других пользователей системы.
         */
        val KEY_FILE: File by lazy {
            File(System.getProperty("user.home"), ".katya/secure-prefs.key")
        }
    }
}
