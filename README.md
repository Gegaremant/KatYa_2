# Katya AI Assistant

<div align="center">
<br>
<img src="KatYa-3.1.jpg" height="400">
<br>
<br>

# [📥 Скачать последнюю версию / Download latest version 📥](https://github.com/Gegaremant/KatYa_2/releases/latest)

[![RU](https://img.shields.io/badge/Язык-Русский-blue)](#-русский) | [![EN](https://img.shields.io/badge/Language-English-red)](#-english)

</div>

---

# 🇷🇺 Русский

## 📖 Описание проекта (Для чего это нужно?)

**KatYa** — это продвинутый и полностью автономный AI-ассистент для Android-устройств. Проект создан для тех, кто хочет иметь мощный искусственный интеллект прямо в своем кармане с максимальной приватностью. 
В отличие от обычных ботов, Катя обладает собственной памятью, может работать в фоне и умеет управлять операционной системой Android. Благодаря встроенной песочнице (PRoot) и возможности получения Root-прав, это не просто чат, а полноценный инструмент для автоматизации, выполнения команд и локального запуска нейросетей.

## 🌟 Возможности Кати

- **Абсолютная автономность**: Работает в фоновом режиме, самостоятельно выполняет запланированные задачи и проводит регулярный самоанализ (Heartbeat).
- **Персистентная память**: Автоматически запоминает важные факты о вас и контекст прошлых бесед, развиваясь вместе с пользователем.
- **Два режима доступа**:
  - **God Mode (Root)**: Полный контроль над Android. Катя может выполнять любые shell-команды напрямую от имени суперпользователя.
  - **Sandbox (PRoot)**: Встроенная изолированная Linux-среда. Позволяет запускать Node.js, Python, локальные серверы и утилиты прямо на телефоном без вреда для ОС.
- **Приватность и Локальная работа**:
  - Распознавание голоса (STT) работает локально через Vosk без интернета.
  - Поддержка запуска локальных LLM моделей через LiteRT прямо на устройстве (формат GGUF).
- **Обход блокировок**: Встроенная поддержка VLESS / Xray для подключения к облачным моделям (OpenAI, DeepSeek и др.) через защищенные прокси, работающая нативно и без ограничений.

## 📂 Структура проекта и Описание папок

Проект построен с использованием Kotlin Multiplatform и Jetpack Compose, что обеспечивает современную архитектуру.

```text
KatYa/
├── androidApp/          # Точка входа для Android приложения, платформенно-специфичный код (манифест, JNI, запуск).
├── composeApp/          # Основная логика приложения и UI на базе Compose Multiplatform.
│   └── src/
│       ├── androidMain/ # Android-специфичные реализации интерфейсов (Root-доступ, сервисы, WebView).
│       └── commonMain/  # Общая бизнес-логика, UI экраны (Settings, Chat), работа с БД, интеграции API и работа с локальными LLM.
├── gradle/              # Конфигурация сборки Gradle и версии библиотек (libs.versions.toml).
├── release_notes/       # История релизов и списки изменений (Changelogs).
└── ...                  # Файлы конфигурации CI/CD, Git и скрипты сборки.
```

## 🚀 Сборка локально

1. Склонируйте репозиторий:
   ```bash
   git clone https://github.com/Gegaremant/KatYa_2.git
   cd KatYa_2/KatYa
   ```
2. Откройте проект в **Android Studio** (или Fleet) и дождитесь загрузки Gradle-зависимостей.
3. Соберите проект через терминал:
   ```bash
   # Для сборки отладочной версии:
   ./gradlew assembleDebug

   # Для сборки релизной версии:
   ./gradlew assembleRelease
   ```
4. Установите получившийся APK на устройство. Для использования `God Mode` убедитесь, что на устройстве установлены Root-права (Magisk/KernelSU). Если прав нет, используйте `Sandbox`.

---

# 🇬🇧 English

## 📖 Project Description (Why is this needed?)

**KatYa** is an advanced and fully autonomous AI assistant for Android devices. This project is built for those who want a powerful artificial intelligence right in their pocket with maximum privacy.
Unlike standard chatbots, Katya has her own persistent memory, can operate in the background, and can control the Android operating system. Thanks to a built-in PRoot sandbox and optional Root access, this is not just a chat interface, but a full-fledged tool for automation, shell command execution, and running local neural networks.

## 🌟 Katya's Features

- **Absolute Autonomy**: Operates in the background, autonomously executes scheduled tasks, and performs regular self-reflection (Heartbeat).
- **Persistent Memory**: Automatically remembers important facts about you and past conversation contexts, growing alongside the user.
- **Two Access Modes**:
  - **God Mode (Root)**: Full control over Android. Katya can execute shell commands directly as a superuser.
  - **Sandbox (PRoot)**: Built-in isolated Linux environment. Allows you to run Node.js, Python, local servers, and utilities directly on the phone without harming the OS.
- **Privacy and Local Execution**:
  - Voice recognition (STT) works locally offline via Vosk.
  - Support for running local LLM models via LiteRT directly on the device (GGUF format).
- **Censorship Bypass**: Built-in VLESS / Xray support for connecting to cloud models (OpenAI, DeepSeek, etc.) via secure proxies, running natively and unrestrictedly.

## 📂 Project Structure and Folders

The project is built using Kotlin Multiplatform and Jetpack Compose, providing a modern architecture.

```text
KatYa/
├── androidApp/          # Android entry point, platform-specific code (Manifest, JNI, launch logic).
├── composeApp/          # Main application logic and UI based on Compose Multiplatform.
│   └── src/
│       ├── androidMain/ # Android-specific implementations (Root access, Background Services, WebView).
│       └── commonMain/  # Shared business logic, UI screens (Settings, Chat), DB operations, API integrations, and local LLM logic.
├── gradle/              # Gradle build configurations and library versions (libs.versions.toml).
├── release_notes/       # Release history and Changelogs.
└── ...                  # CI/CD configurations, Git files, and build scripts.
```

## 🚀 Local Build Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/Gegaremant/KatYa_2.git
   cd KatYa_2/KatYa
   ```
2. Open the project in **Android Studio** (or Fleet) and wait for the Gradle dependencies to sync.
3. Build the project via terminal:
   ```bash
   # Build the debug version:
   ./gradlew assembleDebug

   # Build the release version:
   ./gradlew assembleRelease
   ```
4. Install the resulting APK on your device. To use `God Mode`, make sure your device has Root access (Magisk/KernelSU). If you don't have Root, you can still use the `Sandbox` mode.