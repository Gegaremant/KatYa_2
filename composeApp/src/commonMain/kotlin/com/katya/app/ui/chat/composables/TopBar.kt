package com.katya.app.ui.chat.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.katya.app.ui.chat.ChatActions
import com.katya.app.ui.handCursor
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.chat_history_content_description
import katya.composeapp.generated.resources.ic_add
import katya.composeapp.generated.resources.ic_history
import katya.composeapp.generated.resources.ic_settings
import katya.composeapp.generated.resources.ic_volume_off
import katya.composeapp.generated.resources.ic_volume_up
import katya.composeapp.generated.resources.new_chat_content_description
import katya.composeapp.generated.resources.new_chat_content_description
import katya.composeapp.generated.resources.settings_content_description
import katya.composeapp.generated.resources.toggle_speech_output_content_description
import nl.marc_apps.tts.TextToSpeechInstance
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun TopBar(
    textToSpeech: TextToSpeechInstance? = null,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    actions: ChatActions,
    isChatHistoryEmpty: Boolean,
    hasSavedConversations: Boolean,
    isVlessEnabled: Boolean,
    onNavigateToSettings: () -> Unit,
    onShowHistory: () -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    if (navigationTabBar != null) {
        Box(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp),
        ) {
            Row(modifier = Modifier.align(Alignment.CenterStart)) {
                LeadingButtons(textToSpeech, isSpeechOutputEnabled, isSpeaking, actions, isChatHistoryEmpty, hasSavedConversations, isVlessEnabled, onShowHistory)
            }
            Box(modifier = Modifier.align(Alignment.Center)) {
                navigationTabBar()
            }
            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                if (textToSpeech != null) {
                    SpeechToggleButton(textToSpeech, isSpeechOutputEnabled, isSpeaking, actions)
                }
            }
        }
    } else {
        Row {
            LeadingButtons(textToSpeech, isSpeechOutputEnabled, isSpeaking, actions, isChatHistoryEmpty, hasSavedConversations, isVlessEnabled, onShowHistory)
            Spacer(Modifier.weight(1f))
            if (textToSpeech != null) {
                SpeechToggleButton(textToSpeech, isSpeechOutputEnabled, isSpeaking, actions)
            }
            IconButton(
                modifier = Modifier.handCursor(),
                onClick = onNavigateToSettings,
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_settings),
                    contentDescription = stringResource(Res.string.settings_content_description),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun LeadingButtons(
    textToSpeech: TextToSpeechInstance?,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    actions: ChatActions,
    isChatHistoryEmpty: Boolean,
    hasSavedConversations: Boolean,
    isVlessEnabled: Boolean,
    onShowHistory: () -> Unit,
) {
    if (isVlessEnabled) {
        IconButton(onClick = {}) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Dns,
                contentDescription = "Vless proxy is active",
                tint = androidx.compose.ui.graphics.Color(0xFF4CAF50),
            )
        }
    }

    if (hasSavedConversations) {
        IconButton(
            modifier = Modifier.handCursor(),
            onClick = onShowHistory,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_history),
                contentDescription = stringResource(Res.string.chat_history_content_description),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
    if (!isChatHistoryEmpty) {
        IconButton(
            modifier = Modifier.handCursor(),
            onClick = {
                if (isSpeechOutputEnabled && isSpeaking) {
                    actions.setIsSpeaking(false, "")
                    textToSpeech?.stop()
                }
                actions.startNewChat()
            },
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_add),
                contentDescription = stringResource(Res.string.new_chat_content_description),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun SpeechToggleButton(
    textToSpeech: TextToSpeechInstance,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    actions: ChatActions,
) {
    IconButton(
        modifier = Modifier.handCursor(),
        onClick = {
            if (isSpeechOutputEnabled && isSpeaking) {
                actions.setIsSpeaking(false, "")
                textToSpeech.stop()
            }
            actions.toggleSpeechOutput()
        },
    ) {
        Icon(
            imageVector = if (isSpeechOutputEnabled) {
                vectorResource(Res.drawable.ic_volume_up)
            } else {
                vectorResource(Res.drawable.ic_volume_off)
            },
            contentDescription = stringResource(Res.string.toggle_speech_output_content_description),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}
