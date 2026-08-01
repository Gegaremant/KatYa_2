# Release Notes
## v2.2\n### Added\n- **Default Assistant:** Katya can now be set as the default digital assistant in Android.\n- **Root Command Tool:** Native ability to execute root shell commands internally via su -c.\n- **Accessibility Service:** Added support for screen reading and UI interactions without root API.\n- **Auto Backup:** Added automatic ZIP configuration backups triggered on successful SSH tunnel connections.\n- **Import/Export:** Support for importing and exporting backups in ZIP format.
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
