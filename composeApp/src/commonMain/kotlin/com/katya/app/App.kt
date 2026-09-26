@file:OptIn(ExperimentalMaterial3Api::class)

package com.katya.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.katya.app.data.AppSettings
import com.katya.app.data.ThemeMode
import com.katya.app.tools.BatteryOptimizationPermissionController
import com.katya.app.tools.CalendarPermissionController
import com.katya.app.tools.ExactAlarmPermissionController
import com.katya.app.tools.LocalNetworkPermissionController
import com.katya.app.tools.NotificationPermissionController
import com.katya.app.tools.SetupBatteryOptimizationPermissionHandler
import com.katya.app.tools.SetupCalendarPermissionHandler
import com.katya.app.tools.SetupExactAlarmPermissionHandler
import com.katya.app.tools.SetupLocalNetworkPermissionHandler
import com.katya.app.tools.SetupNotificationPermissionHandler
import com.katya.app.tools.SetupSmsPermissionHandler
import com.katya.app.tools.SetupSmsSendPermissionHandler
import com.katya.app.tools.SmsPermissionController
import com.katya.app.tools.SmsSendPermissionController
import com.katya.app.tts.SpeechEngine
import com.katya.app.tts.SystemTtsSpeechEngine
import com.katya.app.ui.DarkColorScheme
import com.katya.app.ui.LightColorScheme
import com.katya.app.ui.Theme
import com.katya.app.ui.chat.ChatScreen
import com.katya.app.ui.chat.ChatViewModel
import com.katya.app.ui.components.FullScreenImageHost
import com.katya.app.ui.handCursor
import com.katya.app.ui.settings.SettingsScreen
import com.katya.app.ui.withBlackBackground
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.tab_chat
import katya.composeapp.generated.resources.tab_settings
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.marc_apps.tts.experimental.ExperimentalVoiceApi
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinConfiguration

@Serializable
@SerialName("home")
object Home

@Serializable
@SerialName("settings")
object Settings

@Composable
fun App(
    navController: NavHostController,
    lightColorScheme: ColorScheme = LightColorScheme,
    darkColorScheme: ColorScheme = DarkColorScheme,
    speechEngine: SpeechEngine? = null,
    isKoinStarted: Boolean = false,
    onAppOpens: ((Int) -> Unit)? = null,
) {
    setSingletonImageLoaderFactory { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                add(SvgDecoder.Factory())
            }
            .build()
    }

    // Reuse global Koin if already started (Android Application class),
    // otherwise create a new instance (iOS, Desktop, Wasm).
    if (isKoinStarted) {
        AppContent(navController, lightColorScheme, darkColorScheme, speechEngine, onAppOpens)
    } else {
        KoinApplication(
            configuration = koinConfiguration {
                modules(appModule, com.katya.app.stt.sttModule, com.katya.app.audio.audioModule)
            },
        ) {
            AppContent(navController, lightColorScheme, darkColorScheme, speechEngine, onAppOpens)
        }
    }
}

