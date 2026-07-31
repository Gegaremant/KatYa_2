package com.katya.app.tools

import android.content.Intent
import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema
import com.katya.app.voice.KatyaAccessibilityService
import kotlinx.coroutines.delay

object KeepAutomationTool : Tool {
    override val schema = ToolSchema(
        name = "create_google_keep_note_automation",
        description = "Создает заметку в Google Keep с помощью UI-автоматизации (AccessibilityService), нажимая кнопки и вводя текст от имени пользователя.",
        parameters = mapOf(
            "title" to ParameterSchema(
                type = "string",
                description = "Заголовок заметки",
                required = true
            ),
            "content" to ParameterSchema(
                type = "string",
                description = "Содержимое заметки",
                required = true
            )
        )
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val title = args["title"] as? String ?: return "Error: No title provided"
        val content = args["content"] as? String ?: return "Error: No content provided"
        
        val service = KatyaAccessibilityService.instance 
            ?: return "Error: Accessibility Service is not running or not granted permissions."

        try {
            val context = org.koin.java.KoinJavaComponent.getKoin().get<android.content.Context>()
            
            // 1. Launch Google Keep
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.keep")
            if (intent == null) {
                return "Error: Google Keep is not installed."
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            
            // Wait for app to open and load
            delay(3000)

            // 2. Click "New text note" button
            // The floating action button id is usually com.google.android.keep:id/new_note_button
            var clicked = service.clickNodeById("com.google.android.keep:id/new_note_button")
            if (!clicked) {
                // fallback to content description (might vary by locale, typically "New text note")
                clicked = service.clickNodeByContentDescription("New text note") || service.clickNodeByContentDescription("Новая текстовая заметка")
            }
            
            if (!clicked) {
                return "Error: Could not find or click the 'New Note' button."
            }

            // Wait for note editor to open
            delay(1500)

            // 3. Set Title
            val titleSet = service.setTextNodeById("com.google.android.keep:id/editable_title", title)
            
            // 4. Set Content
            val contentSet = service.setTextNodeById("com.google.android.keep:id/edit_note_text", content)

            if (!titleSet && !contentSet) {
                return "Error: Could not find title or content fields to set text."
            }

            // Wait a moment for text to register
            delay(500)

            // 5. Press global BACK to save the note and exit the editor
            service.performGlobalBack()
            delay(500)
            
            // Press BACK again to exit app or return to home
            service.performGlobalBack()

            return "Successfully created note in Google Keep via UI Automation."
        } catch (e: Exception) {
            return "Automation Error: ${e.message}"
        }
    }
}
