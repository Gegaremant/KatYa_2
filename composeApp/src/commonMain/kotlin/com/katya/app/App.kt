@file:OptIn(ExperimentalMaterial3Api::class)

package com.katya.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
    // ready, then force a female voice — Katya's greeting must not sound male.
    @OptIn(ExperimentalVoiceApi::class)
    LaunchedEffect(speechEngine) {
        val instance = (speechEngine as? SystemTtsSpeechEngine)?.instance ?: return@LaunchedEffect
        val systemLanguage = Locale.current.language
        repeat(10) {
            val voices = runCatching { instance.voices.toList() }.getOrDefault(emptyList())
            if (voices.isNotEmpty()) {
                val matchingVoices = voices.filter { it.languageTag.startsWith(systemLanguage) }

                // Katya default voice must be female
                val femaleVoice = matchingVoices.firstOrNull { it.isFemaleVoice() }
                    ?: voices.firstOrNull { it.isFemaleVoice() }
                    ?: matchingVoices.firstOrNull()

                if (femaleVoice != null) {
                    instance.currentVoice = femaleVoice
                }
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
            var isOnboardingCompleted by remember { mutableStateOf(appSettings.isOnboardingCompleted()) }

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
    }
}

@OptIn(ExperimentalVoiceApi::class)
private fun nl.marc_apps.tts.Voice.isFemaleVoice(): Boolean {
    val name = name.lowercase()
    return name.contains("female") ||
        name.contains("woman") ||
        // RHVoice female voices (ru-ru): Elena (dfc), Alena (dfa), Arina (dfd), Natasha (dft)
        name.contains("d-fc") || name.contains("d-fa") ||
        name.contains("d-fd") || name.contains("d-ft") ||
        // Common female voice names
        name.contains("elena") || name.contains("alena") ||
        name.contains("arina") || name.contains("milena") ||
        name.contains("natasha") || name.contains("tanya") ||
        name.contains("sonja") || name.contains("koroleva")
}
