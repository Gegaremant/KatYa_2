package com.katya.app.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.katya.app.getAppFilesDirectory
import com.katya.app.tools.AppLogger
import java.io.File

private const val DATABASE_NAME = "conversations.db"

/**
 * Сколько таблиц должно быть.
 *
 * Порог занижен относительно текущей схемы намеренно: проверка отсекает файл, где
 * таблиц нет вообще (обломок после очистки данных или битая копия), а не ловит
 * каждое добавление в схеме как аварию.
 */
private const val EXPECTED_TABLE_COUNT = 3

/**
 * База диалогов на десктопе — обычный файл SQLite рядом с настройками.
 *
 * Тот же движок `sqldelight`, что и на Android, только драйвер JDBC вместо
 * Android-овского. Формат базы общий, поэтому резервная копия, снятая на
 * телефоне, открывается здесь без конвертации.
 */
actual fun createConversationSqlDriver(): SqlDriver? {
    val dir = File(getAppFilesDirectory())
    dir.mkdirs()
    val url = "jdbc:sqlite:${File(dir, DATABASE_NAME).absolutePath}"
    if (!databaseHasTables(url)) {
        AppLogger.action("База данных", "осталась без таблиц — пересоздаю начисто")
        deleteDatabaseFiles(dir)
    }
    return runCatching { JdbcSqliteDriver(url) }
        .onFailure { e ->
            AppLogger.e("База данных", "не открывается (${e.message}) — удаляю и создаю заново")
            deleteDatabaseFiles(dir)
        }
        .recoverCatching { JdbcSqliteDriver(url) }
        .getOrNull()
}

/**
 * Есть ли в базе таблицы.
 *
 * Файл может открыться и при этом не содержать схемы — так ведёт себя база,
 * оставшаяся от удалённого приложения или восстановленная из неполной копии.
 * Запросы потом падают на `no such table`, поэтому схему проверяем до работы.
 *
 * Через публичный `SqlDriver.execute`, а не через JDBC-типы: у `SqlDriver` нет
 * `connection`, а лезть в `org.sqlite` здесь незачем.
 */
private fun databaseHasTables(url: String): Boolean = runCatching {
    val driver = JdbcSqliteDriver(url)
    try {
        val count: Long = driver.execute(
            identifier = null,
            sql = "SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
            parameters = 0,
            binders = null,
        ).value
        count >= EXPECTED_TABLE_COUNT
    } finally {
        driver.close()
    }
}.getOrDefault(false)

private fun deleteDatabaseFiles(dir: File) {
    listOf(DATABASE_NAME, "$DATABASE_NAME-wal", "$DATABASE_NAME-shm", "$DATABASE_NAME-journal")
        .forEach { File(dir, it).delete() }
}
