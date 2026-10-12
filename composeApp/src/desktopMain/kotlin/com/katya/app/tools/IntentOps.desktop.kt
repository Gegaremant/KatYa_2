package com.katya.app.tools

import java.net.URI

/**
 * Системные интенты — понятие Android.
 *
 * `send_android_intent` умеет открывать другие приложения по имени пакета, и на
 * десктопе этого сделать нечем: пакетов тут нет, а сопоставление имён пакетов с
 * программами — угадывание.
 *
 * Единственное, что переносится по смыслу, — открыть ссылку. Оно и сделано, но
 * по адресу `data_uri`, а не по экшну: `android.intent.action.VIEW` на Windows
 * ничего не значит, и молча проглотив его инструмент вводил бы модель в
 * заблуждение.
 */
actual class IntentOps actual constructor() {
    actual fun sendIntent(
        action: String,
        dataUri: String?,
        packageName: String?,
        extrasJson: String?,
    ): String {
        if (packageName != null) {
            return "Error: открытие приложений по имени пакета поддерживается только на Android. " +
                "Чтобы открыть сайт, передай его как data_uri."
        }
        if (action.contains("VIEW", ignoreCase = true) && !dataUri.isNullOrBlank()) {
            val opened = runCatching {
                java.awt.Desktop.getDesktop().browse(URI(dataUri))
                true
            }
                .getOrDefault(false)
            return if (opened) "Открыто: $dataUri" else "Error: не удалось открыть $dataUri"
        }
        return "Error: системные интенты поддерживаются только на Android. " +
            "Для открытия ссылки передай action=VIEW и data_uri=<адрес>."
    }
}
