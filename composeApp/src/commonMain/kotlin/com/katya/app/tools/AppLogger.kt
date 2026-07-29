package com.katya.app.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

object AppLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    var isEnabled = false

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
        _logs.update { current ->
            (current + entry).takeLast(1000) // Keep last 1000 lines
        }
    }
}
