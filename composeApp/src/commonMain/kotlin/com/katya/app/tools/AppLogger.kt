package com.katya.app.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

object AppLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs
    var isEnabled = true
    fun d(tag: String, message: String) {
        if (!isEnabled) return
        val logEntry = "[${Clock.System.now().toEpochMilliseconds()}] D/$tag: $message"
        addLog(logEntry)
    }

    fun i(tag: String, message: String) {
        if (!isEnabled) return
        val logEntry = "[${Clock.System.now().toEpochMilliseconds()}] I/$tag: $message"
        addLog(logEntry)
    }

    fun e(tag: String, message: String) {
        if (!isEnabled) return
        val logEntry = "[${Clock.System.now().toEpochMilliseconds()}] E/$tag: $message"
        addLog(logEntry)
    }

    fun w(tag: String, message: String) {
        if (!isEnabled) return
        val logEntry = "[${Clock.System.now().toEpochMilliseconds()}] W/$tag: $message"
        addLog(logEntry)
    }

    fun clear() {
        _logs.value = emptyList()
    }

    private fun addLog(entry: String) {
        val truncatedEntry = if (entry.length > 2000) entry.take(2000) + "... [TRUNCATED]" else entry
        _logs.update { current ->
            (current + truncatedEntry).takeLast(1000) // Keep last 1000 lines
        }
    }
}