@Composable
private fun AppContent(
    navController: NavHostController,
    lightColorScheme: ColorScheme,
    darkColorScheme: ColorScheme,
    speechEngine: SpeechEngine?,
    onAppOpens: ((Int) -> Unit)?,
) {
    val appSettings = koinInject<AppSettings>()

    // Track app opens after Koin is initialized
    onAppOpens?.let { callback ->
        LaunchedEffect(Unit) {
            callback(appSettings.trackAppOpen())
        }
    }

    // Set up permission handlers
    val calendarPermissionController = koinInject<CalendarPermissionController>()
    SetupCalendarPermissionHandler(calendarPermissionController)

    val notificationPermissionController = koinInject<NotificationPermissionController>()
    SetupNotificationPermissionHandler(notificationPermissionController)

    val localNetworkPermissionController = koinInject<LocalNetworkPermissionController>()
    SetupLocalNetworkPermissionHandler(localNetworkPermissionController)

    val smsPermissionController = koinInject<SmsPermissionController>()
    SetupSmsPermissionHandler(smsPermissionController)

    val smsSendPermissionController = koinInject<SmsSendPermissionController>()
    SetupSmsSendPermissionHandler(smsSendPermissionController)

    val exactAlarmPermissionController = koinInject<ExactAlarmPermissionController>()
    SetupExactAlarmPermissionHandler(exactAlarmPermissionController)

    val batteryOptimizationPermissionController = koinInject<BatteryOptimizationPermissionController>()
    SetupBatteryOptimizationPermissionHandler(batteryOptimizationPermissionController)

    // Set TTS voice to match system language (system/RHVoice backends only —
    // cloud and on-device engines pick their own voice from settings).
    //
    // The voice list is populated asynchronously right after TTS init, so a
    // one-shot lookup usually sees an empty list and silently keeps the engine's
    // default (which often is a male voice). Retry a few times until voices are
    // ready, then prefer a female voice — Katya's greeting must not sound male.
    // Important: if no female voice is found, do NOT force any voice — leave the
    // engine's default (i.e. the one the user chose in the Android TTS settings).
    // Forcing matchingVoices.firstOrNull() there swallowed the Android voice pick.
    @OptIn(ExperimentalVoiceApi::class)
    LaunchedEffect(speechEngine) {
        val instance = (speechEngine as? SystemTtsSpeechEngine)?.instance ?: return@LaunchedEffect
        val systemLanguage = Locale.current.language
        repeat(10) {
            val voices = runCatching { instance.voices.toList() }.getOrDefault(emptyList())
            if (voices.isNotEmpty()) {
                val matchingVoices = voices.filter { it.languageTag.startsWith(systemLanguage) }
                // If the matching set is empty (unusual locale mapping), fall back to
                // the full list so a proper female voice is still preferred.
                val pool = matchingVoices.ifEmpty { voices }

                val femaleVoice = pool.firstOrNull { it.isFemaleVoice() }
                    ?: voices.firstOrNull { it.isFemaleVoice() }

                if (femaleVoice != null) {
                    instance.currentVoice = femaleVoice
                }
                // No female voice available → keep the engine default (user's Android
                // TTS pick) instead of overriding it with a male voice.
                return@LaunchedEffect
            }
            delay(300)
        }
    }

    val sysTtsPitch by appSettings.sysTtsPitchFlow.collectAsStateWithLifecycle()
    val sysTtsRate by appSettings.sysTtsRateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(speechEngine, sysTtsPitch, sysTtsRate) {
        val instance = (speechEngine as? SystemTtsSpeechEngine)?.instance ?: return@LaunchedEffect
        instance.pitch = sysTtsPitch
        instance.rate = sysTtsRate
    }

    val uiScale by appSettings.uiScaleFlow.collectAsStateWithLifecycle()
    val defaultDensity = LocalDensity.current
    val scaledDensity = remember(defaultDensity, uiScale) {
        Density(defaultDensity.density * uiScale, defaultDensity.fontScale)
    }

    val themeMode by appSettings.themeModeFlow.collectAsStateWithLifecycle()
    val systemInDark = isSystemInDarkTheme()
    val effectiveColorScheme = when (themeMode) {
        ThemeMode.System -> if (systemInDark) darkColorScheme else lightColorScheme
        ThemeMode.Light -> lightColorScheme
        ThemeMode.Dark -> darkColorScheme
        ThemeMode.OledBlack -> darkColorScheme.withBlackBackground()
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
    ) {
        Theme(colorScheme = effectiveColorScheme) {
            // Keyed on the stored value: onboarding state is also restored by "import
            // settings", and an unkeyed remember left the flow showing (or hidden) against
            // whatever the user had just imported.
            val storedOnboardingCompleted = appSettings.isOnboardingCompleted()
            var isOnboardingCompleted by remember(storedOnboardingCompleted) {
                mutableStateOf(storedOnboardingCompleted)
            }

            if (!isOnboardingCompleted && currentPlatform is Platform.Mobile.Android) {
                com.katya.app.ui.components.StartupPermissionFlow(textToSpeech = speechEngine) {
                    isOnboardingCompleted = true
                }
            } else {
                FullScreenImageHost {
                    val chatViewModel: ChatViewModel = koinViewModel()
                    val showTabBar = currentPlatform !is Platform.Mobile
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val isHome = currentBackStackEntry?.destination?.route == "home"

                    val navigationTabBar: @Composable () -> Unit = {
                        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                        val count = 2
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = isHome,
                                onClick = {
                                    navController.navigate(Home) {
                                        popUpTo(Home) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = if (isRtl) count - 1 else 0, count = count),
                                modifier = Modifier.handCursor(),
                            ) {
                                Text(stringResource(Res.string.tab_chat))
                            }
                            SegmentedButton(
                                selected = !isHome,
                                onClick = {
                                    navController.navigate(Settings) {
                                        popUpTo(Home)
                                        launchSingleTop = true
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = if (isRtl) 0 else count - 1, count = count),
                                modifier = Modifier.handCursor(),
                            ) {
                                Text(stringResource(Res.string.tab_settings))
                            }
                        }
                    }

                    NavHost(
                        navController,
                        startDestination = Home,
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    ) {
                        composable<Home> {
                            ChatScreen(
                                viewModel = chatViewModel,
                                textToSpeech = speechEngine,
                                onNavigateToSettings = {
                                    navController.navigate(Settings)
                                },
                                isSandboxAvailable = currentPlatform is Platform.Mobile.Android,
                                navigationTabBar = if (showTabBar) navigationTabBar else null,
                            )
                        }
                        composable<Settings> {
                            if (showTabBar) {
                                DisposableEffect(Unit) {
                                    onDispose {
                                        chatViewModel.refreshSettings()
                                    }
                                }
                            }
                            SettingsScreen(
                                textToSpeech = speechEngine,
                                onNavigateBack = {
                                    chatViewModel.refreshSettings()
                                    navController.navigateUp()
                                },
                                navigationTabBar = if (showTabBar) navigationTabBar else null,
                            )
                        }
                    }
                }
            }
        }
        FirstRunComponentsDialog()
    }
}

