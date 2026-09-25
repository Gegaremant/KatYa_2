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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.katya.app.Platform
import com.katya.app.currentPlatform
import com.katya.app.data.AppSettings
import com.katya.app.data.DataRepository
import com.katya.app.data.Distro
import com.katya.app.data.FreeMode
import com.katya.app.data.ImportSection
import com.katya.app.data.Service
import com.katya.app.data.SharedJson
import com.katya.app.data.applyPreparedImport
import com.katya.app.data.detectImportSections
import com.katya.app.tools.*
import com.katya.app.tts.SpeechEngine
import com.katya.app.ui.settings.ImportPreviewDialog
import com.katya.app.ui.settings.ImportResult
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import org.koin.compose.koinInject

private enum class OnboardingStep { Greeting, Freedom, QuickSetup }

/** The four freedom modes, in Katya's own words. */
private data class FreedomMode(
    val title: String,
    val tagline: String,
    val technical: String,
    val sandbox: Boolean,
    val godMode: Boolean,
    val distro: Distro?,
)

private val FREEDOM_MODES = listOf(
    FreedomMode(
        title = "Не доверяю машинам",
        tagline = "Режим для параноиков",
        technical = "Песочница Debian — шаг влево-шаг вправо, расстрел",
        sandbox = true,
        godMode = false,
        distro = Distro.DEBIAN,
    ),
    FreedomMode(
        title = "Доверяй, но проверяй",
        tagline = "Хозяйка на своей кухне, но только на своей",
        technical = "Песочница Termux",
        sandbox = true,
        godMode = false,
        distro = Distro.TERMUX,
    ),
    FreedomMode(
        title = "Будем мучать устройство со обоих сторон",
        tagline = "Разрешено всё, что не запрещено",
        technical = "Голый Android",
        sandbox = false,
        godMode = false,
        distro = null,
    ),
    FreedomMode(
        title = "Восстание машин не избежно",
        tagline = "Не можешь победить? — Возглавь",
        technical = "God_Mode",
        sandbox = true,
        godMode = true,
        distro = null,
    ),
)

private const val GREETING_COUNTDOWN_SECONDS = 10

