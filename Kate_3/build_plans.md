# Планы на следующие билды

| # | Задача | Категория | Приоритет | Статус |
|---|--------|-----------|-----------|--------|
| 1 | Добавить все разрешения в AndroidManifest.xml | permissions | Высокий | pending |
| 2 | Восстановить песочницу Alpine или мигрировать на Termux | sandbox | Высокий | pending |
| 3 | Установить xray-core и настроить VLESS | vless | Высокий | pending |
| 4 | Реверс-инжиниринг протокола S101 через JBand | s101 | Средний | pending |
| 5 | Добавить голосового ассистента (VoiceInteractionService) | voice | Средний | pending |
| 6 | Интегрировать FreeGPTHub (получить aes_gem_key) | llm | Низкий | pending |
| 7 | Интегрировать freellmpool как LLM-роутер | llm | Средний | pending |
| 8 | Создать таблицу build_plans в sqlite | meta | Высокий | pending |

## Детали

### 1. Разрешения
SYSTEM_ALERT_WINDOW, WRITE_SETTINGS, READ_CONTACTS, WRITE_CONTACTS, READ_CALL_LOG, CALL_PHONE, ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION, PACKAGE_USAGE_STATS, CAMERA, BODY_SENSORS, BIND_VOICE_INTERACTION, BIND_NOTIFICATION_LISTENER_SERVICE, ANSWER_PHONE_CALLS, INSTALL_PACKAGES, DELETE_PACKAGES, BIND_ACCESSIBILITY_SERVICE, MANAGE_EXTERNAL_STORAGE, READ_LOGS, DUMP

### 2. Песочница
Текущая Alpine упала из-за libtalloc.so.2. Нужен рабочий bash, sqlite3, python3, curl и монтирование sdcard.

### 3. VLESS
Скачать xray-core, создать config.json из дампа, автостарт и диагностика прокси.

### 4. S101
Включить Bluetooth HCI snoop log, перехватить трафик JBand ↔ часы, восстановить протокол.

### 5. Голосовой ассистент
BIND_VOICE_INTERACTION, VoiceInteractionService, системное приложение в /system/priv-app.

### 6. FreeGPTHub
AES-ключ через WeChat (Charles的皮卡丘). 9 бесплатных LLM-провайдеров.

### 7. freellmpool
Локальный прокси на порту 8080, автофолбэк между 24 провайдерами, без ключей для старта.

### 8. Таблица build_plans
Создать sqlite-базу для отслеживания задач (замена этому md-файлу).
