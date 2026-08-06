# Katya AI Assistant: Full Capabilities Guide

This document provides a comprehensive overview of all features and capabilities built into Katya, your personal AI assistant.

## 1. Core AI Interaction

- **Offline Wake Word Detection**: Katya constantly listens for the wake word *"Привет Катя"* (Hello Katya) using the Vosk speech recognition engine, which runs entirely locally and offline, ensuring privacy and saving battery life.
- **Auto-Language Text-to-Speech (TTS)**: When Katya responds, she automatically detects the language of the text. If it's English, she switches to an English TTS voice, preventing jarring mispronunciations. She also supports custom **RHVoice** integration for high-quality Russian speech synthesis.
- **Direct LLM Connectivity**: Katya connects directly to Large Language Models (like Ollama or LiteRT). You can connect her to a remote server over the internet, or directly through an **SSH Tunnel** bypassing any REST API exposure, adding an extra layer of security and avoiding cloud API limits.
- **Dynamic Local Model Suggestions**: Katya evaluates your device's hardware (e.g., RAM amount) on first launch to recommend the optimal local model (GGUF format) for running inference directly on your phone.

## 2. Persistent Memory & Context

- **Long-Term Memory System**: Unlike typical chatbots that forget previous sessions, Katya maintains a persistent memory database. She remembers facts about you, your preferences, and past instructions.
- **System Prompt Promotion**: If a memory is recalled frequently (hit count >= 5), Katya automatically promotes this fact into her Core System Prompt, ensuring she never forgets it during new interactions.

## 3. Autonomous Behaviors

- **Heartbeat & Self-Check**: Katya features an autonomous "Heartbeat" loop. Between 8 AM and 10 PM, she briefly wakes up every 30 minutes in the background to analyze her environment, check server statuses, and determine if she needs to proactively notify you about anything. If everything is fine, she goes back to sleep silently.
- **Auto-Heal (Server Management)**: If Katya detects that your remote Ollama server is down, she switches to a lightweight local model (`LiteRT`) to figure out the problem. She can autonomously connect to your server via SSH and execute commands (like `systemctl restart ollama`) to attempt to fix the issue.
- **Free API Fallback**: If your primary paid API (OpenAI/Anthropic) runs out of credits, Katya can automatically scrape GitHub for free, publicly available API keys and use them as a fallback.

## 4. System & App Integrations

Because Katya runs outside of traditional sandboxes (with Root privileges where available), she has deep access to Android system capabilities through her toolsets:

- **Media Controller**: Katya can control your music and media playback (Play, Pause, Skip, Volume up/down) without you touching the phone.
- **Calendar Access**: She can read your calendar events and remind you of upcoming meetings.
- **Notifications & SMS**: Katya can read incoming SMS messages and system push notifications to keep you informed.
- **Camera Access**: Need a picture? Katya can interface directly with your camera to capture images.
- **System Monitoring Overlay**: Connect Katya to a remote Linux server, and she provides a real-time floating UI overlay showing CPU, RAM, and GPU usage metrics.

## 5. Rich Interactive Interface

- **Dynamic UI Rendering**: Katya doesn't just output boring text. If she needs to show you a recipe, a server dashboard, or a brainstorming map, she renders custom Jetpack Compose widgets dynamically right inside the chat window.
- **Markdown & Code Support**: Full support for rendering complex Markdown, tables, and formatted code blocks with syntax highlighting.
- **Backup & Restore**: Easily export and import your entire state — including chat history, memories, tasks, and SSH monitor settings — locally or to your Nextcloud instance.

## 6. Operating Modes & Control

- **Interactive Onboarding**: On the very first launch, Katya greets you with a voice message and provides a beautiful, interactive checklist to grant all necessary system permissions.
- **Access Modes**: You can seamlessly switch Katya's access level: from a secure **Sandbox** (isolated) to full-scale **God Mode** (Root privileges, complete system control) or **Bare Android** (clean operation without external tools).
- **Manual Management**: While Katya is fully autonomous, you can always manually create Long-Term Memory entries or schedule new Background Tasks (with Time, Cron, or Heartbeat triggers) via a user-friendly interface.
- **Environment Awareness (Agent-Reach)**: Katya dynamically analyzes her network reachability and available skills (Hermes skills), adjusting her system prompt to match current conditions.
