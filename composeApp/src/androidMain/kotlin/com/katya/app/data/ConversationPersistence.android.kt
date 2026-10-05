package com.katya.app.data

import android.content.Context
import android.util.Log
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.katya.app.db.KatyaDatabase
import com.katya.app.tools.AppLogger
import org.koin.java.KoinJavaComponent.inject
import java.io.File

/**
 * Opens the conversation database, healing it if it is unusable.
 *
 * Feedback 05.10 (point 1): after a full clear of app data the database came back
 * *without its tables* — the log was full of
 * `no such table: downloadableComponent (SQLITE_ERROR)`, which also meant the component
 * seed never ran, so `libproot.so` was never installed and the sandbox could never
 * start. A root cleaner that removes the `.db` but leaves `-wal`/`-shm` (or a restored
 * backup that lacks the schema) produces exactly that state: the file opens, but the
 * schema behind it is not there.
 *
 * So the table list is checked once at open. If it is short, the three files go and the
 * database is created from the current schema. Losing an already-broken conversation
 * history is a fair price for getting a working app; refusing to open is not.
 */
actual fun createConversationSqlDriver(): SqlDriver? {
    val context: Context by inject(Context::class.java)
    val name = "conversations.db"
    if (!databaseHasTables(context, name)) {
        AppLogger.action(
            "База данных",
            "после очистки данных осталась без таблиц — пересоздаю начисто",
        )
        deleteDatabaseFiles(context, name)
    }
    return try {
        AndroidSqliteDriver(KatyaDatabase.Schema, context, name)
    } catch (e: Exception) {
        // Last resort: a file SQLite refuses to open at all.
        AppLogger.e("База данных", "не открывается (${e.message}) — удаляю и создаю заново")
        deleteDatabaseFiles(context, name)
        runCatching { AndroidSqliteDriver(KatyaDatabase.Schema, context, name) }.getOrNull()
    }
}

private fun databaseHasTables(context: Context, name: String): Boolean {
    val file = File(context.getDatabasePath(name).absolutePath)
    // No file yet is not a problem — the driver will create it with the full schema.
    if (!file.exists() || file.length() == 0L) return true
    return try {
        android.database.sqlite.SQLiteDatabase
            .openDatabase(file.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            .use { db ->
                db.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table'", null).use {
                    it.moveToFirst() && it.getInt(0) > 0
                }
            }
    } catch (_: Exception) {
        // Unreadable or corrupt — treat as "needs recreating".
        false
    }
}

private fun deleteDatabaseFiles(context: Context, name: String) {
    try {
        context.deleteDatabase(name)
    } catch (_: Exception) {
        Log.e("KatyaDb", "deleteDatabase failed for $name")
    }
    // deleteDatabase() can leave the side files behind on some devices, and a stray
    // -wal is exactly what makes the next open come up schema-less.
    val base = File(context.getDatabasePath(name).absolutePath)
    for (suffix in listOf("", "-wal", "-shm", "-journal")) {
        runCatching { File(base.absolutePath + suffix).delete() }
    }
}
