package com.katya.app.data

import com.katya.app.getAppFilesDirectory
import java.io.File

private const val LEGACY_FILE_NAME = "conversations.enc"

/**
 * Старое зашифрованное хранилище диалогов.
 *
 * На Android файл лежит во внутреннем хранилище и переносился вместе с
 * приложением. На десктопе внутреннего хранилища нет — конфигурация живёт в
 * `~/.katya`, и туда же кладётся этот файл, чтобы он находился в том же месте,
 * что и всё остальное состояние.
 *
 * Формат тот же, поэтому резервная копия с телефона читается на компьютере и
 * наоборот.
 */
actual fun readLegacyConversationFile(): ByteArray? {
    val file = File(getAppFilesDirectory(), LEGACY_FILE_NAME)
    return if (file.isFile) file.readBytes() else null
}

actual fun deleteLegacyConversationFile() {
    File(getAppFilesDirectory(), LEGACY_FILE_NAME).takeIf { it.isFile }?.delete()
}
