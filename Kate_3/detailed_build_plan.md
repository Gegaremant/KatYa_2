# Detailed Katya Build Plan

## 1. Android Permissions

### Цель
Выдать Кате максимальные права для управления устройством.

### Добавить в AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<uses-permission android:name="android.permission.WRITE_SETTINGS"/>
<uses-permission android:name="android.permission.READ_CONTACTS"/>
<uses-permission android:name="android.permission.WRITE_CONTACTS"/>
<uses-permission android:name="android.permission.READ_CALL_LOG"/>
<uses-permission android:name="android.permission.CALL_PHONE"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.BODY_SENSORS"/>
<uses-permission android:name="android.permission.BIND_VOICE_INTERACTION"/>
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"/>
<uses-permission android:name="android.permission.ANSWER_PHONE_CALLS"/>
<uses-permission android:name="android.permission.INSTALL_PACKAGES"/>
<uses-permission android:name="android.permission.DELETE_PACKAGES"/>
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE"/>
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.READ_LOGS"/>
<uses-permission android:name="android.permission.DUMP"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

### Шаги
1. Добавить блок в манифест при следующей сборке
2. После установки выполнить pm grant для runtime-разрешений
3. Для BIND_VOICE_INTERACTION, BIND_NOTIFICATION_LISTENER_SERVICE, BIND_ACCESSIBILITY_SERVICE — APK должен быть в /system/priv-app
4. Запросить у пользователя разрешения через ActivityResult API

## 2. Sandbox Recovery

### Проблема
Alpine/proot мёртв: libtalloc.so.2 не найдена.

### Вариант A: Починить Alpine
```bash
pkg install libtalloc
pm path com.inspiredandroid.katya | cut -d: -f2 | xargs dirname
su -c "ln -sf /data/data/com.termux/files/usr/lib/libtalloc.so.2 <APP_LIB_DIR>/libtalloc.so.2"
```

### Вариант B: Мигрировать на Termux
- Использовать Termux напрямую для python3, sqlite3, curl, git
- Монтировать sdcard через termux-setup-storage
- Отказаться от Alpine/proot

### Требования
- bash, python3, sqlite3, curl, git, node
- Доступ к /sdcard/
- Фоновые процессы для прокси/xray

## 3. VLESS/Xray

### Текущее состояние
Процессов нет, порты закрыты, конфиги отсутствуют.

### Шаги
```bash
curl -L -o /data/local/tmp/xray.zip https://github.com/XTLS/Xray-core/releases/latest/download/Xray-linux-arm64-v8a.zip
unzip /data/local/tmp/xray.zip -d /data/local/tmp/xray
# Создать config.json из дампа
/data/local/tmp/xray/xray run -c /data/local/tmp/xray/config.json
```

### Проверка конфликтов портов
```bash
netstat -tulpn | grep -E "1080|10808|10809"
```

## 4. S101 Protocol Reverse Engineering

### MAC адрес часов
04:EB:47:A8:C6:57

### Шаги
1. Включить Bluetooth HCI snoop log в настройках разработчика
2. Запустить JBand, выполнить действия с часами
3. Скопировать лог: `cp /sdcard/btsnoop_hci.log /sdcard/Download/Katya/Katya_Share/`
4. Запустить s101_protocol_reverse.py для извлечения пакетов
5. Проанализировать структуру команд

## 5. Voice Assistant

### Задача
Сделать Катю голосовым ассистентом Android.

### Шаги
1. Реализовать VoiceInteractionService
2. Добавить BIND_VOICE_INTERACTION в манифест
3. Установить APK в /system/priv-app
4. Настроить распознавание речи (локально или через API)
5. Реализовать фразу пробуждения «Окей, Катя»

## 6. FreeGPTHub

### Задача
Получить aes_gem_key для 9 бесплатных LLM-провайдеров.

### Шаги
1. Подписаться на WeChat «Charles的皮卡丘»
2. Отправить «FreeGPTHub»
3. Сохранить ключ в конфиг
4. Реализовать фолбэк между провайдерами

## 7. freellmpool

### Задача
Локальный LLM-роутер на 24 провайдера.

### Шаги
```bash
pip install freellmpool
freellmpool proxy --port 8080
```
Направить запросы Кати на http://localhost:8080/v1

## 8. Additional Tasks
- SQLite база build_plans.db
- Docker контейнер для Antigravity (см. antigravity_docker_plan.md)
- Баги: звук уведомлений, зависание музыки, рекурсивный разговор
- Интеграции: Obsidian, Telegram, Email, Умный дом

## 9. Audio Ducking

### Задача
Управлять музыкой при разговоре с Катей — ставить на паузу и возобновлять.

### Архитектура
- `AudioDuckingManager` в CommandExecutor
- Проверка `AudioManager.isMusicActive()` перед активацией
- Запрос `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` или отправка media pause
- После TTS: отпустить фокус или отправить play только если `wasPlaying == true`
- Плавное восстановление громкости через `VolumeShaper`

## 10. System Auditor / Debug Mode / Log Storage

### Задача
Мониторинг событий системы (AudioFocus, смерть процессов, Doze, уведомления) с кольцевым буфером на 10 минут и возможностью сохранения на флешку.

### Компоненты
- `SystemAuditor` — фоновый сервис с выбором модулей (AudioFocus, процессы, Doze и т.д.)
- Тумблер «Отладка» в UI
- Кольцевой буфер в RAM на 10 минут
- По команде «Катя, что случилось?» — анализ и ответ
- Выбор хранилища логов: внутренняя память, SD-карта, OTG-флешка
- Автосброс буфера на носитель при заполнении или по команде

## 11. TTS Filter

### Задача
Убрать из озвучки markdown-разметку, звёздочки, эмодзи и технические символы, чтобы TTS говорил естественно.

### Шаги
- Strip markdown: удалить `**`, `*`, `#`, `[]`, `()`
- Убрать `\\n\\n` и технические отступы
- Эмодзи → словесное описание или удалить
- Код-блоки → «пропущено»
- Добавить паузы на знаках препинания (опционально)

## 12. Vosk STT Integration

### Текущее состояние
Модель `vosk-model-small-ru-0.22` уже лежит в `/data/data/com.inspiredandroid.katya/files/vosk/`, но не используется.

### Задача
Переключить голосовой ввод с Android SpeechRecognizer на офлайн VoskRecognizer.

### Шаги
1. Реализовать VoskRecognizer в VoiceInput
2. Активировать только по кнопке записи
3. Добавить постобработку: исправление типичных ошибок модели

## 13. LLM Infrastructure (Zoo)

### Цель
Обеспечить Катю бесплатным и гибким доступом к различным LLM.

### Инструменты (все добавлены в план)
- **freellmpool** — роутер на 24 бесплатных провайдера
- **CatGPT-Gateway** — управление платными API-ключами
- **OpenGem** — пул Google-аккаунтов для Gemini
- **openai-oauth** — использование ChatGPT-подписки как API (прокси на порт 10531)
- **FreeGPTHub** — агрегатор 9 бесплатных LLM-провайдеров
- **OpenMinis** — готовая среда AI-агента (вдохновение и переиспользование кода)
- **no-cost-ai** — ещё один инструмент для бесплатного доступа (детали в репозитории)

## Ближайший билд
1. Добавить разрешения в манифест
2. Восстановить песочницу (Termux)
3. Запустить VLESS
4. Начать VoiceInteractionService
5. Реализовать Audio Ducking
6. Внедрить System Auditor с выбором хранилища
7. Подключить TTS Filter
8. Интегрировать Vosk STT
9. Настроить LLM-инфраструктуру (Freellmpool, openai-oauth и др.)
