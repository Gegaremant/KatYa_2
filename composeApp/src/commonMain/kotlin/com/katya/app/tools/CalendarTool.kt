package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema

expect class CalendarOps() {
    fun getEvents(daysAhead: Int): String
    fun createEvent(title: String, description: String, startTimeMs: Long, endTimeMs: Long): String
}

class CalendarTool : Tool {
    private val calendarOps = CalendarOps()

    override val schema = ToolSchema(
        name = "manage_calendar",
        description = "Чтение и создание событий в системном календаре Android (синхронизируется с Google Calendar и др.).",
        parameters = mapOf(
            "action" to ParameterSchema("string", "Действие: 'read' (прочитать расписание) или 'create' (создать событие)", true),
            "days_ahead" to ParameterSchema("integer", "Сколько дней вперед читать (для read). По умолчанию 7.", false),
            "title" to ParameterSchema("string", "Заголовок события (для create).", false),
            "description" to ParameterSchema("string", "Описание события (для create).", false),
            "start_time" to ParameterSchema("integer", "Время начала в Unix Timestamp (миллисекунды) (для create).", false),
            "end_time" to ParameterSchema("integer", "Время окончания в Unix Timestamp (миллисекунды) (для create).", false)
        )
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val action = args["action"]?.toString()
            ?: return mapOf("success" to false, "error" to "Не указано действие (action).")

        return try {
            when (action) {
                "read" -> {
                    val days = (args["days_ahead"] as? Number)?.toInt() ?: 7
                    val result = calendarOps.getEvents(days)
                    mapOf("success" to true, "events" to result)
                }
                "create" -> {
                    val title = args["title"]?.toString() ?: "Новое событие"
                    val desc = args["description"]?.toString() ?: ""
                    
                    // Если время не передано, создаем на завтра в 12:00
                    val defaultStart = System.currentTimeMillis() + 24 * 60 * 60 * 1000
                    val startTime = (args["start_time"] as? Number)?.toLong() ?: defaultStart
                    val endTime = (args["end_time"] as? Number)?.toLong() ?: (startTime + 60 * 60 * 1000)

                    val result = calendarOps.createEvent(title, desc, startTime, endTime)
                    mapOf("success" to !result.startsWith("Error"), "result" to result)
                }
                else -> mapOf("success" to false, "error" to "Неизвестное действие: $action")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Ошибка календаря: ${e.message}")
        }
    }
}
