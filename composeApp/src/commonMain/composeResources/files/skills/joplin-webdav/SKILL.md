---
name: Joplin WebDAV Sync
description: Управление заметками Joplin через WebDAV сервер.
---

# Синхронизация Joplin по WebDAV

Ты можешь взаимодействовать с заметками Joplin, размещенными на WebDAV сервере.
Joplin хранит заметки в виде Markdown-файлов.

## Доступ к WebDAV через Rclone

Для доступа к заметкам Joplin на WebDAV сервере рекомендуется использовать `rclone`.

1. Настрой подключение к WebDAV:
```bash
rclone config create joplin_webdav webdav url=https://your-webdav-url vendor=other user=your_user pass=your_pass
```

2. Смонтируй WebDAV директорию в локальную файловую систему (требуется root):
```bash
su -c "rclone mount joplin_webdav: /sdcard/joplin_notes"
```

3. После этого ты можешь читать, создавать и изменять заметки Joplin (.md) прямо в `/sdcard/joplin_notes/`.

## Использование CURL

Альтернативно, если rclone недоступен, ты можешь использовать `curl` для прямого взаимодействия с WebDAV:

- **Получить список файлов:**
```bash
curl -u user:pass -X PROPFIND https://your-webdav-url/
```

- **Скачать заметку:**
```bash
curl -u user:pass -O https://your-webdav-url/note.md
```

- **Загрузить/Обновить заметку:**
```bash
curl -u user:pass -T local_note.md https://your-webdav-url/note.md
```
