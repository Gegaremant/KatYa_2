package com.katya.app.ui.chat.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.katya.app.tts.SpeechEngine
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
import katya.composeapp.generated.resources.settings_content_description
import katya.composeapp.generated.resources.toggle_speech_output_content_description
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun TopBar(
    textToSpeech: SpeechEngine? = null,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    actions: ChatActions,
    isChatHistoryEmpty: Boolean,
    hasSavedConversations: Boolean,
    isVlessEnabled: Boolean,
    deviceStatus: String? = null,
    connectionStatus: String? = null,
    showDeviceStatus: Boolean = false,
    showConnectionStatus: Boolean = false,
    isNetworkConnected: Boolean = false,
    isBatteryCharging: Boolean = false,
    isThinking: Boolean = false,
    onNavigateToSettings: () -> Unit,
    onShowHistory: () -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val waitSeconds = rememberWaitSeconds(isThinking)
        if (navigationTabBar != null) {
            Box(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp),
            ) {
                Row(modifier = Modifier.align(Alignment.CenterStart)) {
                    LeadingButtons(
                        textToSpeech = textToSpeech,
                        isSpeechOutputEnabled = isSpeechOutputEnabled,
                        isSpeaking = isSpeaking,
                        actions = actions,
                        isChatHistoryEmpty = isChatHistoryEmpty,
                        hasSavedConversations = hasSavedConversations,
                        isVlessEnabled = isVlessEnabled,
                        isThinking = isThinking,
                        onShowHistory = onShowHistory,
                    )
                }
                Box(modifier = Modifier.align(Alignment.Center)) {
                    navigationTabBar()
                }
                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
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
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LeadingButtons(
                    textToSpeech = textToSpeech,
                    isSpeechOutputEnabled = isSpeechOutputEnabled,
                    isSpeaking = isSpeaking,
                    actions = actions,
                    isChatHistoryEmpty = isChatHistoryEmpty,
                    hasSavedConversations = hasSavedConversations,
                    isVlessEnabled = isVlessEnabled,
                    isThinking = isThinking,
                    onShowHistory = onShowHistory,
                )
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

        if (showDeviceStatus && deviceStatus != null) {
            StatusRow(text = deviceStatus)
        }
        if (showConnectionStatus && connectionStatus != null) {
            // Feedback 05.10 #6: waiting is part of the same line, not a second one —
            // "Подключено к <модель> · ожидание ответа 12 с". A separate row read as two
            // unrelated facts, and the timer makes it visible that the process is really
            // running instead of hanging.
            StatusRow(
                text = if (isThinking) {
                    val wait = if (isNetworkConnected) {
                        "ожидание ответа $waitSeconds с"
                    } else {
                        "модель не отвечает — запрос завис $waitSeconds с"
                    }
                    "$connectionStatus · $wait"
                } else {
                    connectionStatus
                },
                isOnline = isNetworkConnected,
            )
        }
    }
}

/** Seconds spent waiting for the current reply, counting up from zero. */
@Composable
private fun rememberWaitSeconds(isThinking: Boolean): State<Int> {
    val seconds = remember { mutableIntStateOf(0) }
    LaunchedEffect(isThinking) {
        if (!isThinking) {
            seconds.intValue = 0
            return@LaunchedEffect
        }
        while (isThinking) {
            delay(1000)
            seconds.intValue += 1
        }
    }
    return seconds
}

@Composable
private fun LeadingButtons(
    textToSpeech: SpeechEngine?,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    actions: ChatActions,
    isChatHistoryEmpty: Boolean,
    hasSavedConversations: Boolean,
    isVlessEnabled: Boolean,
    isThinking: Boolean,
    onShowHistory: () -> Unit,
) {
    // VLESS proxy indicator
    if (isVlessEnabled) {
        Box(
            modifier = Modifier.padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Dns,
                contentDescription = "Vless proxy is active",
                tint = Color(0xFF4CAF50),
            )
        }
    }

    // Working/reasoning indicator. The old three-pulsing-dots row was removed: it read as
    // a connection/availability status and never conveyed what the model was actually doing.
    // Honest model status now lives in the status banner tied to fallbackStatus instead.

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
private fun StatusRow(text: String, isOnline: Boolean? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Feedback 07.10 #5: here were two stacked `padding` modifiers — 16/2 плюс
            // 12/6. В итоге отступ по бокам становился 28dp вместо 16, а по вертикали
            // каждая строка съедала 8dp вместо 2: строки состояния разъезжались и
            // «съедали» место, ради которого их и поднимали выше. Один отступ вместо
            // двух — и строка снова компактная.
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isOnline != null) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(8.dp)
                    .background(
                        color = if (isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        shape = CircleShape,
                    ),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpeechToggleButton(
    textToSpeech: SpeechEngine,
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
