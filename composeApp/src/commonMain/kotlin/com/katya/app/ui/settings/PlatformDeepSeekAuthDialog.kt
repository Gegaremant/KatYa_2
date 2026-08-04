package com.katya.app.ui.settings

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformDeepSeekAuthDialog(
    onTokenExtracted: (String) -> Unit,
    onDismiss: () -> Unit
)
