package com.katya.app

import com.katya.app.data.AppSettings
import com.katya.app.data.ConversationStorage
import com.katya.app.data.DataRepository
import com.katya.app.data.EmailStore
import com.katya.app.data.HeartbeatManager
import com.katya.app.data.MemoryStore
import com.katya.app.data.createConversationSqlDriver
import com.katya.app.db.KatyaDatabase
import com.katya.app.data.NotificationStore
import com.katya.app.data.NotesStore
import com.katya.app.data.RemoteDataRepository
import com.katya.app.data.SmsDraftStore
import com.katya.app.data.SmsStore
import com.katya.app.data.TaskScheduler
import com.katya.app.data.TaskStore
import com.katya.app.data.ToolExecutor
import com.katya.app.data.createConversationPersistence
import com.katya.app.data.runMigrations
import com.katya.app.email.EmailPoller
import com.katya.app.inference.createLocalInferenceEngine
import com.katya.app.mcp.McpServerManager
import com.katya.app.monitor.MonitorService
import com.katya.app.monitor.createMonitorService
import com.katya.app.network.Requests
import com.katya.app.notifications.NotificationReader
import com.katya.app.skills.SkillManager
import com.katya.app.sms.SmsPoller
import com.katya.app.sms.SmsReader
import com.katya.app.sms.SmsSender
import com.katya.app.splinterlands.SplinterlandsApi
import com.katya.app.splinterlands.SplinterlandsBattleRunner
import com.katya.app.splinterlands.SplinterlandsStore
import com.katya.app.tools.AudioPermissionController
import com.katya.app.tools.CalendarPermissionController
import com.katya.app.tools.LocalNetworkPermissionController
import com.katya.app.tools.NotificationListenerController
import com.katya.app.tools.NotificationPermissionController
import com.katya.app.tools.SmsPermissionController
import com.katya.app.tools.SmsSendPermissionController
import com.katya.app.tunnel.SshTunnelService
import com.katya.app.tunnel.createTunnelService
import com.katya.app.ui.chat.ChatViewModel
import com.katya.app.ui.settings.SettingsViewModel
import com.katya.app.ui.settings.SplinterlandsViewModel
import com.katya.app.tools.ExactAlarmPermissionController
import com.katya.app.tools.AccessibilityPermissionController
import com.katya.app.tools.BatteryOptimizationPermissionController

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<CalendarPermissionController> { CalendarPermissionController() }
    single<AudioPermissionController> { AudioPermissionController() }
    single<NotificationPermissionController> { NotificationPermissionController() }
    single<LocalNetworkPermissionController> { LocalNetworkPermissionController() }
    single<SmsPermissionController> { SmsPermissionController() }
    single<SmsSendPermissionController> { SmsSendPermissionController() }
    single<ExactAlarmPermissionController> { ExactAlarmPermissionController() }
    single<BatteryOptimizationPermissionController> { BatteryOptimizationPermissionController() }
    single<AccessibilityPermissionController> { AccessibilityPermissionController() }
    single<SmsReader> { SmsReader() }
    single<SmsSender> { SmsSender() }
    single<NotificationListenerController> { NotificationListenerController() }
    single<NotificationReader> { NotificationReader() }
    single<AppSettings> {
        AppSettings(createSecureSettings()).also {
            it.runMigrations(createLegacySettings())
        }
    }
    single<Requests> {
        Requests()
    }
    single<KatyaDatabase?> {
        val driver = createConversationSqlDriver()
        if (driver != null) KatyaDatabase(driver) else null
    }
    single<ConversationStorage> {
        ConversationStorage(get(), createConversationPersistence(get(), get()))
    }
    single<ToolExecutor> {
        ToolExecutor()
    }
    single<MemoryStore> {
        MemoryStore(get(), get())
    }
    single<TaskStore> {
        TaskStore(get())
    }
    single<EmailStore> {
        EmailStore(get())
    }
    single<EmailPoller> {
        EmailPoller(get<EmailStore>())
    }
    single<SmsStore> {
        SmsStore(get())
    }
    single<SmsPoller> {
        SmsPoller(get<SmsStore>(), get<SmsReader>())
    }
    single<SmsDraftStore> {
        SmsDraftStore(get())
    }
    single<NotificationStore> { NotificationStore(get()) }
    single<NotesStore> { NotesStore(get()) }
    single<SplinterlandsStore> {
        SplinterlandsStore(get())
    }
    single<SplinterlandsApi> {
        SplinterlandsApi()
    }
    single<HeartbeatManager> {
        HeartbeatManager(get(), get(), get(), get())
    }
    single<McpServerManager> {
        McpServerManager(get())
    }
    single<SkillManager> {
        SkillManager()
    }
    single<RemoteDataRepository> {
        RemoteDataRepository(
            requests = get(),
            appSettings = get(),
            conversationStorage = get(),
            toolExecutor = get(),
            memoryStore = get(),
            taskStore = get(),
            heartbeatManager = get(),
            emailStore = get(),
            emailPoller = get(),
            smsStore = get(),
            smsPoller = get(),
            smsReader = get(),
            smsPermissionController = get(),
            smsSendPermissionController = get(),
            smsSender = get(),
            smsDraftStore = get(),
            notificationStore = get(),
            notificationListenerController = get(),
            mcpServerManager = get(),
            skillManager = get(),
            localInferenceEngine = createLocalInferenceEngine(),
        )
    }
    single<DataRepository> { get<RemoteDataRepository>() }
    single<SplinterlandsBattleRunner> {
        SplinterlandsBattleRunner(get(), get(), get<DataRepository>(), get<DaemonController>())
    }
    single<TaskScheduler> {
        TaskScheduler(
            get<DataRepository>(),
            get(),
            get(),
            get(),
            get(),
            get<EmailPoller>(),
            get<SmsStore>(),
            get<SmsPoller>(),
            get<NotificationStore>(),
        )
    }
    single<SshTunnelService> { createTunnelService() }
    single<MonitorService> { createMonitorService() }
    single<DaemonController> { createDaemonController() }
    viewModel { SettingsViewModel(get<com.katya.app.data.AppSettings>(), get<com.katya.app.stt.WakeWordPlatform>(), get<DataRepository>(), get<DaemonController>(), get<NotificationPermissionController>(), get<TaskScheduler>(), localNetworkPermissionController = get<LocalNetworkPermissionController>()) }
    viewModel { SplinterlandsViewModel(get<DataRepository>(), get(), get(), get<SplinterlandsApi>()) }
    viewModel { ChatViewModel(get<DataRepository>(), get<TaskScheduler>(), get<MonitorService>(), get<com.katya.app.stt.WakeWordPlatform>(), get<AppSettings>(), localNetworkPermissionController = get<LocalNetworkPermissionController>()) }
}
