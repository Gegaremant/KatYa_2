Был такой план, что-то сделано, что то нет. Надо 
1 проверить, что все задачи выполнены. Те которые не закончены/не реализованы необходимо реализовать.
2 Не знаю где но надо Rename kai_secure_prefs.xml to katya_secure_prefs.xml
3 После реализации меняем версию в манифесте и в интерфейсе на 3.0.9
4 запускаем сборку локально
5 Проследи, что бы сборка прошла без ошибок, в случае обнаружения ошибок - устани их. 
6 после окончания успешлной сборки переименуй готовыйй apk в Katya-3.0.8.apk
7 выложи  в корневую лиректорию проекта 
8 составь отчет о проделаной работе

# Plan for 3.0.8 Update

## 1. Local Servers (`ServersContent.kt` & `SshTunnelService`)
- **[MODIFY]** `ServersContent.kt`: 
  - Change password toggle to use icons (eye open/closed) instead of text.
  - Remove "Постоянное авто-восстановление" toggle (already global).
  - Remove "Поднять туннель" button.
  - Move "Сохранить настройки" button down and bind it to trigger `startTunnel()` after saving.

## 2. DeepSeek Auth (`DeepSeekAuthDialog.kt`)
- **[MODIFY]** `DeepSeekAuthDialog.kt`:
  - Fix RTL (Right-to-Left) bug by removing any reversing `visualTransformation` or RTL modifiers.
  - Integrate `doctor` (lsscan) logic to the authorization flow.

## 3. Legacy Endpoints & 3.0.7 Backlog
- **[MODIFY]** `SettingsViewModel.kt` or `ServiceEntry`: Add "Legacy version" (Free fast/expert) fallback endpoints and "Self hosted" open-ai compatible endpoint to the Free API list.

## 4. Root & Initial Setup
- **[MODIFY]** `GeneralSettings.kt` / `SettingsViewModel.kt`:
  - Default mode to Sandbox instead of God Mode.
  - On God Mode toggle, properly check root (blocking). If failed, show Katya's specific message + Github link.
  - Fix state flashing for root check.

## 5. Versioning
- **[MODIFY]** `build.gradle.kts` (or equivalent config): Update app version to 3.0.8.

## 6. STT/TTS Updates
- **[MODIFY]** STT logic (`SttController.kt` / Settings):
  - Integrate GKPSR (Google Keyboard Parsing Speech Recognizer) using the provided `speech.kt`. Set it as the default.
  - Update UI to show "Android" and "GKPSR" (Google Keyboard Parsing Speech Recognizer).
- **[MODIFY]** TTS logic (`GeneralSettings.kt` / `AppDatabase` defaults):
  - Hardcode the default voice to a female voice.

## 7. VLESS Proxy Additions
- **[MODIFY]** `ServersContent.kt` (VLESS/Proxy save logic):
  - Validate the proxy tunnel actually connects before saving and showing a green checkmark.
  - Upon successful save, select the new profile automatically, clear input fields, and collapse the UI.
