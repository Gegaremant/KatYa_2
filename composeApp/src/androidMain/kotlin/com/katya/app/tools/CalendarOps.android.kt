package com.katya.app.tools

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import org.koin.java.KoinJavaComponent.getKoin

actual class CalendarOps actual constructor() {
    private val context: Context = getKoin().get<Context>()

    actual fun getEvents(daysAhead: Int): String {
        val contentResolver = context.contentResolver
        val now = System.currentTimeMillis()
        val endTime = now + daysAhead * 24 * 60 * 60 * 1000L

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        )

        val selection = "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?) AND (${CalendarContract.Events.DELETED} != 1)"
        val selectionArgs = arrayOf(now.toString(), endTime.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        val cursor = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        val events = mutableListOf<String>()

        cursor?.use {
            val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
            val descIdx = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
            val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)

            while (it.moveToNext()) {
                val title = it.getString(titleIdx) ?: "Без названия"
                val desc = if (descIdx >= 0) it.getString(descIdx) ?: "" else ""
                val start = it.getLong(startIdx)
                val end = it.getLong(endIdx)

                events.add("Событие: $title\nНачало: ${java.util.Date(start)}\nОкончание: ${java.util.Date(end)}\nОписание: $desc\n---")
            }
        }

        if (events.isEmpty()) {
            return "Событий на ближайшие $daysAhead дней не найдено."
        }
        return events.joinToString("\n")
    }

    actual fun createEvent(title: String, description: String, startTimeMs: Long, endTimeMs: Long): String {
        val contentResolver = context.contentResolver
        
        // Find default calendar ID
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        val cursor = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null
        )
        
        var calendarId: Long = 1 // fallback
        cursor?.use {
            val idIdx = it.getColumnIndex(CalendarContract.Calendars._ID)
            val primaryIdx = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            while (it.moveToNext()) {
                if (it.getInt(primaryIdx) == 1) {
                    calendarId = it.getLong(idIdx)
                    break
                } else if (calendarId == 1L) {
                    calendarId = it.getLong(idIdx)
                }
            }
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startTimeMs)
            put(CalendarContract.Events.DTEND, endTimeMs)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }

        return try {
            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                "Событие '$title' успешно создано в календаре."
            } else {
                "Error: Не удалось создать событие (uri is null)."
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
