package com.katya.app.ui.chat.composables

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.katya.app.Platform
import com.katya.app.currentPlatform
import com.katya.app.data.ServiceEntry
import com.katya.app.data.imageExtensions
import com.katya.app.skills.SkillManifest
import com.katya.app.stt.createSttController
import com.katya.app.tools.AudioPermissionController
import com.katya.app.tools.SetupAudioPermissionHandler
import com.katya.app.ui.gradientBrush
import com.katya.app.ui.handCursor
import com.katya.app.ui.outlineTextFieldColors
import io.github.vinceglb.filekit.PlatformFile
import com.katya.app.data.KatyaFile
import com.katya.app.data.PlatformKatyaFile
import com.katya.app.data.BytesKatyaFile
import com.katya.app.tools.rememberImageCaptureLauncher
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.name
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.ic_attach
import katya.composeapp.generated.resources.ic_file
import katya.composeapp.generated.resources.ic_image
import katya.composeapp.generated.resources.ic_stop
import katya.composeapp.generated.resources.ic_up
import katya.composeapp.generated.resources.prompt_ask_question
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import com.katya.app.data.AppSettings
import com.katya.app.data.VoiceUiMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionInput(
    files: ImmutableList<KatyaFile>,
    addFile: (KatyaFile) -> Unit,
    removeFile: (KatyaFile) -> Unit,
    ask: (String) -> Unit,
    supportedFileExtensions: ImmutableList<String>,
    textState: TextFieldValue,
    onTextStateChange: (TextFieldValue) -> Unit,
    isLoading: Boolean = false,
    cancel: () -> Unit = {},
    availableServices: ImmutableList<ServiceEntry> = persistentListOf(),
    selectedService: ServiceEntry? = availableServices.firstOrNull(),
    onSelectService: (String) -> Unit = {},
    sttController: com.katya.app.stt.SttController? = null,
    audioPermissionController: com.katya.app.tools.AudioPermissionController? = null,
    wakeWordTriggerCount: Int = 0,
    installedSkills: ImmutableList<SkillManifest> = persistentListOf(),
    modifier: Modifier = Modifier,
) {
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val sttController = sttController ?: remember { if (isPreview) null else createSttController() }
    val audioPermissionController: AudioPermissionController? = audioPermissionController ?: if (isPreview) null else koinInject()
    val isListening by (sttController?.isListening ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsStateWithLifecycle()
    val partialResults by (sttController?.partialResults ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsStateWithLifecycle()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    if (audioPermissionController != null) {
        SetupAudioPermissionHandler(audioPermissionController)
    }

    LaunchedEffect(partialResults) {
        if (isListening && partialResults.isNotBlank()) {
            onTextStateChange(TextFieldValue(partialResults, TextRange(partialResults.length)))
        }
    }

    val appSettings: AppSettings? = if (isPreview) null else koinInject()
    val voiceUiMode by (appSettings?.voiceUiModeFlow ?: kotlinx.coroutines.flow.MutableStateFlow(VoiceUiMode.FULL_SCREEN)).collectAsStateWithLifecycle()

    if (isListening) {
        VoiceOverlay(
            isListening = isListening,
            partialResults = partialResults,
            mode = voiceUiMode,
            onCancel = {
                sttController?.stopListening()
                onTextStateChange(TextFieldValue(""))
            }
        )
    }

    Column(modifier = modifier) {
        // Slash autocomplete: shown when the user is typing the first token and it starts
        // with `/`. Selecting an entry rewrites the first token to the canonical skill id
        // so the ViewModel can match it at send time.
        if (installedSkills.isNotEmpty()) {
            val slashQuery = remember(textState.text, textState.selection) {
                detectSlashQuery(textState.text, textState.selection.start)
            }
            if (slashQuery != null) {
                SkillAutocomplete(
                    skills = installedSkills,
                    query = slashQuery,
                    onSelect = { skill ->
                        val text = textState.text
                        val firstSpace = text.indexOfFirst { it.isWhitespace() }
                        val rest = if (firstSpace < 0) "" else text.substring(firstSpace)
                        val newText = "/${skill.id}$rest"
                        val cursor = ("/" + skill.id + " ").length
                        onTextStateChange(
                            TextFieldValue(
                                text = if (rest.isEmpty()) "/${skill.id} " else newText,
                                selection = TextRange(cursor.coerceAtMost(if (rest.isEmpty()) cursor else newText.length)),
                            ),
                        )
                    },
                )
                Spacer(Modifier.padding(top = 4.dp))
            }
        }

        if (files.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (file in files) {
                    val icon = if (file.extension.lowercase() in imageExtensions) {
                        Res.drawable.ic_image
                    } else {
                        Res.drawable.ic_file
                    }
                    SuggestionChip(
                        modifier = Modifier.handCursor(),
                        onClick = { removeFile(file) },
                        icon = {
                            Icon(
                                modifier = Modifier.size(16.dp),
                                painter = painterResource(icon),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        },
                        label = {
                            DisableSelection {
                                Text(
                                    modifier = Modifier.handCursor(),
                                    text = truncateFileName(file.name),
                                )
                            }
                        },
                    )
                }
            }
        }

        fun submitQuestion() {
            val text = textState.text
            if (text.isNotBlank()) {
                ask(text.trim())
                onTextStateChange(TextFieldValue(""))
            }
        }

        val allowFileAttachment = supportedFileExtensions.isNotEmpty()
        val filePickerLauncher = if (allowFileAttachment) {
            rememberFilePickerLauncher(
                type = FileKitType.File(extensions = supportedFileExtensions),
                mode = io.github.vinceglb.filekit.dialogs.FileKitMode.Multiple(),
            ) { files ->
                files?.forEach { file ->
                    addFile(PlatformKatyaFile(file))
                }
            }
        } else {
            null
        }

        val cameraLauncher = rememberImageCaptureLauncher(onResult = { bytes ->
            if (bytes != null) {
                addFile(BytesKatyaFile(bytes, "camera_capture.jpg", "jpg", "image/jpeg"))
            }
        })

        LaunchedEffect(wakeWordTriggerCount) {
            if (wakeWordTriggerCount > 0) {
                if (audioPermissionController?.requestPermission() == true) {
                    sttController?.startListening { result ->
                        onTextStateChange(TextFieldValue(""))
                        ask(result.trim())
                    }
                }
            }
        }

        val focusRequester = remember { FocusRequester() }
        TextField(
            value = textState,
            onValueChange = onTextStateChange,
            modifier = Modifier
                .focusRequester(focusRequester)
                .padding(16.dp)
                .heightIn(max = 120.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(
                    BorderStroke(width = 2.dp, brush = gradientBrush),
                    shape = RoundedCornerShape(28.dp),
                )
                .onPreviewKeyEvent { event ->
                    // Only handle hardware keyboard on desktop/web platforms
                    if (currentPlatform !is Platform.Mobile && event.key.keyCode == Key.Enter.keyCode && event.type == KeyEventType.KeyDown) {
                        if (event.isShiftPressed) {
                            // Shift+Enter -> manually insert newline
                            val currentText = textState.text
                            val selection = textState.selection
                            val start = minOf(selection.start, selection.end).coerceIn(0, currentText.length)
                            val end = maxOf(selection.start, selection.end).coerceIn(0, currentText.length)

                            val newText = currentText.replaceRange(start, end, "\n")
                            onTextStateChange(
                                TextFieldValue(
                                    text = newText,
                                    selection = TextRange(start + 1),
                                ),
                            )
                            return@onPreviewKeyEvent true
                        } else {
                            // Enter without Shift -> send message and consume event
                            submitQuestion()
                            return@onPreviewKeyEvent true
                        }
                    }
                    return@onPreviewKeyEvent false
                },
            colors = outlineTextFieldColors(),
            placeholder = {
                Text(
                    stringResource(Res.string.prompt_ask_question),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (availableServices.size > 1) {
                        ServiceSelector(
                            services = availableServices,
                            onSelectService = onSelectService,
                        )
                    }
                    if (isLoading) {
                        TrailingIcon(icon = Res.drawable.ic_stop, onClick = cancel, isPulsing = true)
                    } else if (textState.text.isNotBlank() && !isListening) {
                        TrailingIcon(icon = Res.drawable.ic_up, onClick = { submitQuestion() })
                    } else if (isListening) {
                        TrailingIcon(icon = Res.drawable.ic_stop, onClick = { sttController?.stopListening() }, isPulsing = true)
                    } else {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                if (audioPermissionController?.requestPermission() == true) {
                                    sttController?.startListening { result ->
                                        onTextStateChange(TextFieldValue(""))
                                        ask(result.trim())
                                    }
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Speak",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            },
            keyboardActions = if (currentPlatform !is Platform.Mobile) {
                KeyboardActions(onSend = { submitQuestion() })
            } else {
                KeyboardActions() // No keyboard send action on mobile
            },
            leadingIcon = if (filePickerLauncher != null) {
                {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircleIconButton(
                            icon = vectorResource(Res.drawable.ic_attach),
                            onClick = { filePickerLauncher.launch() },
                            modifier = Modifier.padding(start = 7.dp),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                        CircleIconButton(
                            icon = androidx.compose.material.icons.Icons.Default.CameraAlt,
                            onClick = { cameraLauncher.launch() },
                            modifier = Modifier.padding(start = 2.dp),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                imeAction = if (currentPlatform is Platform.Mobile) ImeAction.Default else ImeAction.Send,
            ),
        )
        val inInspection = LocalInspectionMode.current
        LaunchedEffect(Unit) {
            if (!inInspection) focusRequester.requestFocus()
        }
    }
}

/**
 * Returns the slash-command query string the user is currently typing, or null if
 * the cursor isn't inside a leading `/<token>`. Examples:
 *  - `"/su"` cursor at 3 → `"su"`
 *  - `"/summarize https://…"` cursor at 4 → `"sum"`
 *  - `"hello /foo"` (slash not at start) → `null`
 *  - `"/foo bar"` cursor at 6 → `null` (cursor past first space)
 */
internal fun detectSlashQuery(text: String, cursor: Int): String? {
    if (!text.startsWith('/')) return null
    val firstSpace = text.indexOfFirst { it.isWhitespace() }
    val tokenEnd = if (firstSpace < 0) text.length else firstSpace
    if (cursor > tokenEnd) return null
    return text.substring(1, tokenEnd).lowercase()
}

/**
 * Shortens a filename that is too long to display in a chip. Returns the first [maxChars]
 * characters of the base name followed by `…` and the original extension, so the user still
 * recognizes the file type. Short names are returned unchanged.
 */
internal fun truncateFileName(name: String, maxChars: Int = 16): String {
    if (name.length <= maxChars) return name
    val dotIndex = name.lastIndexOf('.')
    return if (dotIndex > 0 && dotIndex < name.length - 1) {
        val base = name.substring(0, dotIndex)
        val ext = name.substring(dotIndex) // includes the dot
        val keep = (maxChars - ext.length - 1).coerceAtLeast(1)
        "${base.take(keep)}…$ext"
    } else {
        "${name.take(maxChars - 1)}…"
    }
}

@Composable
internal fun TrailingIcon(
    icon: org.jetbrains.compose.resources.DrawableResource = Res.drawable.ic_up,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPulsing: Boolean = false,
) {
    val pulseModifier = if (isPulsing) {
        val infiniteTransition = rememberInfiniteTransition()
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
        Modifier.graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
            alpha = pulseAlpha
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(brush = gradientBrush, CircleShape)
            .handCursor()
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            vectorResource(icon),
            modifier = Modifier.size(32.dp).then(pulseModifier),
            contentDescription = null,
            tint = Color.White,
        )
    }
}

@Composable
internal fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .handCursor(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            modifier = Modifier.size(24.dp),
            contentDescription = null,
            tint = tint,
        )
    }
}
