package com.katya.app.tools

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema

object MediaControllerTool : Tool {
    override val schema = ToolSchema(
        name = "media_controller",
        description = "Управляет воспроизведением фоновой музыки (Яндекс Музыка, Spotify, VK Музыка и т.д.) через медиа-кнопки.",
        parameters = mapOf(
            "action" to ParameterSchema(
                type = "string",
                description = "Действие: 'play', 'pause', 'next', 'previous'",
                required = true
            )
        )
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val action = args["action"] as? String ?: return "Error: Action is required"
        
        val keyCode = when (action.lowercase()) {
            "play", "pause", "play_pause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return "Error: Unsupported action '$action'"
        }

        try {
            val context = org.koin.java.KoinJavaComponent.getKoin().get<Context>()
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            audioManager.dispatchMediaKeyEvent(downEvent)
            
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(upEvent)
            
            return "Successfully dispatched media key action: $action"
        } catch (e: Exception) {
            return "Failed to control media: ${e.message}"
        }
    }
}