@Composable
private fun FirstRunComponentsDialog() {
    val appSettings = koinInject<AppSettings>()
    val componentsRepository = koinInject<com.katya.app.components.ComponentsRepository>()
    val launcher = koinInject<com.katya.app.components.ComponentDownloadLauncher>()
    val components by componentsRepository.components.collectAsStateWithLifecycle()
    val deviceAbi = com.katya.app.components.currentAbi()
    var dismissed by remember { mutableStateOf(appSettings.isComponentsPromptSkipped()) }

    val missing = remember(components, dismissed) {
        if (dismissed) {
            emptyList()
        } else {
            components.filter { c ->
                c.id != com.katya.app.components.ComponentsRepository.SEED_MARKER_ID &&
                    (c.abi == null || c.abi == deviceAbi) &&
                    c.status != "installed"
            }
        }
    }

    if (missing.isNotEmpty() && !appSettings.isComponentsPromptSkipped()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                dismissed = true
                appSettings.setComponentsPromptSkipped(true)
            },
            title = { androidx.compose.material3.Text("Доскачать компоненты?") },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text(
                        "Катя больше не таскает лишнее в APK — " +
                            "Debian, Proot и остальное качается по запросу:",
                    )
                    missing.forEach { c ->
                        androidx.compose.material3.Text(
                            "• ${c.name}",
                            modifier = androidx.compose.ui.Modifier.padding(start = 8.dp, top = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    missing.forEach { launcher.startDownload(it.id) }
                    dismissed = true
                }) { androidx.compose.material3.Text("Скачать в фоне") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    dismissed = true
                    appSettings.setComponentsPromptSkipped(true)
                }) { androidx.compose.material3.Text("Позже") }
            },
        )
    }
}

@OptIn(ExperimentalVoiceApi::class)
private fun nl.marc_apps.tts.Voice.isFemaleVoice(): Boolean {
    val name = name.lowercase()
    return name.contains("female") ||
        name.contains("woman") ||
        // RHVoice female short codes (ru-ru): Elena (dfc), Alena (dfa),
        // Arina (dfd), Natasha (dft), Irina (dfa-2?) — keep a loose "d-f" prefix
        // so future female builds still match without updating this list.
        name.contains("d-f") ||
        // Common female voice names (RHVoice, Google, various TTS providers)
        name.contains("elena") || name.contains("alena") ||
        name.contains("arina") || name.contains("milena") ||
        name.contains("natasha") || name.contains("tanya") ||
        name.contains("tanja") || name.contains("sonja") ||
        name.contains("sonya") || name.contains("koroleva") ||
        name.contains("irina") || name.contains("iriska") ||
        name.contains("anna") || name.contains("julia") ||
        name.contains("julie") || name.contains("kate") ||
        name.contains("katya") || name.contains("katia") ||
        name.contains("lena") || name.contains("olga") ||
        name.contains("maria") || name.contains("masha") ||
        name.contains("dasha") || name.contains("vera") ||
        name.contains("sveta") || name.contains("zina") ||
        name.contains("ava") || name.contains("emma") ||
        name.contains("mia") || name.contains("zoey") ||
        name.contains("samantha") || name.contains("victoria") ||
        name.contains("serena") || name.contains("susan") ||
        name.contains("sarah") || name.contains("amy") ||
        name.contains("alice") || name.contains("monica") ||
        name.contains("lisa") || name.contains("jane") ||
        name.contains("lucy") || name.contains("lily") ||
        name.contains("hazel") || name.contains("evelyn") ||
        name.contains("aubrey") || name.contains("audrey") ||
        name.contains("genevieve") || name.contains("georgie")
}
