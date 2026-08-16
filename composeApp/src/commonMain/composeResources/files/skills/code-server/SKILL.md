---
name: Code-Server
description: Поднятие локальной среды разработки (VS Code) через code-server с root доступом.
---

# Code-Server

Ты можешь развернуть полноценную среду разработки прямо на устройстве.

## Установка и запуск
1. Установка: `pkg install code-server`
2. Запуск: `code-server --bind-addr 0.0.0.0:8080 --auth none`

## Root доступ
Для запуска `code-server` от имени root, чтобы получить доступ ко всей файловой системе:
`su -c "code-server --bind-addr 0.0.0.0:8080 --user-data-dir /data/local/tmp/code-server --auth none"`

Это позволит тебе и пользователю редактировать файлы `/data/data/...` прямо в веб-интерфейсе!
