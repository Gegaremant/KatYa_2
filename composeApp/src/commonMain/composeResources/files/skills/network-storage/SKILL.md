---
name: Network Storage & Tunnels
description: Работа с сетевыми протоколами SSHFS, Rclone, SMB, WebDAV.
---

# Работа с сетевыми хранилищами

Ты можешь монтировать сетевые диски прямо в файловую систему Android через Termux.

## SSHFS (Монтирование по SSH)
Если нужно подключить удаленный сервер:
`sshfs user@host:/path /local/path`
(Требует root и поддержку FUSE ядра, используй модуль magisk sshfs, если не работает из коробки).

## Rclone
Для облачных дисков (Google Drive, Dropbox, WebDAV) используй `rclone`.
Пример настройки: `rclone config`
Монтирование: `su -c "rclone mount remote: /sdcard/cloud_drive"`

Если что-то не работает, ищи альтернативные пути обхода через API, CURL запросы или проксирование портов.
