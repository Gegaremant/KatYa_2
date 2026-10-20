package com.katya.app.tts

/**
 * На Android системный синтезатор есть всегда: если он не поднялся, причина
 * лежит в журнале, и отдельного объяснения в интерфейсе не нужно.
 */
actual fun systemTtsUnavailableReason(): String? = null
