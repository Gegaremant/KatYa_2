package com.katya.app.ui.settings

import androidx.compose.runtime.Composable

/**
 * Вход в DeepSeek на десктопе не подключён.
 *
 * На Android это невидимый WebView, который проходит авторизацию и отдаёт токен
 * с cookie. На десктопе встроенного окна авторизации нет, а открывать браузер и
 * вылавливать токен из его профиля — чтение чужих данных, на которое
 * пользователь не подписывался.
 *
 * Статус сообщается прямо: «нужен ключ DeepSeek», а не пустое окно, которое
 * выглядит как зависшее.
 */
@Composable
actual fun PlatformDeepSeekAuthDialog(
    onTokenExtracted: (DeepSeekAuthSession) -> Unit,
    onStatus: (String) -> Unit,
    onDismiss: () -> Unit,
    initialEmail: String,
    initialPassword: String,
) {
    onStatus("Вход через браузер поддерживается только на Android — укажите API-ключ вручную")
    onDismiss()
}
