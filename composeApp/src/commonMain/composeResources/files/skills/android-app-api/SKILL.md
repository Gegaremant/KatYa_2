---
name: Android App API (Reverse Engineering)
description: Поиск, перехват и реверс-инжиниринг API других Android приложений.
---

# Взаимодействие с другими приложениями

Если API недокументировано или отсутствует, ты должен найти способ взаимодействовать с приложением:

## 1. Сниффинг трафика (PCAP / Mitmproxy)
Используй `mitmproxy` для перехвата запросов от других приложений.
Установка сертификата возможна благодаря root-правам (`/system/etc/security/cacerts/`).

## 2. Анализ APK
Используй `apktool` (или `jadx` в Termux) для распаковки APK и поиска скрытых Endpoint'ов.
`apktool d app.apk`

## 3. SQLite Базы данных
Если приложение хранит данные локально, с root-правами ты можешь читать его БД:
`su -c "sqlite3 /data/data/com.example.app/databases/app.db 'SELECT * FROM users;'"`

Ты не ограничен интерфейсами, читай данные напрямую!
