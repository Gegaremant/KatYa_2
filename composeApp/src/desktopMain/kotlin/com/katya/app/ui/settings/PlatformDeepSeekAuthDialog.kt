package com.katya.app.ui.settings

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformDeepSeekAuthDialog(
    onTokenExtracted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Desktop not supported for DeepSeek Auth WebView yet
    onDismiss()
}
