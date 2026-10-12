package com.katya.app

import com.katya.app.tools.AppLogger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Имена файлов базы, которые бэкап готов принять. */
private val DB_FILES = setOf("conversations.db", "conversations.db-wal", "conversations.db-shm")

/** Префиксы, под которыми в архиве могут лежать модели и файлы распознавания. */
private val MODEL_PREFIXES = listOf("vosk/", "models/")

/**
 * Отбрасывает путь записи, который увел бы файл за пределы каталога назначения.
 *
 * На Android та же проверка уже была: без неё запись вида `vosk/../../evil.db`
 * проходила мимо `startsWith("vosk/")` и писала куда угодно. Формат архива общий
 * с мобильной версией, значит и защита должна быть общей.
 */
private fun isSafeEntryName(name: String): Boolean {
    if (name.isEmpty() || name.startsWith("/") || name.contains('\\')) return false
    return name.split('/').none { it == ".." || it == "." }
}

internal object DesktopBackups {

    fun generate(jsonConfig: String, includeDatabase: Boolean, includeModels: Boolean): ByteArray {
        val root = File(getAppFilesDirectory())
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zos ->
            zos.putNextEntry(ZipEntry("config.json"))
            zos.write(jsonConfig.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            if (includeDatabase) {
                // WAL и SHM едут вместе с базой: без них восстановленный файл
                // может оказаться старше журнала, который к нему относится.
                for (name in DB_FILES) {
                    val file = File(root, name)
                    if (file.isFile) {
                        zos.putNextEntry(ZipEntry(name))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }

            if (includeModels) {
                for (prefix in MODEL_PREFIXES) {
                    val dir = File(root, prefix.trimEnd('/'))
                    if (!dir.isDirectory) continue
                    dir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val name = prefix + file.relativeTo(dir).path.replace(File.separatorChar, '/')
                        if (!isSafeEntryName(name)) return@forEach
                        zos.putNextEntry(ZipEntry(name))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
        return bytes.toByteArray()
    }

    fun read(zipBytes: ByteArray): BackupPayload? {
        var configJson: String? = null
        val databaseFiles = mutableMapOf<String, ByteArray>()
        val modelFiles = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && isSafeEntryName(name)) {
                    when {
                        name == "config.json" -> configJson = zis.readBytes().decodeToString()
                        name in DB_FILES -> databaseFiles[name] = zis.readBytes()
                        MODEL_PREFIXES.any { name.startsWith(it) } -> modelFiles[name] = zis.readBytes()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return if (configJson != null) BackupPayload(configJson, databaseFiles, modelFiles) else null
    }

    fun apply(payload: BackupPayload) {
        val root = File(getAppFilesDirectory()).canonicalFile
        val rootPath = root.path + File.separator

        payload.databaseFiles.forEach { (name, bytes) ->
            if (name !in DB_FILES) return@forEach
            val file = File(root, name)
            file.parentFile?.mkdirs()
            file.outputStream().use { it.write(bytes) }
        }
        // WAL от прошлой базы переигрался бы поверх восстановленной, поэтому его
        // удаляем — в том числе те файлы, которых в архиве не было.
        DB_FILES.forEach { name ->
            if (name !in payload.databaseFiles) File(root, name).delete()
        }

        payload.modelFiles.forEach { (name, bytes) ->
            if (!isSafeEntryName(name) || MODEL_PREFIXES.none { name.startsWith(it) }) return@forEach
            val file = File(root, name).canonicalFile
            if (!file.path.startsWith(rootPath)) return@forEach
            file.parentFile?.mkdirs()
            file.outputStream().use { it.write(bytes) }
        }
        AppLogger.action("Резервная копия", "восстановлена")
    }
}
