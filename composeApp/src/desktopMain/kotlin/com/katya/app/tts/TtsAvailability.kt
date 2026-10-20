package com.katya.app.tts

/**
 * Причина отсутствия синтезатора на десктопе.
 *
 * На Linux без пакета `speech-dispatcher` говорить нечем, на Windows нужен
 * PowerShell (через него идёт SAPI5), на macOS `say` есть всегда.
 */
actual fun systemTtsUnavailableReason(): String? = DesktopTts.unavailableReason()
