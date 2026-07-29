package com.katya.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.katya.app.data.AppSettings
import com.katya.app.data.DataRepository
import com.katya.app.data.ThemeMode
import com.katya.app.ui.DarkColorScheme
import com.katya.app.ui.LightColorScheme
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import nl.marc_apps.tts.TextToSpeechEngine
import nl.marc_apps.tts.rememberTextToSpeechOrNull
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FileKit.init(this)
        handleDeepLinkIntent(intent)
        checkSystemPermissions()

        val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val appSettings: AppSettings = get()
        setContent {
            val themeMode by appSettings.themeModeFlow.collectAsStateWithLifecycle()
            val systemInDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                ThemeMode.System -> systemInDark
                ThemeMode.Light -> false
                ThemeMode.Dark, ThemeMode.OledBlack -> true
            }
            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                )
            }
            val context = LocalContext.current
            val lightScheme: ColorScheme = if (dynamicColor) dynamicLightColorScheme(context) else LightColorScheme
            val darkScheme: ColorScheme = if (dynamicColor) dynamicDarkColorScheme(context) else DarkColorScheme
            val navController = rememberNavController()
            // Defer TTS initialization until after the first frame
            var ttsReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { ttsReady = true }
            val textToSpeech = if (ttsReady) {
                rememberTextToSpeechOrNull(TextToSpeechEngine.SystemDefault)
            } else {
                null
            }
            App(
                navController = navController,
                lightColorScheme = lightScheme,
                darkColorScheme = darkScheme,
                textToSpeech = textToSpeech,
                isKoinStarted = true,
                onAppOpens = { appOpens ->
                    if (appOpens % 5 == 0) {
                        requestReview(this@MainActivity)
                    }
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // Re-assert the daemon every time the activity is brought to the foreground.
        // `onCreate`-only is not enough: aggressive OEM battery managers (MIUI,
        // EMUI/Huawei) sometimes kill the foreground service while the activity
        // is still alive in the background — without this, the user has to fully
        // close and reopen the app for scheduling to resume. `startForegroundService`
        // is idempotent when the service is already up.
        autoStartDaemon()
    }

    private fun autoStartDaemon() {
        val daemonController: DaemonController = get()
        if (daemonController is AndroidDaemonController && daemonController.shouldAutoStart()) {
            daemonController.start()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_HEARTBEAT, false) == true) {
            val dataRepository: DataRepository = get()
            dataRepository.requestOpenHeartbeat()
            // Drop the extra so a configuration change (screen rotation) doesn't re-trigger
            // the deep-link after ChatViewModel has already consumed it.
            intent.removeExtra(EXTRA_OPEN_HEARTBEAT)
        }
        if (intent?.action == Intent.ACTION_ASSIST) {
            val dataRepository: DataRepository = get()
            dataRepository.requestOpenAssist()
            // clear action so it does not keep triggering on rotation or reopening
            // chat after ChatViewModel has already consumed the request.
            intent.action = null
        }
        if (intent?.action == Intent.ACTION_VIEW) {
            val url = intent.dataString
            if (url != null && url.endsWith(".zip") && url.contains("alphacephei.com")) {
                val dataRepository: DataRepository = get()
                val wakeWordPlatform: com.katya.app.stt.WakeWordPlatform = get()
                dataRepository.setWakeWordModelLang(url)
                dataRepository.setWakeWordEnabled(true)
                wakeWordPlatform.startDownload(url)
            }
        }
    }

    private val autoRevokeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // finished
    }

    private val batteryOptLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        checkAutoRevokePermission()
    }

    private fun checkSystemPermissions() {
        val appSettings: AppSettings = get()
        if (!appSettings.hasRequestedRoot()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Запрос Root-прав")
                .setMessage("Предоставить приложению Root-доступ? Это может понадобиться для продвинутого сетевого взаимодействия (туннелей) и некоторых системных функций.")
                .setPositiveButton("Разрешить") { _, _ ->
                    appSettings.setRequestedRoot(true)
                    requestRoot()
                    checkBatteryOptimization()
                }
                .setNegativeButton("Отклонить") { _, _ ->
                    appSettings.setRequestedRoot(true)
                    checkBatteryOptimization()
                }
                .setCancelable(false)
                .show()
        } else {
            // If already requested, we can silently try to get root in background just in case it was granted
            requestRoot()
            checkBatteryOptimization()
        }
    }

    private fun requestRoot() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                process.outputStream.write("exit\n".toByteArray())
                process.outputStream.flush()
                process.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    /**
     * Проверяет, отключена ли оптимизация энергосбережения для приложения.
     * Если не отключена — показывает пользователю диалог с объяснением, зачем это нужно
     * (для работы демона, туннелей в фоне), и предлагает перейти в настройки или проигнорировать.
     */
    private fun checkBatteryOptimization() {
        // 2. Battery Optimization
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Настройка энергосбережения")
                .setMessage("Для нормальной работы приложения в фоновом режиме (демон, туннели и автозапуск) требуется отключить автоматическое управление энергопитанием.")
                .setPositiveButton("Настроить сейчас") { _, _ ->
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    try {
                        batteryOptLauncher.launch(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        checkAutoRevokePermission()
                    }
                }
                .setNegativeButton("Игнорировать") { _, _ ->
                    checkAutoRevokePermission()
                }
                .setCancelable(false)
                .show()
        } else {
            checkAutoRevokePermission()
        }
    }

    private fun checkAutoRevokePermission() {
        // 3. Disable App Hibernation (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pm = packageManager
            if (!pm.isAutoRevokeWhitelisted) {
                try {
                    val intent = Intent(android.content.Intent.ACTION_AUTO_REVOKE_PERMISSIONS)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    autoRevokeLauncher.launch(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(navController = rememberNavController())
}
