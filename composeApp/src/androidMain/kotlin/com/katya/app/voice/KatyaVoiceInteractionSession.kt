package com.katya.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View

class KatyaVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreate() {
        super.onCreate()
    }

    override fun onHandleAssist(
        data: Bundle?,
        structure: android.app.assist.AssistStructure?,
        content: android.app.assist.AssistContent?
    ) {
        super.onHandleAssist(data, structure, content)
        // Here we can read the screen content and context
        
        // Start the main app activity for voice input
        val intent = Intent(context, Class.forName("com.katya.app.MainActivity")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("voice_interaction", true)
        }
        startAssistantActivity(intent)
    }

    override fun onCreateContentView(): View? {
        // Return null to not show a custom view here, but rather start our MainActivity
        return super.onCreateContentView()
    }
}