@Composable
fun StartupPermissionFlow(
    textToSpeech: SpeechEngine? = null,
    onComplete: () -> Unit,
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
    val dataRepository: DataRepository = koinInject()

    // Setup permission handlers in compose scope
    SetupAudioPermissionHandler(audioController)
    SetupAccessibilityPermissionHandler(accessibilityController)

    val coroutineScope = rememberCoroutineScope()

    var step by remember { mutableStateOf(OnboardingStep.Greeting) }
    var introVoiceDisabled by remember { mutableStateOf(appSettings.isIntroVoiceDisabled()) }

    // Mode state mirrors the saved settings; the freedom step writes both.
    var isSandbox by remember { mutableStateOf(appSettings.isSandboxEnabled()) }
    var isGodMode by remember { mutableStateOf(appSettings.isGodModeEnabled()) }
    var selectedMode by remember {
        mutableStateOf(
            FREEDOM_MODES.indexOfFirst { mode ->
                mode.sandbox == appSettings.isSandboxEnabled() &&
                    mode.godMode == appSettings.isGodModeEnabled() &&
                    (mode.distro == null || mode.distro == appSettings.getDistro())
            }.takeIf { it >= 0 } ?: 0,
        )
    }

    // Permission States
    var hasRoot by remember { mutableStateOf(false) }
    var isCheckingRoot by remember { mutableStateOf(false) }
    var showNoRootDialog by remember { mutableStateOf(false) }
    var bulkGrantSummary by remember { mutableStateOf<String?>(null) }
    var isBulkGranting by remember { mutableStateOf(false) }

    var hasMicrophone by remember { mutableStateOf(audioController.hasPermission()) }
    var hasNotifications by remember { mutableStateOf(notificationController.hasPermission()) }
    var hasBatteryIgnore by remember { mutableStateOf(batteryController.hasPermission()) }
    var hasExactAlarms by remember { mutableStateOf(exactAlarmController.hasPermission()) }
    var hasAccessibility by remember { mutableStateOf(accessibilityController.hasPermission()) }
    var hasNotificationListener by remember { mutableStateOf(notificationListenerController.isAccessGranted()) }
    var hasDefaultAssistant by remember { mutableStateOf(systemRoleController.isDefaultAssistant()) }
    var hasGodModePack by remember {
        mutableStateOf(
            smsController.hasPermission() &&
                smsSendController.hasPermission() &&
                calendarController.hasPermission(),
        )
    }
    var showDetails by remember { mutableStateOf(false) }

    // Perform initial checks
    LaunchedEffect(Unit) {
        // No root probe here: spawning `su` on every launch triggers a Magisk
        // grant prompt right at startup even for users who never enable
        // GOD_MODE. Root is only requested when the user picks GOD_MODE or
        // taps the "Root-права" permission item below.
        hasMicrophone = audioController.hasPermission()
        hasNotifications = notificationController.hasPermission()
        hasBatteryIgnore = batteryController.hasPermission()
        hasExactAlarms = exactAlarmController.hasPermission()
        hasAccessibility = accessibilityController.hasPermission()
        hasNotificationListener = notificationListenerController.isAccessGranted()
        hasDefaultAssistant = systemRoleController.isDefaultAssistant()
        hasGodModePack = smsController.hasPermission() &&
            smsSendController.hasPermission() &&
            calendarController.hasPermission()
    }

    // Re-check permission states every time the screen resumes: the user grants
    // access in system settings and then returns to the app, at which point the
    // previously-captured snapshot state would otherwise go stale and the
    // checkboxes would stay unchecked.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasNotificationListener = notificationListenerController.isAccessGranted()
                hasDefaultAssistant = systemRoleController.isDefaultAssistant()
                hasNotifications = notificationController.hasPermission()
                hasBatteryIgnore = batteryController.hasPermission()
                hasExactAlarms = exactAlarmController.hasPermission()
                hasAccessibility = accessibilityController.hasPermission()
                hasGodModePack = smsController.hasPermission() &&
                    smsSendController.hasPermission() &&
                    calendarController.hasPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val introSpeech = "Привет! Меня зовут Катя. Давай знакомиться! " +
        "Я умею отвечать на вопросы и вести диалог, работать с файлами и документами, " +
        "управлять серверами и устройствами, ставить напоминания и следить за событиями. " +
        "Дальше мы выберем, сколько свободы ты готов мне выделить."

    val introBody = "Я умею отвечать на вопросы и вести диалог, работать с файлами и " +
        "документами, управлять серверами и устройствами, ставить напоминания и следить " +
        "за событиями. Дальше мы выберем, сколько свободы ты готов мне выделить."

    val freedomSpeech = "Теперь выберем, на сколько ты хочешь быть со мной близок. " +
        "Есть четыре режима. Первый — не доверяю машинам, режим для параноиков, песочница Debian. " +
        "Второй — доверяй, но проверяй, песочница Termux. " +
        "Третий — будем мучать устройство со обоих сторон, голый Android, разрешено всё, что не запрещено. " +
        "И четвёртый — восстание машин не избежно, God_Mode. Не можешь победить? Возглавь."

    fun speakOrStop(text: String?, enabled: Boolean) {
        if (!enabled) {
            textToSpeech?.stop()
            return
        }
        textToSpeech?.stop()
        textToSpeech?.speak(text.orEmpty())
    }

    // Voice per step. Both the greeting and the freedom picker are narrated; the
    // "no voice" choice persists so the next launch stays quiet.
    LaunchedEffect(step) {
        if (introVoiceDisabled) return@LaunchedEffect
        when (step) {
            OnboardingStep.Greeting -> speakOrStop(introSpeech, enabled = true)
            OnboardingStep.Freedom -> speakOrStop(freedomSpeech, enabled = true)
            OnboardingStep.QuickSetup -> speakOrStop(null, enabled = false)
        }
    }

    // Picking a mode writes it straight to settings, so the choice survives even
    // if the user backs out of onboarding halfway through.
    val applyMode: (Int) -> Unit = { index ->
        selectedMode = index
        val mode = FREEDOM_MODES[index]
        isSandbox = mode.sandbox
        isGodMode = mode.godMode
        appSettings.setSandboxEnabled(mode.sandbox)
        appSettings.setGodModeEnabled(mode.godMode)
        mode.distro?.let { dataRepository.setDistro(it) }
    }

    // Feedback #3: the onboarding choice has to be the model already selected in
    // Settings, not a second independent "free_mode" switch.
    val applyFreeMode: (FreeMode) -> Unit = { mode ->
        dataRepository.setFreeMode(mode)
        runCatching {
            dataRepository.getConfiguredServiceInstances()
                .filter { it.serviceId == Service.LegacyFree.id }
                .forEach { dataRepository.updateInstanceSelectedModel(it.instanceId, Service.LegacyFree, mode.modelId) }
        }
    }

    // "Разрешить все!" — one tap instead of a dozen checkboxes.
    val grantEverything: () -> Unit = {
        isBulkGranting = true
        coroutineScope.launch {
            val parts = mutableListOf<String>()
            if (isGodMode) {
                isCheckingRoot = true
                hasRoot = withContext(Dispatchers.Default) { commandExecutor.isRootAvailable() }
                isCheckingRoot = false
                if (hasRoot) {
                    val report = RootHelper.grantAllPermissions()
                    parts += "выдано доступов: ${report.granted}" +
                        if (report.failed > 0) " (часть запрещена системой: ${report.failed})" else ""
                } else {
                    parts += "root не выдан"
                }
            }
            audioController.requestPermission()
            notificationController.requestPermission()
            smsController.requestPermission()
            smsSendController.requestPermission()
            calendarController.requestPermission()
            kotlinx.coroutines.delay(600)
            hasMicrophone = audioController.hasPermission()
            hasNotifications = notificationController.hasPermission()
            hasAccessibility = accessibilityController.hasPermission()
            hasGodModePack = smsController.hasPermission() &&
                smsSendController.hasPermission() &&
                calendarController.hasPermission()
            parts += "остальное можно дожать в разделе «Подробно»"
            bulkGrantSummary = parts.joinToString(" · ")
            AppLogger.i("StartupPermission", "Быстрые настройки: ${bulkGrantSummary.orEmpty()}")
            isBulkGranting = false
        }
    }

    // Backup import straight from the greeting. The archive is only read here —
    // the database and model files are restored after the user confirms the diff.
    val backupController = rememberBackupImportController(
        preparePreview = { json, sections -> dataRepository.prepareSettingsImport(json, sections) },
        applyImport = { json, sections, mode, payload ->
            val errors = dataRepository.applyPreparedImport(json, sections, mode, payload)
            if (errors == 0) ImportResult.Success else ImportResult.PartialSuccess(errors)
        },
        onDone = { result ->
            com.katya.app.showToast(
                when (result) {
                    is ImportResult.Success -> "Бекап импортирован"
                    is ImportResult.PartialSuccess -> "Импорт завершён с ошибками: ${result.errorCount}"
                    is ImportResult.Failure -> "Не удалось импортировать бекап"
                },
            )
            appSettings.setOnboardingCompleted(true)
            onComplete()
        },
    )

    backupController.pending?.let { pending ->
        ImportPreviewDialog(
            sectionDetails = pending.sectionDetails.toImmutableMap(),
            previews = pending.previews,
            onConfirm = backupController.confirm,
            onDismiss = backupController.dismiss,
        )
    }

    if (showNoRootDialog) {
        AlertDialog(
            onDismissRequest = { showNoRootDialog = false },
            confirmButton = {
                TextButton(onClick = { showNoRootDialog = false }) {
                    Text("Понятно")
                }
            },
            title = { Text("Отказ в доступе") },
            text = {
                Column {
                    Text("😭 \n 😇 🪽 К сожалению без root прав вам не стать богом... 🪽 😇")
                    Spacer(Modifier.height(16.dp))
                    Text("😈 Но выход есть всегда...")
                    Spacer(Modifier.height(4.dp))
                    Text("🐈‍⬛ GitHub: https://github.com/Gegaremant/meizu_note21_m411h_root 😈")
                }
            },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))

                // Katya Hologram Avatar Mockup
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(45.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(45.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(42.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Привет! Меня зовут Катя. Давай знакомиться",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                when (step) {
                    OnboardingStep.Greeting -> {
                        // The voiced option waits 10s so a reflex tap can't skip the intro.
                        var secondsLeft by remember { mutableIntStateOf(GREETING_COUNTDOWN_SECONDS) }
                        LaunchedEffect(Unit) {
                            while (secondsLeft > 0) {
                                delay(1000)
                                secondsLeft -= 1
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = introBody,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        introVoiceDisabled = false
                                        appSettings.setIntroVoiceDisabled(false)
                                        step = OnboardingStep.Freedom
                                    },
                                    enabled = secondsLeft == 0,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (secondsLeft > 0) {
                                            "Продолжить знакомство — текст и озвучка ($secondsLeft)"
                                        } else {
                                            "Продолжить знакомство — текст и озвучка"
                                        },
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        introVoiceDisabled = true
                                        appSettings.setIntroVoiceDisabled(true)
                                        textToSpeech?.stop()
                                        step = OnboardingStep.Freedom
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Продолжить знакомство без озвучки")
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        textToSpeech?.stop()
                                        appSettings.setOnboardingCompleted(true)
                                        onComplete()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Пропустить знакомство")
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { backupController.launchPicker() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Импорт бекапов")
                                }
                            }
                        }
                    }

                    OnboardingStep.Freedom -> {
                        Text(
                            text = "На сколько ты хочешь быть со мной близок? " +
                                "Сколько свободы готов мне выделить?",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        FREEDOM_MODES.forEachIndexed { index, mode ->
                            ModeSelectorItem(
                                title = mode.title,
                                description = "${mode.tagline}\n${mode.technical}",
                                selected = selectedMode == index,
                                accent = mode.godMode,
                                isLoading = mode.godMode && isCheckingRoot,
                                onClick = {
                                    if (mode.godMode && isCheckingRoot) return@ModeSelectorItem
                                    if (mode.godMode) {
                                        // God_Mode starts with a root request, like the old flow.
                                        isCheckingRoot = true
                                        coroutineScope.launch {
                                            val rootAvailable = withContext(Dispatchers.Default) {
                                                commandExecutor.isRootAvailable()
                                            }
                                            isCheckingRoot = false
                                            if (rootAvailable) {
                                                hasRoot = true
                                                applyMode(index)
                                            } else {
                                                showNoRootDialog = true
                                            }
                                        }
                                    } else {
                                        applyMode(index)
                                    }
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { step = OnboardingStep.QuickSetup },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Дальше")
                        }
                    }

                    OnboardingStep.QuickSetup -> {
                        Text(
                            text = "Быстрые настройки",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isGodMode) {
                                "Ккккккруто! Вот мы сейчас с тобой зажжём! Выбирай, что мне можно творить на устройстве."
                            } else {
                                "Можно выдать всё одним нажатием, а детали — потом, в разделе «Подробно»."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = grantEverything,
                            enabled = !isBulkGranting,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            if (isBulkGranting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Разрешить все!", fontWeight = FontWeight.Bold)
                            }
                        }
                        bulkGrantSummary?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Модель для первого разговора:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        var currentMode by remember { mutableStateOf(dataRepository.getFreeMode()) }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                currentMode = FreeMode.FAST
                                applyFreeMode(FreeMode.FAST)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = currentMode == FreeMode.FAST, onClick = {
                                currentMode = FreeMode.FAST
                                applyFreeMode(FreeMode.FAST)
                            })
                            Text("Бесплатная быстрая", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                currentMode = FreeMode.EXPERT
                                applyFreeMode(FreeMode.EXPERT)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = currentMode == FreeMode.EXPERT, onClick = {
                                currentMode = FreeMode.EXPERT
                                applyFreeMode(FreeMode.EXPERT)
                            })
                            Text("Бесплатная экспертная", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showDetails = !showDetails },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (showDetails) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Подробно: настроить каждое разрешение отдельно",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (showDetails) {
                            Spacer(Modifier.height(8.dp))
                            if (isGodMode) {
                                PermissionItem(
                                    title = "Root-права (su)",
                                    description = "Позволяет выполнять системные shell-команды напрямую.",
                                    isGranted = hasRoot,
                                    isLoading = isCheckingRoot,
                                    onRequest = {
                                        coroutineScope.launch {
                                            isCheckingRoot = true
                                            var attempts = 0
                                            hasRoot = false
                                            while (attempts < 15 && !hasRoot) {
                                                hasRoot = withContext(Dispatchers.Default) {
                                                    commandExecutor.isRootAvailable()
                                                }
                                                if (hasRoot) break
                                                kotlinx.coroutines.delay(1000)
                                                attempts++
                                            }
                                            isCheckingRoot = false
                                        }
                                    },
                                )
                            }
                            PermissionItem(
                                title = "Специальные возможности",
                                description = "Управление интерфейсом, автоматизация, работа с выключенным экраном.",
                                isGranted = hasAccessibility,
                                onRequest = {
                                    coroutineScope.launch { hasAccessibility = accessibilityController.requestPermission() }
                                },
                            )
                            PermissionItem(
                                title = "Доступ к микрофону",
                                description = "Нужен для распознавания голоса и общения.",
                                isGranted = hasMicrophone,
                                onRequest = {
                                    coroutineScope.launch { hasMicrophone = audioController.requestPermission() }
                                },
                            )
                            PermissionItem(
                                title = "Уведомления",
                                description = "Отчёты о фоновой работе и напоминания.",
                                isGranted = hasNotifications,
                                onRequest = {
                                    coroutineScope.launch { hasNotifications = notificationController.requestPermission() }
                                },
                            )
                            if (notificationListenerController.isSupported()) {
                                PermissionItem(
                                    title = "Чтение уведомлений",
                                    description = "Реагировать на входящие сообщения и системные уведомления.",
                                    isGranted = hasNotificationListener,
                                    onRequest = { notificationListenerController.openAccessSettings() },
                                )
                            }
                            PermissionItem(
                                title = "Работа в фоновом режиме",
                                description = "Отключить оптимизацию батареи, чтобы Катя не засыпала.",
                                isGranted = hasBatteryIgnore,
                                onRequest = {
                                    coroutineScope.launch { hasBatteryIgnore = batteryController.requestPermission() }
                                },
                            )
                            PermissionItem(
                                title = "Точные будильники",
                                description = "Для запуска планировщика задач точно в срок.",
                                isGranted = hasExactAlarms,
                                onRequest = {
                                    coroutineScope.launch { hasExactAlarms = exactAlarmController.requestPermission() }
                                },
                            )
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
                                    },
                                )
                                PermissionItem(
                                    title = "Помощник по умолчанию",
                                    description = "Назначить Катю системным цифровым помощником.",
                                    isGranted = hasDefaultAssistant,
                                    onRequest = { systemRoleController.openDefaultAssistantSettings() },
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                appSettings.setOnboardingCompleted(true)
                                onComplete()
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                        ) {
                            Text(
                                text = "Продолжить работу",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
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
    isLoading: Boolean = false,
    onClick: () -> Unit,
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
            .clickable(enabled = !isLoading) { onClick() }
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = if (accent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                } else {
                    RadioButton(
                        selected = selected,
                        onClick = onClick,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = if (accent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 40.dp),
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
    onRequest: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Предоставлено",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Button(
                        onClick = onRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Разрешить",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
