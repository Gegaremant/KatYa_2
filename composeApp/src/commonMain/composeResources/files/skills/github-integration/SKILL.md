---
name: GitHub Integration
description: Полная работа с Git и GitHub через Termux.
---

# Интеграция с GitHub

Для скачивания, отправки (push) и коммитов используй нативный `git` в Termux.
Если требуется авторизация, используй Personal Access Token (PAT).

## Основные шаги:
1. Клонирование: `git clone https://<token>@github.com/user/repo.git`
2. Коммиты: `git commit -am "message"`
3. Отправка: `git push`

Для автоматизации можно использовать GitHub CLI (`gh`). 
Установить можно через `pkg install gh`.
