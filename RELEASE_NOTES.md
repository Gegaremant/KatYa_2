# Release Notes

## v3.0.2
### Added & Improved
- **Device Admin & Trust Agent Cleanup:** Removed Device Admin and Trust Agent checks to simplify permissions and app startup.
- **Authorization Text Direction Fix:** Guaranteed LTR text layout for login, credentials, and authorization dialogs.
- **Header & Title Styling:** Set light color for "Слух и Речь (STT/TTS)" and updated UI card header titles.
- **Settings Import & Deduplication:** "Заменить настройки" defaults to OFF (`false`). Importing settings automatically merges new items with existing ones without duplicate entries.
- **VLESS Proxy Connection & Xray Core:** Fixed Xray JSON output (removed empty `"flow": ""` field that caused parse crashes), added binary execution fallback paths in proot/Termux, and updated connection ping target.
- **Local STT/TTS Model Download Checks:** Added model readiness checks when selecting local STT (Vosk) or TTS (Piper/HRVoise) engines with download prompt dialogs.
- **Microphone "Внимаю" Banner:** Replaced full-screen listening overlay with a non-blocking bottom banner showing "Внимаю" and real-time speech recognition text.
- **Device & Connection Status Header:** Added status header lines in main chat for Battery %, charging, CPU/RAM/sensor stats, and API connection status.
- **Reasoning & Thoughts Display:** Added toggle support to unspoiler `<think>` blocks and read reasoning aloud via TTS voice synthesis.
- **Email Provider Quick Presets:** Added quick preset chips (Gmail, Outlook, Yandex, Mail.ru) in the Add Email Account dialog with automatic IMAP/SMTP server configuration.
- **Camera Runtime Permission Fix:** Added runtime `Manifest.permission.CAMERA` permission request and try-catch safety wrapper, resolving camera launch crashes.

## v2.4.3
### Added
- **Clean Sandbox Install:** `LinuxSandboxManager` now performs a clean wipe of `rootfs`, `home`, and `tmp` directories when installing the sandbox to avoid caching bugs.
- **Action Logging:** Added robust UI logging of background actions like "Запрашиваю root-права для VLESS" directly into the UI state.
- **Redesigned Debug & Server Settings:** Consolidated debug logging settings into a new "Отладка" block on the Servers tab with levels: Выкл, Размышления, Кратко, and Полная.
- **Memory Tool Fixes:** Fixed a localization issue where all tools falsely displayed the same memory string resources.
- **Task Scheduling Refinements:** Tasks and triggers have been renamed to "Однократно", "Расписание", and "Пульс" for clarity.

## v2.4.15
### Added
- **Manual Memory & Tasks:** Added `AddMemorySheet` and `AddEditTaskSheet` UI to manually add memories and schedule tasks (with TIME, CRON, and HEARTBEAT triggers) without relying solely on Katya's autonomous actions.
- **Operating Modes (Sandbox / God Mode / Bare Android):** Added UI settings to switch Katya between full system access (God Mode) and restricted modes. System prompts and permissions dynamically adjust based on the selected mode.
- **Interactive Onboarding:** Introduced `StartupPermissionFlow` with a voice greeting on the very first launch, offering a beautiful checklist for granting necessary permissions.
- **Agent-Reach & Skills Knowledge:** Integrated dynamic capability awareness into the system prompt, so Katya knows whether she has network access (Agent-Reach) and can leverage available Hermes skills.
- **Dynamic Local Proxy Bypass:** Fixed connection issues by routing local loopback traffic (localhost, 127.0.0.1) directly to services like FreeDeepSeekAPI, bypassing the VLESS proxy.
### Changed
- **Token Extraction:** Improved token extraction reliability for DeepSeekAuthDialog by correctly parsing both cookies and localStorage.
- **Model Selector:** Unified the model selector to robustly fallback to default models when connection status is unknown.

