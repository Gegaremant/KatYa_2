package com.katya.app.tts

import com.katya.app.Platform
import com.katya.app.currentPlatform
import com.katya.app.tools.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Синтез речи на десктопе — голосом самой операционной системы.
 *
 * Раньше на десктопе работал FreeTTS из `nl.marc-apps`: у него английские голоса
 * и нет управления тембром, то есть Катя на компьютере говорила по-английски и
 * медленнее, чем на телефоне. Теперь на Linux это speech-dispatcher, на Windows —
 * SAPI5 через PowerShell, на macOS — `say`. У всех троих есть скорость, у
 * первых двух — ещё и тембр.
 *
 * Голос ОС — единственный честный вариант для десктопа: ставить Piper с
 * LiteRT ради одной платформы значит тащить модель в приложение, а системный
 * синтезатор на месте всегда.
 */
class DesktopTtsSpeechEngine(
    private val backend: Backend,
) : SpeechEngine {

    /** Способ, которым именно эта система говорит. */
    interface Backend {
        /** Умеет ли движок менять тембр. */
        val supportsPitch: Boolean

        /** Умеет ли движок менять скорость. */
        val supportsRate: Boolean

        /** Человеческое имя движка — для журнала и подсказок в интерфейсе. */
        val displayName: String

        /** Готовит команду к запуску: сама команда и уборка за собой. */
        fun prepare(text: String, tuning: VoiceTuning): Prepared
    }

    /** Команда к запуску и то, что надо убрать после неё. */
    class Prepared(
        val command: List<String>,
        val cleanup: () -> Unit = {},
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override var onSpeechCompleted: (() -> Unit)? = null

    // Guards stale completions: only the newest utterance may fire the callback.
    private var generation = 0L

    @Volatile
    private var process: Process? = null

    private var job: Job? = null

    @Volatile
    private var tuning: VoiceTuning = VoiceTuning()

    override val voiceTuningSupport: VoiceTuningSupport = VoiceTuningSupport(
        rate = backend.supportsRate,
        pitch = backend.supportsPitch,
    )

    override fun applyTuning(tuning: VoiceTuning) {
        this.tuning = tuning
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        val myGeneration = ++generation
        job?.cancel()
        stopProcess()

        val prepared = backend.prepare(text, tuning)
        job = scope.launch {
            try {
                AppLogger.d("TTS", "Говорю через ${backend.displayName}")
                val started = ProcessBuilder(prepared.command)
                    .redirectErrorStream(true)
                    .start()
                process = started
                // Вывод читаем отдельной задачей: иначе процесс упирается в
                // переполненный буфер, а ожидание конца фразы — в ожидание
                // конца вывода, то есть тоже намертво.
                val reader = scope.launch(Dispatchers.IO) {
                    runCatching {
                        started.inputStream.bufferedReader().forEachLine { line ->
                            if (line.isNotBlank()) AppLogger.d("TTS", line)
                        }
                    }
                }
                // Ждём конца фразы, но не бесконечно.
                //
                // На части систем speech-dispatcher не отвечает, когда у сообщения
                // заданы скорость или тон: синтез проходит, а подтверждение о
                // конце приходит не всегда, и клиент ждёт вечно. Без предела
                // Катя оставалась бы «говорящей» после каждой фразы — кнопка
                // «стоп» не возвращалась. Предел считается от длины текста, а
                // не фиксированный: длинная фраза имеет право звучать долго.
                val timeout = speechTimeoutMillis(text, tuning.rateFactor)
                val finished = started.waitFor(timeout, TimeUnit.MILLISECONDS)
                if (!finished) {
                    AppLogger.w(
                        "TTS",
                        "${backend.displayName} не подтвердил конец фразы за ${timeout / 1000} с — прерываю ожидание",
                    )
                    started.destroyForcibly()
                }
                reader.cancel()
                if (myGeneration == generation) onSpeechCompleted?.invoke()
            } catch (e: Exception) {
                AppLogger.w("TTS", "Синтез через ${backend.displayName} не удался: ${e.message}")
                if (myGeneration == generation) onSpeechCompleted?.invoke()
            } finally {
                prepared.cleanup()
            }
        }
    }

    override fun stop() {
        generation++
        job?.cancel()
        stopProcess()
        onSpeechCompleted?.invoke()
    }

    private fun stopProcess() {
        val running = process ?: return
        process = null
        runCatching { running.destroy() }
        runCatching { running.waitFor(1, TimeUnit.SECONDS) }
        if (running.isAlive) runCatching { running.destroyForcibly() }
    }

    private companion object {
        /**
         * Сколько ждать конца фразы.
         *
         * Оценка: русская речь — примерно 12 знаков в секунду, при нулевом
         * темпе; скорость голоса делит её на коэффициент. Плюс запас на
         * запуск синтезатора и на растянутую фразу.
         */
        fun speechTimeoutMillis(text: String, rateFactor: Float): Long {
            val seconds = (text.length / 12f / rateFactor).coerceAtLeast(3f)
            return ((seconds + 5f) * 1000f).toLong()
        }
    }
}

/**
 * Синтезаторы операционных систем и их поиск.
 *
 * Отдельный объект, а не функции внутри движка: точку входа на десктопе волнует
 * только «есть синтезатор или нет», а всё остальное — детали конкретной системы.
 */
object DesktopTts {

    /** Движок синтеза для текущей ОС либо `null`, если говорить нечем. */
    fun createOrNull(): SpeechEngine? {
        val backend = detectBackend() ?: return null
        return DesktopTtsSpeechEngine(backend)
    }

    /**
     * Почему синтеза нет.
     *
     * `null` — значит, синтезатор есть и всё в порядке. Иначе здесь причина,
     * которую можно показать человеку: «нет пакета», «нет PowerShell» — вместо
     * молчания, в котором непонятно, сломалась Катя или компьютер.
     */
    fun unavailableReason(): String? = when (currentPlatform) {
        is Platform.Desktop.Linux ->
            "Не установлен speech-dispatcher — на этой системе им нечем говорить. " +
                "Поставьте пакет speech-dispatcher (и espeak-ng для русского голоса)."
        is Platform.Desktop.Windows ->
            "Не найден PowerShell — через него Катя говорит системным голосом Windows (SAPI5)."
        is Platform.Desktop.Mac -> null
        else -> "Синтез речи на этой платформе не поддерживается."
    }

    private fun detectBackend(): DesktopTtsSpeechEngine.Backend? = when (currentPlatform) {
        is Platform.Desktop.Linux -> {
            if (commandExists("spd-say")) {
                // Демон speech-dispatcher не всегда запущен: без него `spd-say`
                // отвечает «не могу подключиться». Поднимаем его один раз и
                // молча — если он уже работает, запуск просто ничего не меняет.
                startDispatcherIfNeeded()
                SpeechDispatcherBackend
            } else {
                null
            }
        }
        is Platform.Desktop.Windows ->
            if (powershellPath() != null) WindowsSapiBackend else null
        is Platform.Desktop.Mac -> MacSayBackend
        else -> null
    }

    /**
     * Linux: speech-dispatcher.
     *
     * У него своя шкала скорости и тона — ровно такая же, −100…+100, поэтому
     * проценты Кати уходят в неё без пересчёта.
     */
    private object SpeechDispatcherBackend : DesktopTtsSpeechEngine.Backend {
        override val supportsPitch = true
        override val supportsRate = true
        override val displayName = "speech-dispatcher"

        override fun prepare(text: String, tuning: VoiceTuning): DesktopTtsSpeechEngine.Prepared = DesktopTtsSpeechEngine.Prepared(
            command = listOf(
                "spd-say",
                "-r", tuning.ratePercent.toString(),
                "-p", tuning.pitchPercent.toString(),
                "-l", "ru",
                "-w",
                text,
            ),
        )
    }

    /**
     * Windows: SAPI5 через PowerShell.
     *
     * У SAPI5 своя шкала: скорость −10…+10, тон −50…+50, — поэтому проценты
     * Кати пересчитываются в неё. Текст и скрипт уходят во временные файлы:
     * в командной строке текст виден в списке процессов, а это личное.
     */
    private object WindowsSapiBackend : DesktopTtsSpeechEngine.Backend {
        override val supportsPitch = true
        override val supportsRate = true
        override val displayName = "SAPI5"

        override fun prepare(text: String, tuning: VoiceTuning): DesktopTtsSpeechEngine.Prepared {
            val shell = powershellPath() ?: return DesktopTtsSpeechEngine.Prepared(
                command = listOf("powershell.exe", "-NoProfile", "-Command", "Write-Output ''"),
            )
            val textFile = File.createTempFile("katya_tts_", ".txt")
            textFile.writeText(text, Charsets.UTF_8)
            val scriptFile = File.createTempFile("katya_tts_", ".ps1")
            scriptFile.writeText(
                buildString {
                    appendLine("Add-Type -AssemblyName System.Speech")
                    appendLine("\$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer")
                    appendLine("\$synth.Rate = ${sapiRate(tuning.ratePercent)}")
                    appendLine("\$synth.Pitch = ${sapiPitch(tuning.pitchPercent)}")
                    appendLine("\$synth.SetOutputToDefaultAudioDevice()")
                    appendLine(
                        "\$text = [System.IO.File]::ReadAllText('${textFile.absolutePath}', " +
                            "[System.Text.Encoding]::UTF8)",
                    )
                    appendLine("\$synth.Speak(\$text)")
                },
                Charsets.UTF_8,
            )
            return DesktopTtsSpeechEngine.Prepared(
                command = listOf(
                    shell,
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptFile.absolutePath,
                ),
                cleanup = {
                    runCatching { textFile.delete() }
                    runCatching { scriptFile.delete() }
                },
            )
        }

        /** Проценты Кати → шкала SAPI5 для скорости: −10…+10. */
        private fun sapiRate(percent: Int): Int = (percent / 10).coerceIn(-10, 10)

        /** Проценты Кати → шкала SAPI5 для тона: −50…+50. */
        private fun sapiPitch(percent: Int): Int = (percent / 2).coerceIn(-50, 50)
    }

    /**
     * macOS: `say`.
     *
     * Скорость у `say` измеряется словами в минуту, поэтому проценты пересчитываются
     * в множитель. Тона у `say` нет вовсе — честно говорим об этом через
     * `voiceTuningSupport`, и ползунок тембра на этой системе не притворяется
     * работающим.
     */
    private object MacSayBackend : DesktopTtsSpeechEngine.Backend {
        override val supportsPitch = false
        override val supportsRate = true
        override val displayName = "say (macOS)"

        override fun prepare(text: String, tuning: VoiceTuning): DesktopTtsSpeechEngine.Prepared {
            val wordsPerMinute = (175f * VoiceTuning.percentToFactor(tuning.ratePercent))
                .toInt()
                .coerceIn(90, 700)
            return DesktopTtsSpeechEngine.Prepared(
                command = listOf("say", "-r", wordsPerMinute.toString(), text),
            )
        }
    }

    /** Путь к PowerShell: системный, если переменные окружения его не подсказали. */
    private fun powershellPath(): String? {
        val fromEnv = System.getenv("SystemRoot")
            ?.let { File(it, "System32/WindowsPowerShell/v1.0/powershell.exe") }
            ?.takeIf { it.isFile }
        if (fromEnv != null) return fromEnv.absolutePath
        return listOf(
            "C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe",
            "C:/Program Files/PowerShell/7/pwsh.exe",
        ).firstOrNull { File(it).isFile }
    }

    /** Есть ли исполняемый файл в PATH. */
    private fun commandExists(name: String): Boolean {
        val path = System.getenv("PATH") ?: return false
        val extensions = if (isWindows()) listOf("", ".exe", ".cmd", ".bat") else listOf("")
        return path.split(File.pathSeparator).any { dir ->
            extensions.any { ext ->
                val candidate = File(dir, name + ext)
                candidate.isFile && candidate.canExecute()
            }
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name", "").lowercase().contains("win")

    /** Поднять демон speech-dispatcher, если он ещё не работает. */
    private fun startDispatcherIfNeeded() {
        if (dispatcherRunning()) return
        if (!commandExists("speech-dispatcher")) return
        runCatching {
            val process = ProcessBuilder("speech-dispatcher")
                .redirectErrorStream(true)
                .start()
            process.waitFor(3, TimeUnit.SECONDS)
            process.destroy()
        }
    }

    /**
     * Работает ли демон speech-dispatcher.
     *
     * Демон держит сокет в `/tmp/speech-dispatcher`; если там пусто, значит его
     * не запускали и `spd-say` упрётся в «не могу подключиться».
     */
    private fun dispatcherRunning(): Boolean = File("/tmp/speech-dispatcher").listFiles()?.isNotEmpty() == true
}
