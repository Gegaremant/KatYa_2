package com.katya.app.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Журнал приложения. Формат строк — «дата время  действие — результат»,
 * как просили: [21.09 09-27 запрос доступа к памяти — утверждён].
 *
 * Основной поток [logs] виден в UI (вкладка «Сервер» → «Посмотреть логи»).
 * Отдельный канал [rootLogs] (и файл root.log) фиксирует запросы к root-правам.
 */
object AppLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _rootLogs = MutableStateFlow<List<String>>(emptyList())
    val rootLogs: StateFlow<List<String>> = _rootLogs

    var isEnabled = true
    var logFilePath: String? = null
        private set

    var rootLogFilePath: String? = null
        private set

    fun setLogFilePath(path: String?) {
        logFilePath = path
    }

    /** Отдельный файл для root-событий; ставится из androidMain. */
    fun setRootLogFilePath(path: String?) {
        rootLogFilePath = path
    }

    /**
     * Действие с результатом: [21.09 09-27 запрос доступа к памяти — утверждён].
     * Основной способ логирования «что и что получилось».
     */
    fun action(description: String, result: String) {
        if (!isEnabled) return
        addLog("${fmtTime()}  $description — $result", _logs, logFilePath)
    }

    /** Запись в journal root-событий (отдельный поток + файл). */
    fun rootAction(description: String, result: String) {
        if (!isEnabled) return
        val line = "${fmtTime()}  ROOT: $description — $result"
        addLog(line, _rootLogs, rootLogFilePath)
    }

    fun d(tag: String, message: String) {
        if (!isEnabled) return
        addLog("[${fmtTime()}] D/$tag: $message", _logs, logFilePath)
    }

    fun i(tag: String, message: String) {
        if (!isEnabled) return
        addLog("[${fmtTime()}] I/$tag: $message", _logs, logFilePath)
    }

    fun e(tag: String, message: String) {
        if (!isEnabled) return
        addLog("[${fmtTime()}] E/$tag: $message", _logs, logFilePath)
    }

    fun w(tag: String, message: String) {
        if (!isEnabled) return
        addLog("[${fmtTime()}] W/$tag: $message", _logs, logFilePath)
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun clearRoot() {
        _rootLogs.value = emptyList()
    }

    private fun addLog(entry: String, sink: MutableStateFlow<List<String>>, filePath: String?) {
        val truncatedEntry = if (entry.length > 2000) entry.take(2000) + "... [TRUNCATED]" else entry
        sink.update { current ->
            (current + truncatedEntry).takeLast(1000)
        }
        filePath?.let { path ->
            try {
                val file = java.io.File(path)
                file.parentFile?.mkdirs()
                file.appendText(entry + "\n")
            } catch (e: Exception) {
                // Silent fail to avoid log recursion
            }
        }
    }

    /** «дд.мм чч-мм», например 21.09 09-27. */
    private fun fmtTime(): String = try {
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        "${local.dayOfMonth.pad2()}.${local.monthNumber.pad2()} ${local.hour.pad2()}-${local.minute.pad2()}"
    } catch (t: Throwable) {
        "${Clock.System.now().toEpochMilliseconds()}"
    }

    private fun Int.pad2(): String = toString().padStart(2, '0')
}