## v2.3
### Added
- **System Control (Root/Sandbox):** Katya can now execute local shell commands natively via the new `ExecuteCommandTool`. It automatically detects Root (`su`) and uses it if available, or falls back to the app sandbox.
- **Native File Downloader:** Katya can download files directly from the web and place them into system directories (if rooted) using the new `DownloadFileTool`.
- **Advanced SSH & SFTP:** Added generic `SshTool` and `SftpTool` allowing Katya to connect to any server to execute arbitrary commands or transfer files seamlessly.
- **Calendar Integration:** Added `CalendarTool` to allow Katya to read and create calendar events using the native Android calendar provider.
- **App Guts Analyzer:** Added `AppGutsTool` giving Katya root-level dumpsys package insight (like AppManager backend).
- **System Intents:** Added `IntentTool` to allow Katya to navigate the Android UI, open activities, and trigger services dynamically.
- **Root Apps Catalog:** Added `RootAppsCatalogTool` to allow Katya to parse and suggest apps from the awesome-android-root repository.
- **Voice UI Modes:** Added support for switching between Full Screen and Bottom Sheet voice interfaces in the settings.
- **Smart Truncation:** Large text file uploads (> 1MB) are now intelligently truncated (keeping the top 100 and bottom 1000 lines) to avoid API token limits while preserving crucial log information.
- **Default Assistant:** Katya can now be set as the default digital assistant in Android.
- **Auto Backup:** Added automatic ZIP configuration backups triggered on successful SSH tunnel connections.
- **Import/Export:** Support for importing and exporting backups in ZIP format.
### Fixed
- **UI:** Fixed crash and scrolling issues on the App Logs screen when network output is large.
- **Tasks:** Fixed an issue where automated backups would spam the Scheduled Tasks list with completed tasks.
- **Voice (VAD):** Fixed `SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` passing a Long instead of an Int, which broke voice auto-send.
- **Models:** Removed non-functional AutoSearch (proxy search) to favor the seamless fallback system.
### Added
- **Exact Alarms:** The background Heartbeat is now scheduled using Android's `AlarmManager` (with `setExactAndAllowWhileIdle`), guaranteeing execution exactly on time, even during deep Doze sleep.
- **Sequential Startup Permission Flow:** The app now gracefully requests critical system permissions (Notifications, Exact Alarms, Battery Optimization exclusions) one by one at startup, providing clear explanations from Katya for each requirement.
- **Manual Heartbeat Trigger ("Пинок"):** Added a dedicated forced-refresh button for the Heartbeat in the `Agent -> Heartbeat` settings, giving you instant manual control over background tasks.
- **Scheduling via Tools:** Katya can now independently manage tasks (`schedule_task`) and add them to your `ScheduledTaskList` behind the scenes, without relying on the old sandbox UI.
### Changed
- Refactored `ScheduledTaskList` to display tasks correctly and integrated with tool-based task scheduling.

## v1.3.2
### Added
- **Out of the sandbox:** Katya now fully utilizes Root privileges and has access to the full Android file system, Termux, and system APIs.
- **RHVoice Support:** Added ability to directly download and select RHVoice synthesizers in settings.
- **Smart Reconnection:** SSH tunnel now automatically retries connection using exponential backoff when network drops.
- **Termux MCP Servers:** Added pre-configured Local MCP Servers (GitHub, SQLite, Filesystem) for root environment.
### Changed
- Rebranded remaining references from Katya to Katya.
- Global package renamed to `com.katya.app`.
- Removed all multiplatform unused code (iosApp, site, flatpak, aur) to focus heavily on the Android application.
- Exported configurations now default to `[date]_Katya_config.json`.

## [Unreleased]

## v1.0.4
### Added
- Added Root access confirmation dialog on first launch.
- Implemented SSH Tunnel via JSch library for connecting to local models on srv-llm.
- Added Battery Optimization explanation dialog in MainActivity.
- Translated Quick Actions to Russian and added configuration examples.
- Updated Local API AI description with reference to the Servers tab for SSH tunnels.

### Fixed
- Fixed black text on dark theme in GeneralSettings (Dropdowns/Inputs).
### Fixed
- Fixed an issue causing `ScreenshotTest` to crash by gracefully bypassing Koin initialization for STT and `AudioPermissionController` components when running in Compose `LocalInspectionMode`.
- Resolved unresolved references to `SttController` by ensuring Kotlin safe calls (`?.`) are correctly used within `QuestionInput.kt` when STT is disabled in preview mode.
- CI/CD Unit Test pipeline is now restored and `screenshotTests` run successfully alongside unit tests.
