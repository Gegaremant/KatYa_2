package com.katya.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.katya.app.Platform
import com.katya.app.currentPlatform
import com.katya.app.data.AppSettings
import com.katya.app.tools.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import nl.marc_apps.tts.TextToSpeechInstance

@Composable
fun StartupPermissionFlow(
    textToSpeech: TextToSpeechInstance? = null,
    onComplete: () -> Unit
) {
    val appSettings = koinInject<AppSettings>()

    // Skip onboarding if not on Android or already completed
    if (appSettings.isOnboardingCompleted() || currentPlatform !is Platform.Mobile.Android) {
        LaunchedEffect(Unit) { onComplete() }
        return
    }

    val notificationController = koinInject<NotificationPermissionController>()
    val exactAlarmController = koinInject<ExactAlarmPermissionController>()
    val batteryController = koinInject<BatteryOptimizationPermissionController>()
    val accessibilityController = koinInject<AccessibilityPermissionController>()
    val audioController = koinInject<AudioPermissionController>()
    val smsController = koinInject<SmsPermissionController>()
    val smsSendController = koinInject<SmsSendPermissionController>()
    val calendarController = koinInject<CalendarPermissionController>()
    val notificationListenerController = koinInject<NotificationListenerController>()
    val systemRoleController = remember { SystemRoleController() }
    val commandExecutor = remember { CommandExecutor() }

    // Setup permission handlers in compose scope
    SetupAudioPermissionHandler(audioController)
    SetupAccessibilityPermissionHandler(accessibilityController)

    val coroutineScope = rememberCoroutineScope()

    // Mode Selection States
    var isSandbox by remember { mutableStateOf(appSettings.isSandboxEnabled()) }
    var isGodMode by remember { mutableStateOf(appSettings.isGodModeEnabled()) }

    // Synchronize initial selection to settings if not already set
    LaunchedEffect(isSandbox, isGodMode) {
        appSettings.setSandboxEnabled(isSandbox)
        appSettings.setGodModeEnabled(isGodMode)
    }

    // Permission States
    var hasRoot by remember { mutableStateOf(false) }
    var isCheckingRoot by remember { mutableStateOf(false) }

    var hasMicrophone by remember { mutableStateOf(audioController.hasPermission()) }
    var hasNotifications by remember { mutableStateOf(notificationController.hasPermission()) }
    var hasBatteryIgnore by remember { mutableStateOf(batteryController.hasPermission()) }
    var hasExactAlarms by remember { mutableStateOf(exactAlarmController.hasPermission()) }
    var hasAccessibility by remember { mutableStateOf(accessibilityController.hasPermission()) }
    var hasNotificationListener by remember { mutableStateOf(notificationListenerController.isAccessGranted()) }
    var hasGodModePack by remember {
        mutableStateOf(
            smsController.hasPermission() &&
            smsSendController.hasPermission() &&
            calendarController.hasPermission()
        )
    }

    // Perform initial checks
    LaunchedEffect(Unit) {
        hasRoot = commandExecutor.isRootAvailable()
        hasMicrophone = audioController.hasPermission()
        hasNotifications = notificationController.hasPermission()
        hasBatteryIgnore = batteryController.hasPermission()
        hasExactAlarms = exactAlarmController.hasPermission()
        hasAccessibility = accessibilityController.hasPermission()
        hasNotificationListener = notificationListenerController.isAccessGranted()
        hasGodModePack = smsController.hasPermission() &&
                smsSendController.hasPermission() &&
                calendarController.hasPermission()
    }

    // Voice Greeting
    LaunchedEffect(textToSpeech) {
        val tts = textToSpeech ?: return@LaunchedEffect
        try {
            tts.say("Привет, я цифровой помощник Катя. Чтобы мне быть максимально полезной тебе, мне нужны следующие доступы")
        } catch (_: Exception) {
            // Ignore speech synthesis failures
        }
    }

    

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(36.dp))

                // Katya Hologram Avatar Mockup
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(45.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(45.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Цифровой помощник Катя",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Первоначальная настройка и выбор режима",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // Mode Selection Buttons
                Text(
                    text = "Выберите режим работы:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sandbox Mode Button
                    ModeSelectorItem(
                        title = "Песочница",
                        description = "Изолированное окружение Termux. Безопасно, без доступа к системе.",
                        selected = isSandbox && !isGodMode,
                        onClick = {
                            isSandbox = true
                            isGodMode = false
                        }
                    )

                    // Bare Android Mode Button
                    ModeSelectorItem(
                        title = "Голый Android",
                        description = "Работа напрямую на устройстве с системными правами пользователя (без Root).",
                        selected = !isSandbox && !isGodMode,
                        onClick = {
                            isSandbox = false
                            isGodMode = false
                        }
                    )

                    // GOD_MODE Button
                    ModeSelectorItem(
                        title = "GOD_MODE (Режиссерская версия)",
                        description = "Полная власть над устройством. Требуются Root-права (Magisk) и все доступы.",
                        selected = isSandbox && isGodMode,
                        accent = true,
                        onClick = {
                            isSandbox = true
                            isGodMode = true
                        }
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Необходимые разрешения:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(Modifier.height(8.dp))

                // Permissions List
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // GOD_MODE Specific: Root Rights
                    if (isGodMode) {
                        PermissionItem(
                            title = "Root-права (su)",
                            description = "Позволяет выполнять системные shell-команды напрямую.",
                            isGranted = hasRoot,
                            isLoading = isCheckingRoot,
                            onRequest = {
                                coroutineScope.launch {
                                    isCheckingRoot = true
                                    hasRoot = commandExecutor.isRootAvailable()
                                    if (hasRoot) {
                                        RootHelper.grantAllPermissions()
                                        // Update state immediately to show as granted
                                        hasMicrophone = true
                                        hasGodModePack = true
                                        hasNotifications = true
                                    }
                                    isCheckingRoot = false
                                }
                            }
                        )
                    }

                    // Accessibility (Common)
                    PermissionItem(
                        title = "Специальные возможности",
                        description = "Используется для управления интерфейсом, автоматизации и работы с выключенным экраном.",
                        isGranted = hasAccessibility,
                        onRequest = {
                            coroutineScope.launch {
                                hasAccessibility = accessibilityController.requestPermission()
                            }
                        }
                    )

                    // Microphone (Common)
                    PermissionItem(
                        title = "Доступ к микрофону",
                        description = "Необходим для распознавания голоса и общения.",
                        isGranted = hasMicrophone,
                        onRequest = {
                            coroutineScope.launch {
                                hasMicrophone = audioController.requestPermission()
                            }
                        }
                    )

                    // Notifications (Common)
                    PermissionItem(
                        title = "Уведомления",
                        description = "Для отчетов о фоновой работе и отправки напоминаний.",
                        isGranted = hasNotifications,
                        onRequest = {
                            coroutineScope.launch {
                                hasNotifications = notificationController.requestPermission()
                            }
                        }
                    )

                    // Notification Listener (Common)
                    if (notificationListenerController.isSupported()) {
                        PermissionItem(
                            title = "Чтение уведомлений",
                            description = "Позволяет Кате реагировать на входящие сообщения и системные уведомления.",
                            isGranted = hasNotificationListener,
                            onRequest = {
                                notificationListenerController.openAccessSettings()
                                // The user has to return to the app, so we can't reliably auto-update here
                                // without a lifecycle observer, but they can click again if needed.
                            }
                        )
                    }

                    // Background battery optimization (Common)
                    PermissionItem(
                        title = "Работа в фоновом режиме",
                        description = "Отключение оптимизации батареи, чтобы Катя не засыпала.",
                        isGranted = hasBatteryIgnore,
                        onRequest = {
                            coroutineScope.launch {
                                hasBatteryIgnore = batteryController.requestPermission()
                            }
                        }
                    )

                    // Exact alarms (Common)
                    PermissionItem(
                        title = "Точные будильники",
                        description = "Для запуска планировщика задач точно в срок.",
                        isGranted = hasExactAlarms,
                        onRequest = {
                            coroutineScope.launch {
                                hasExactAlarms = exactAlarmController.requestPermission()
                            }
                        }
                    )

                    // GOD_MODE Pack (SMS, Calendar, Storage)
                    if (isGodMode) {
                        PermissionItem(
                            title = "GOD_MODE Пакет доступов",
                            description = "Доступ к SMS, Календарю, Памяти и Контактам.",
                            isGranted = hasGodModePack,
                            onRequest = {
                                coroutineScope.launch {
                                    smsController.requestPermission()
                                    smsSendController.requestPermission()
                                    calendarController.requestPermission()
                                    hasGodModePack = smsController.hasPermission() &&
                                            smsSendController.hasPermission() &&
                                            calendarController.hasPermission()
                                }
                            }
                        )

                        PermissionItem(
                            title = "Помощник по умолчанию",
                            description = "Назначить Катю системным цифровым помощником.",
                            isGranted = false, // Cannot easily check synchronously without context
                            onRequest = {
                                systemRoleController.openDefaultAssistantSettings()
                            }
                        )

                        PermissionItem(
                            title = "Администратор устройства",
                            description = "Расширенные права управления устройством.",
                            isGranted = false,
                            onRequest = {
                                systemRoleController.openDeviceAdminSettings()
                            }
                        )

                        PermissionItem(
                            title = "Агент доверия",
                            description = "Глубокая системная интеграция и обход блокировок.",
                            isGranted = false,
                            onRequest = {
                                systemRoleController.openTrustAgentSettings()
                            }
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))

                // Continue Button
                Button(
                    onClick = {
                        appSettings.setOnboardingCompleted(true)
                        onComplete()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGodMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Продолжить работу",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ModeSelectorItem(
    title: String,
    description: String,
    selected: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = when {
        selected && accent -> MaterialTheme.colorScheme.secondary
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }

    val backgroundColor = when {
        selected && accent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = if (accent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 40.dp)
            )
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    isLoading: Boolean = false,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Предоставлено",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Button(
                        onClick = onRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Разрешить",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
