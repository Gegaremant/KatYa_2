package com.katya.app.ui.settings

import androidx.compose.runtime.Composable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Full DeepSeek web session captured from the sign-in WebView.
 *
 * [token]   — Bearer/JWT session token (the value of the `user_session` cookie).
 * [cookie]  — full cookie string for chat.deepseek.com (`name=value; name=value`).
 * [hifDliq]/[hifLeim] — anti-bot headers (`x-hif-dliq`/`x-hif-leim`) that the
 *                       DeepSeek web client sends with its API requests. Newer
 *                       FreeDeepseekAPI versions require them; without them token
 *                       auth fails even though the token itself is valid.
 * [wasmUrl] — sha3 PoW wasm bundle used by the server-side proof-of-work solver.
 *
 * Field names map 1:1 to `deepseek-auth.json` keys (`hif_dliq`/`hif_leim` with
 * underscores), so serializing this class yields a valid auth file.
 */
@Serializable
data class DeepSeekAuthSession(
    val token: String = "",
    val cookie: String = "",
    @SerialName("hif_dliq") val hifDliq: String = "",
    @SerialName("hif_leim") val hifLeim: String = "",
    val wasmUrl: String = "https://fe-static.deepseek.com/chat/static/sha3_wasm_bg.7b9ca65ddd.wasm",
)

@Composable
expect fun PlatformDeepSeekAuthDialog(
    onTokenExtracted: (DeepSeekAuthSession) -> Unit,
    onDismiss: () -> Unit,
    initialEmail: String = "",
    initialPassword: String = "",
)
