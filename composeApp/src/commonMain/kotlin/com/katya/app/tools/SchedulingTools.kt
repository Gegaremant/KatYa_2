package com.katya.app.tools

import com.katya.app.data.TaskStatus
import com.katya.app.data.TaskStore
import com.katya.app.data.TaskTrigger
import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolInfo
import com.katya.app.network.tools.ToolSchema
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.tool_cancel_task_description
import katya.composeapp.generated.resources.tool_cancel_task_name
import katya.composeapp.generated.resources.tool_list_tasks_description
import katya.composeapp.generated.resources.tool_list_tasks_name
import katya.composeapp.generated.resources.tool_schedule_task_description
import katya.composeapp.generated.resources.tool_schedule_task_name
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
object SchedulingTools {

    /**
     * Reject execute_at instants more than this far in the past. A small slack covers
     * round-trip latency between "now" on the AI side and "now" in the tool executor;
     * larger gaps indicate a UTC/local sign flip that would otherwise either fire
     * immediately or (after backoff) silently sit PENDING.
     */
    private const val PAST_INSTANT_SLACK_MS = 60_000L

    fun scheduleTaskTool(taskStore: TaskStore) = object : Tool {
        override val schema = ToolSchema(
            name = "schedule_task",
            description = "Запланировать выполнение prompt'а позже, по расписанию или на каждом heartbeat. Это ЕДИНСТВЕННЫЙ способ выполнить что-то после текущего хода диалога: напоминания, «вернуться позже», периодические обновления, проверки, постоянные дополнения к heartbeat (поздороваться, всегда summarizing письма) — всё через этот инструмент. Каждый запуск начинается с чистого диалога, поэтому вложи в prompt всё необходимое. Ровно один триггер: execute_at (один раз в момент), cron (по расписанию) или on_heartbeat=true (добавляется к каждой самопроверке heartbeat). Время считай по **локальному времени** из раздела `## Context`, а не по UTC.",
            parameters = mapOf(
                "description" to ParameterSchema(type = "string", description = "Человекочитаемое описание задачи", required = true),
                "prompt" to ParameterSchema(type = "string", description = "Для execute_at/cron: полный prompt, который уйдёт модели при срабатывании. Для on_heartbeat: инструкция, добавляемая к каждой самопроверке heartbeat (например, 'Поздоровайся с пользователем по времени суток.').", required = true),
                "execute_at" to ParameterSchema(type = "string", description = "Дата-время ISO 8601 для одноразового запуска. Либо с часовым поясом (например, '2025-03-15T09:00:00+02:00') — трактуется как этот конкретный момент, — либо без него (например, '2025-03-15T09:00:00') — трактуется в местном часовом поясе пользователя из `## Context`. Предпочтительнее с часовым поясом. Должно быть в будущем.", required = false),
                "cron" to ParameterSchema(type = "string", description = "Cron-выражение для повторяющейся задачи (например, '0 9 * * 1' — каждый понедельник в 9:00)", required = false),
                "on_heartbeat" to ParameterSchema(type = "boolean", description = "true — выполнять этот prompt на каждой самопроверке heartbeat. Для постоянных дополнений к поведению heartbeat.", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val description = args["description"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing description")
            val prompt = args["prompt"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing prompt")
            val executeAt = args["execute_at"]?.toString()
            val cron = args["cron"]?.toString()
            val onHeartbeat = args["on_heartbeat"] as? Boolean ?: false

            val triggerCount = listOf(executeAt != null, cron != null, onHeartbeat).count { it }
            if (triggerCount == 0) {
                return mapOf("success" to false, "error" to "Exactly one of execute_at, cron, or on_heartbeat must be provided")
            }
            if (triggerCount > 1) {
                return mapOf("success" to false, "error" to "execute_at, cron, and on_heartbeat are mutually exclusive — pick one")
            }

            val trigger = when {
                onHeartbeat -> TaskTrigger.HEARTBEAT
                cron != null -> TaskTrigger.CRON
                else -> TaskTrigger.TIME
            }

            val scheduledAtEpochMs = if (executeAt != null) {
                val parsed = try {
                    parseIso8601ToEpochMs(executeAt)
                } catch (e: Exception) {
                    return mapOf("success" to false, "error" to "Invalid execute_at format: ${e.message}")
                }
                val nowMs = Clock.System.now().toEpochMilliseconds()
                if (parsed < nowMs - PAST_INSTANT_SLACK_MS) {
                    return mapOf(
                        "success" to false,
                        "error" to "execute_at ($executeAt) is in the past — check the Local time in Context and retry (use an offset-qualified value like 2025-03-15T09:00:00+02:00 to avoid UTC/local ambiguity)",
                    )
                }
                parsed
            } else {
                0L // cron and heartbeat tasks don't use this field at creation time
            }

            val task = taskStore.addTask(
                description = description,
                prompt = prompt,
                scheduledAtEpochMs = scheduledAtEpochMs,
                cron = cron,
                trigger = trigger,
            )

            return mapOf(
                "success" to true,
                "task_id" to task.id,
                "description" to task.description,
                "trigger" to trigger.name,
                "scheduled_at" to (executeAt ?: "n/a"),
                "cron" to (cron ?: "none"),
            )
        }
    }

    fun cancelTaskTool(taskStore: TaskStore) = object : Tool {
        override val schema = ToolSchema(
            name = "cancel_task",
            description = "Отменить запланированную задачу по её ID. Когда пользователь просит остановить, отменить или удалить задачу, вызови инструмент с ID из списка запланированных задач. Если не уверен, какая именно — сначала вызови list_tasks.",
            parameters = mapOf(
                "task_id" to ParameterSchema(type = "string", description = "ID отменяемой задачи", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val taskId = args["task_id"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing task_id")

            val removed = taskStore.removeTask(taskId)
            return if (removed) {
                mapOf("success" to true, "task_id" to taskId, "status" to "REMOVED")
            } else {
                mapOf("success" to false, "error" to "Task not found: $taskId")
            }
        }
    }

    fun listTasksTool(taskStore: TaskStore) = object : Tool {
        override val schema = ToolSchema(
            name = "list_tasks",
            description = "Показать все запланированные задачи с их ID, описаниями и статусом. Вызывай перед cancel_task, если нужно найти ID задачи. Можно отфильтровать по статусу.",
            parameters = mapOf(
                "status" to ParameterSchema(type = "string", description = "Фильтр по статусу: PENDING (в ожидании) или COMPLETED (выполнена)", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val statusFilter = args["status"]?.toString()?.uppercase()
            val allTasks = taskStore.getAllTasks()

            val filtered = if (statusFilter != null) {
                val status = try {
                    TaskStatus.valueOf(statusFilter)
                } catch (e: Exception) {
                    return mapOf("success" to false, "error" to "Invalid status: $statusFilter. Use PENDING or COMPLETED")
                }
                allTasks.filter { it.status == status }
            } else {
                allTasks
            }

            return mapOf(
                "success" to true,
                "count" to filtered.size,
                "tasks" to filtered.map { task ->
                    mapOf(
                        "id" to task.id,
                        "description" to task.description,
                        "prompt" to task.prompt,
                        "trigger" to task.trigger.name,
                        "scheduled_at_epoch_ms" to task.scheduledAtEpochMs,
                        "created_at_epoch_ms" to task.createdAtEpochMs,
                        "cron" to (task.cron ?: "none"),
                        "status" to task.status.name,
                        "last_result" to (task.lastResult ?: "none"),
                    )
                },
            )
        }
    }

    val scheduleTaskToolInfo = ToolInfo(
        id = "schedule_task",
        name = "Schedule Task",
        description = "Schedule a task for future execution",
        nameRes = Res.string.tool_schedule_task_name,
        descriptionRes = Res.string.tool_schedule_task_description,
    )

    val cancelTaskToolInfo = ToolInfo(
        id = "cancel_task",
        name = "Cancel Task",
        description = "Cancel a scheduled task",
        nameRes = Res.string.tool_cancel_task_name,
        descriptionRes = Res.string.tool_cancel_task_description,
    )

    val listTasksToolInfo = ToolInfo(
        id = "list_tasks",
        name = "List Tasks",
        description = "List all scheduled tasks",
        nameRes = Res.string.tool_list_tasks_name,
        descriptionRes = Res.string.tool_list_tasks_description,
    )

    val schedulingToolDefinitions = listOf(scheduleTaskToolInfo, cancelTaskToolInfo, listTasksToolInfo)

    fun getSchedulingTools(taskStore: TaskStore): List<Tool> = listOf(
        scheduleTaskTool(taskStore),
        cancelTaskTool(taskStore),
        listTasksTool(taskStore),
    )

    private fun parseIso8601ToEpochMs(isoString: String): Long {
        // Try parsing as Instant first (with timezone offset)
        return try {
            Instant.parse(isoString).toEpochMilliseconds()
        } catch (e: Exception) {
            // Fall back to LocalDateTime (no timezone) and use system default
            val localDateTime = LocalDateTime.parse(isoString)
            localDateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        }
    }
}
