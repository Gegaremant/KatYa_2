package com.katya.app.data

import com.katya.app.TaskAlarmScheduler
import com.katya.app.tools.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Both pending task lists produced by [TaskStore.getPendingTasksPartitioned]. */
data class PendingTaskPartition(
    val scheduled: List<ScheduledTask>,
    val heartbeatAdditions: List<ScheduledTask>,
)

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class TaskStore(
    private val appSettings: AppSettings,
    private val taskAlarmScheduler: TaskAlarmScheduler? = null,
) {

    private val json = SharedJson
    private val mutex = Mutex()

    /**
     * Сохранённый список не читается целиком. Ставится в [loadTasks] и держит
     * запись: пустой список, показанный пользователю при битом значении, не
     * должен тут же превратиться в новую запись поверх этого значения.
     */
    private var corrupt = false

    /**
     * Live view of every persisted task. Updated on every mutation (add/update/
     * remove), including updates coming from the scheduler completing a task, so
     * the Settings UI and the chat task widget never show stale lists.
     */
    private val _tasksFlow = MutableStateFlow(loadTasks())
    val tasksFlow: StateFlow<List<ScheduledTask>> = _tasksFlow.asStateFlow()

    private fun publish() {
        _tasksFlow.value = loadTasks()
    }

    private fun loadTasks(): MutableList<ScheduledTask> {
        val raw = appSettings.getScheduledTasksJson()
        // Пустое значение — это «ничего не хранится», а не «хранится мусор».
        // Различать это обязательно: у пользователей, у которых на диске уже
        // лежит `""` (его писал прежний импорт), попытка считать файл битым
        // заблокировала бы им добавление задач навсегда — до бэкапа, который
        // они как раз и не могут сделать. Пустое значение лечится само собой
        // первой же записью.
        if (raw.isBlank()) {
            corrupt = false
            return mutableListOf()
        }
        // Feedback 06.10 #4: a task created long ago vanished across an update, while a
        // new one created afterwards worked fine. The cause was here, not in the
        // scheduler: `decodeFromString<List<…>>` is all-or-nothing, so ONE row that the
        // current schema cannot read made the whole list come back empty — and the next
        // save (adding the new task) overwrote the stored JSON, destroying the old task
        // for good. The failure was also swallowed by a `println`, which never reaches
        // the log file, so there was nothing to see afterwards.
        //
        // So: decode row by row, keep what reads, report what does not, and refuse to
        // write back a list that came out shorter than the one on disk.
        val parsed = runCatching { json.parseToJsonElement(raw).jsonArray }
        val rows = parsed.getOrNull()
        if (rows == null) {
            // Нечитаемо не одна строка, а значение целиком. Пустой список здесь
            // был первой половиной потери: следующий addTask записывал поверх
            // файла список из одного элемента, и лежавшее там исчезало без следа.
            // Флаг ниже не даёт записи завершить начатое.
            AppLogger.e(
                "TaskStore",
                "список задач не читается целиком (${parsed.exceptionOrNull()?.message}) — " +
                    "запись поверх него заблокирована, чтобы не потерять то, что там лежит",
            )
            corrupt = true
            return mutableListOf()
        }
        corrupt = false

        val tasks = mutableListOf<ScheduledTask>()
        var broken = 0
        rows.forEachIndexed { index, element ->
            val task = runCatching { json.decodeFromJsonElement(ScheduledTask.serializer(), element) }
                .getOrNull()
            if (task == null) {
                broken++
                AppLogger.e("TaskStore", "задача №$index не читается текущей схемой — остальные показываю")
            } else {
                tasks += task
            }
        }

        // Migration: tasks persisted before the `trigger` field existed decode with
        // the default (TIME). Upgrade rows that carry a cron expression to CRON so the
        // scheduler can distinguish time/cron from heartbeat additions.
        var migrated = false
        val upgraded = tasks.map { task ->
            if (task.trigger == TaskTrigger.TIME && task.cron != null) {
                migrated = true
                task.copy(trigger = TaskTrigger.CRON)
            } else {
                task
            }
        }.toMutableList()

        // A row we failed to read is still on disk. Persisting now would erase it, and
        // the user would never know which task went missing — so keep the file as it is.
        if (broken == 0 && migrated) saveTasks(upgraded)
        return upgraded
    }

    private fun saveTasks(tasks: List<ScheduledTask>) {
        // Пока значение на диске целиком нечитаемо, запись поверх него — это
        // гарантированная потеря всего, что там было. Молча блокируем её и
        // говорим в журнал: список останется читаемым для пользователя через
        // восстановление из бэкапа, который ещё можно сделать до записи.
        if (corrupt) {
            AppLogger.e(
                "TaskStore",
                "запись отменена — сохранённый список не читается, новая запись стёрла бы его",
            )
            return
        }
        appSettings.setScheduledTasksJson(json.encodeToString(tasks))
    }

    suspend fun addTask(
        description: String,
        prompt: String,
        scheduledAtEpochMs: Long,
        cron: String? = null,
        trigger: TaskTrigger = if (cron != null) TaskTrigger.CRON else TaskTrigger.TIME,
    ): ScheduledTask = mutex.withLock {
        val tasks = loadTasks()
        val now = Clock.System.now()
        val effectiveScheduledAt = when (trigger) {
            TaskTrigger.HEARTBEAT -> 0L

            // heartbeat tasks are not time-gated
            TaskTrigger.CRON -> if (scheduledAtEpochMs == 0L) {
                try {
                    CronExpression(cron!!).nextAfter(now)?.toEpochMilliseconds() ?: now.toEpochMilliseconds()
                } catch (_: Exception) {
                    now.toEpochMilliseconds()
                }
            } else {
                scheduledAtEpochMs
            }

            TaskTrigger.TIME -> scheduledAtEpochMs
        }
        val task = ScheduledTask(
            id = Uuid.random().toString(),
            description = description,
            prompt = prompt,
            scheduledAtEpochMs = effectiveScheduledAt,
            createdAtEpochMs = now.toEpochMilliseconds(),
            cron = cron,
            trigger = trigger,
        )
        tasks.add(task)
        saveTasks(tasks)
        publish()
        taskAlarmScheduler?.scheduleNext()
        task
    }

    fun getAllTasks(): List<ScheduledTask> = loadTasks()

    /**
     * Re-arms the OS alarm for the nearest pending task.
     *
     * Needed when the task list is replaced wholesale rather than mutated: a
     * settings import, or a reboot clearing every pending alarm.
     */
    fun rearmAlarm() {
        taskAlarmScheduler?.scheduleNext()
    }

    /**
     * All PENDING non-heartbeat tasks — what the user thinks of as "scheduled". Heartbeat-
     * triggered tasks are surfaced separately via [getPendingHeartbeatAdditions].
     */
    fun getPendingTasks(): List<ScheduledTask> = loadTasks().filter { it.status == TaskStatus.PENDING && it.trigger != TaskTrigger.HEARTBEAT }

    /** Standing additions to every heartbeat self-check. */
    fun getPendingHeartbeatAdditions(): List<ScheduledTask> = loadTasks().filter { it.status == TaskStatus.PENDING && it.trigger == TaskTrigger.HEARTBEAT }

    /**
     * Both pending scheduled tasks and heartbeat additions from a single load. Hot-path
     * callers (chat system prompt, heartbeat prompt) need both lists per invocation;
     * combining avoids re-parsing the tasks JSON twice.
     */
    fun getPendingTasksPartitioned(): PendingTaskPartition {
        val (additions, scheduled) = loadTasks()
            .filter { it.status == TaskStatus.PENDING }
            .partition { it.trigger == TaskTrigger.HEARTBEAT }
        return PendingTaskPartition(scheduled = scheduled, heartbeatAdditions = additions)
    }

    suspend fun updateTask(task: ScheduledTask): ScheduledTask = mutex.withLock {
        val tasks = loadTasks()
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            tasks[index] = task
            saveTasks(tasks)
            publish()
        }
        taskAlarmScheduler?.scheduleNext()
        task
    }

    suspend fun removeTask(id: String): Boolean = mutex.withLock {
        val tasks = loadTasks()
        val removed = tasks.removeAll { it.id == id }
        if (removed) {
            saveTasks(tasks)
            publish()
        }
        taskAlarmScheduler?.scheduleNext()
        removed
    }

    fun getDueTasks(): List<ScheduledTask> {
        val now = Clock.System.now().toEpochMilliseconds()
        return loadTasks().filter {
            it.trigger != TaskTrigger.HEARTBEAT &&
                it.scheduledAtEpochMs <= now &&
                it.status == TaskStatus.PENDING
        }
    }
}
