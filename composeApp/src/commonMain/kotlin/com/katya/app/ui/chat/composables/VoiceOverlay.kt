package com.katya.app.ui.chat.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.katya.app.data.VoiceUiMode
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.prompt_ask_question
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceOverlay(
    isListening: Boolean,
    partialResults: String,
    mode: VoiceUiMode,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // New bottom bar style for voice input
    if (isListening) {
        androidx.compose.animation.AnimatedVisibility(
            visible = isListening,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300),
            ),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300),
            ),
        ) {
            ListeningBar(
                partialResults = partialResults,
                onCancel = onCancel,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ListeningBar(
    partialResults: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAnimation",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 2.dp,
                brush = com.katya.app.ui.gradientBrush,
                shape = RoundedCornerShape(28.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .scale(scale)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Listening",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = if (partialResults.isNotBlank()) partialResults else "Внимаю...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 10,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(32.dp).padding(start = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun VoiceContent(
    partialResults: String,
    onCancel: () -> Unit,
    isFullScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    // Keep old full-screen content for backward compatibility if needed
    // This is now unused but kept to avoid breaking existing references
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Legacy VoiceOverlay content")
    }
}
